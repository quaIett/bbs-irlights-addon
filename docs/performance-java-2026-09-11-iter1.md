# Локальные Java-оптимизации — итерация 1, 2026-09-11

Основание: `IRLights-NEXT-SESSION-PROMPT.md` и фактические исходники на этом ПК.
MC 1.20.4, core `main`, addon `master`, версия 1.1.6. Существующие незакоммиченные
изменения сохранены. Новые изменения GLSL, качества, лимитов и частоты обновлений
не входят в эту итерацию. Итоговый игровой A/B предстоит.

## Кандидаты и статус

| № | Приоритет | Решение | Проверка / статус |
| --- | --- | --- | --- |
| 1 | P1 | Immutable Member разделяется между обычными spots только внутри одного bake, лениво на retained slot | Реализовано; overlay differential и allocation PASS |
| 2 | P1 | Scratch для снимков Model/Mob; свежая поза, восстановление в finally и вложенные вызовы | Реализовано; standalone PASS, in-client проверка предстоит |
| 3 | P2 | Heap индексов существующих 128 SoA slots при вытеснении | Реализовано; differential production put PASS |
| 4 | P2 | Общая матрица положения/направления spot; сохранён resolver API | Реализовано; differential harness PASS |
| 5 | P2 | TreeMap → сортируемые scratch-массивы | Отложено: geometry уже кешируется на кадр; польза на типичных размерах карт не измерена, сложность recursive shallow snapshots сейчас не оправдана |
| 6 | P2 | Primitive LRU stamps по слою вместо второй карты и Long boxing | Реализовано; differential trace и allocation PASS |
| 7 | P3 | glBindBufferBase перед upload заменяет отдельный generic bind | Реализовано; настоящий GL43 harness PASS, 201 проверенная загрузка |

## Базовое состояние

Обе базовые сборки прошли до правок. Снимок исходников и SHA-инвентарь 453 файлов
сохранены в `BBS/deliverables/optimization-java-2026-09-11-iter1/baseline/`.
Там же лежат существовавшие локальные JAR и Git HEAD/status четырёх репозиториев.

Локальный исходный addon JAR имеет SHA-256
`4F58EC606EBF9CFFF4C31EE7D5ECC559161CADE025A5BFF0582B81ED4ADB8003`,
а не `4B4101...` из перенесённого отчёта. Его вложенный core совпадает с
сохранённым исходным локальным core (`EDA2B137...03488`). После базовой
пересборки core без публикации проверка против нового build/libs закономерно
сообщила `Nested core is stale`; это не нарушение состава исходного JAR.
Итог проверяется после новой публикации core и сборки addon. В baseline сохранено
сравнение содержимого первоначального и пересобранного core (пять отличающихся
class entries); для A/B важны конкретные JAR/SHA, а не один номер версии.
Runtime CR здесь называется `ComplementaryReimagined_IRLights`; его GLSL и
исходный Modification имеют контрольный SHA `64FE36FE...C64CD`.
Общий файл версии находится в `BBS/VERSION`, содержал 1.1.5 и приведён к
требуемой 1.1.6; координаты core/addon уже содержали 1.1.6.

## Проверки

JDK: `C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot`.
Gradle cache: `C:/Users/qualet/.gradle`, Maven local: `C:/Users/qualet/.m2/repository`.
Bytecode target остаётся Java 17. Сборки выполняются offline из заполненного кеша.

Существующие проверки: block-cache 31, shadow-budget 23, profiler 23 + CSV +
4 strict-CSV, LightCollector 370 491, pipeline 3 998 684 / 245 reference frames /
21 shader variants — PASS. Логи сохраняются в отдельной папке итерации.

Для upload harness компилируются настоящие LightBuffer, VlGlobalsBuffer и
ClusterGridBuffer с LWJGL 3.3.3/JOML 1.10.5; Minecraft/GL заглушек нет.
Скрытый GLFW OpenGL43: NVIDIA GeForce RTX 3060 Laptop GPU. Проверены чужие
generic/indexed bindings, byte-exact доставка данных, active/empty, полный и
малый буфер, delete/reinit, повторный empty, dither frameIndex и wrap 4095→0.
Один и тот же harness запущен для исходных файлов из baseline и новых файлов:
в обоих случаях 2 305 073 утверждения, 201 проверенная загрузка PASS. Один лишний bind
убран в каждом upload LightBuffer/VlGlobalsBuffer/ClusterGridBuffer, включая
empty cluster rebind; при обычном полном кадре это минус три GL/JNI вызова.
Это не межкадровый кеш GL-состояния: indexed rebind выполняется каждый раз.
Основание семантики bind: [OpenGL 4.6 Core, §6.1](https://registry.khronos.org/OpenGL/specs/gl/glspec46.core.pdf#page=84).

## Измеренная лишняя работа

| Участок / синтетическая нагрузка | До → после | Что означает |
| --- | --- | --- |
| Member: 26 spots × 20 общих known casters | 520 → 20 записей; 24 960 → 960 B/bake | −96,15% выделений именно этих immutable записей |
| BBS Model: 24 группы, snapshot + pose evaluation | 4 288 → 0 B/sample | Warmed ThreadMXBean; не весь метод sample |
| BBS Mob: 24 части, snapshot + traversal | 1 648 → 88 B/sample | Stream traversal остаётся; не весь метод sample |
| Spot render register | 192 → 112 B/call, два → одно произведение матриц | Реальный JOML/LightMath, границы Minecraft/BBS заменены fixtures |
| Cookie warm hit после 128 обращений | 24 → 0 B/hit | Исключены boxed Long и второй hash lookup |
| Выбор farthest, 4096 кандидатов от дальних к ближним | 503 936 → 47 848 сравнений | Те же 128 retained slots, −90,5% сравнений |
| Выбор farthest, 4096 случайных кандидатов | 53 340 → 4 911 сравнений | −90,8% сравнений |

Overlay: 44 прежних проверки + 27 016 новых утверждений. Differential 300 кадров /
30 lights: обе версии дали sp.reuse=4056, pt.reuse=624, draw decisions=4320.
Старые snapshots после reset, изменённые revision/type/bounds, UNKNOWN,
duplicates, исчезновение caster, disabled collection и point masks покрыты.

Nearest-pool: 92 208 838 побитовых утверждений для фактического production put
против сохранённого scan, 158 обычных + 4 malformed кадра; итог harness
92 208 842 проверки. Сверяются все SoA fields/slots/count/farthest/telemetry после
каждого emit. NaN/Infinity по-прежнему отсеиваются существующим guard до pool.
Heap не строится при <=128 casters и при всех отвергнутых новых кандидатах.
Обе версии выделяют 0 B/collect. Последний локальный микрозамер: descending
209 966→190 091 ns, random 41 102→31 825 ns; small 1209→1231 ns и all-rejected
19 144→20 250 ns показали небольшое ухудшение. Результаты колебались между
прогонами, поэтому универсальное ускорение не заявляется; кандидат принят
для плотной сцены с заменами, где доказано сокращение сравнений.

Render/cookie: 627 078 проверок; 12 000 матриц, roll, отрицательные/неравномерные/
нулевые scale, большие координаты, вложенный callback загрузки cookie и ошибки.
Finite, ±0 и Infinity сравниваются по raw bits; NaN сравниваются по классификации,
поскольку payload арифметического NaN менялся после JIT даже без правок исходника.
Cookie trace покрывает изменяемые/equal-key Link, >128 warm hits, flipbook >32,
read/decode/upload failures, reload/delete/recreate, overflow, ties и HashMap
collision/tree-bin. CookieArrayBase и его GL guards не менялись; этот CPU harness
заменяет GL/asset boundary и не проверяет настоящую загрузку cookie в GPU.
Последние median 9 alternating pairs: cookie 16,890→8,646 ns/hit; spot
116,752→97,016 ns/call. Аллокации устойчивы между прогонами, время заметно
колеблется с нагрузкой. Cookie microbenchmark исключает создание строки BBS Link.

Независимое ревью core heap/Member/upload и render/cookie: actionable замечаний
нет; frozen reference-файлы сверены со снимком. `git diff --check` PASS с учётом
Windows CRLF. Синтетические overlay draw decisions не являются игровыми counters.

BBS scratch: 145 474 утверждения PASS, настоящие BBS 2.3.1 Model.resetPose/
applyPose/Transform/Color, vanilla ModelPart и JOML. Проверены прежняя pose
signature, текущие значения, exact refs/raw float bits (включая alpha/rotate2/
scale/lighting/orient, NaN и -0), большая→малая→большая модель, перестановка и
замена групп при том же размере, nested вызовы и очистка чужих ссылок.
Setup, applyPose, signature failures проверены; getPose/dispatcher failures
смоделированы на callback boundary. Cleanup Mob исполняет pose→overlay→parts→cache
даже при ошибках; primary exception сохраняется, последующие suppressed, model
flags и hurtTime восстанавливаются внешним finally. Покрыты все 8 комбинаций
cleanup failures с/без renderer failure, повторное использование scope после
ошибок. Независимое ревью Model/Mob scratch и cleanup замечаний не нашло.

Это не запуск полных sampler/dispatcher/mixin в Minecraft. Опциональная
`-Dirlite.checkCasterRevisions=true` проверка Villager расширена до 11 сценариев
с восстановлением всех частей; она компилируется в JAR, но здесь не запускалась.

## Игровая проверка

Итог: core `build publishToMavenLocal` и addon `build -Pmc=1.20.4` PASS.
Обновлены только два точно найденных remap-cache файла зависимости core 1.1.6.
`verify-core-bundle.ps1`: ровно один nested core 1.1.6, SHA совпадает со свежим
core, bytecode major=61. Новый addon SHA-256:
`6B154C1BF5A3F457E1A429D162B50A999CA6E7672408764D3A49C602A685B703`.
Core SHA-256: `56E4582D42110379B09D70317D1A42FF4C5489D1FB9FCA8D33DDD0E230ABD100`.
JAR/отчёт/логи/перенос: `BBS/deliverables/optimization-java-2026-09-11-iter1/`.
17 сохранённых runtime/mod/shader файлов совпали по SHA с началом работы.
Коммитов/push нет. Предыдущие комплекты и runtime не заменялись.

После завершения сборки сравнивать с сохранённым baseline при тех же мире,
камере, разрешении, effects и числе источников. Проверить текущие BBS-позы и
анимацию, live actor/replay/свет на BodyPart bone, point/spot/degenerate cones,
cookie flipbook/reload и плотную сцену с более чем 128 caster-кандидатами.
Для счётчиков использовать sp.reuse/pt.reuse/draw и соответствующие CPU intervals.
VL-проверка предыдущей итерации остаётся отдельной; shaderpack не заменялся.

```powershell
./tools/analyze-profile.ps1 -Capture <capture.csv> -WarmupFrames 240 `
  -MeasureFrames 600 -RequireGpuMetric deferred2 -RequireCompleteWindow
```
