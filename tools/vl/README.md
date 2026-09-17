# Проверка production VL на GPU

`verify-vl-gpu.ps1` — самостоятельная проверка библиотеки CR на настоящем
OpenGL-драйвере. Minecraft, Iris и мир не запускаются; конфигурация игры не
изменяется. GLFW создаёт невидимое окно (`GLFW_VISIBLE=false`) с OpenGL 4.3.
Нужны Java 21 и уже загруженные `lwjgl`, `lwjgl-glfw`, `lwjgl-opengl` 3.3.2,
включая точные `natives-windows.jar` для x64. Скрипт берёт их из
`IRLights/.gradle-user`, либо `GRADLE_USER_HOME`/`-GradleUserHome`.

Из каталога аддона:

```powershell
& ./tools/verify-vl-gpu.ps1 -OutputDirectory build/vl-gpu-final
& ./tools/verify-vl-gpu.ps1 -Width 512 -Height 288 -OutputDirectory build/vl-gpu-final-512
# Диагностика отдельных принятых изменений; дополнительное время, не настройки игры:
& ./tools/verify-vl-gpu.ps1 -IsolateChanges -OutputDirectory build/vl-gpu-isolated
```

При AccessDenied Java на cached JAR запускать тот же скрипт вне ограниченной
песочницы. Скрипт не скачивает зависимости, не правит production GLSL и не
собирает мод. `-JavaHome`, `-Baseline`, `-Current`, размеры и длины окон можно
передать явно. Целевой bytecode harness — Java 17, запуск проверен на Java 21.

## Что проверяется

Базовая библиотека сохранена в `reference/irlite_lights.glsl`; текущая читается
непосредственно из
`Shadres/Modification/ComplementaryReimagined/shaders/lib/irlite/irlite_lights.glsl`.
В fragment shader включается **вся** библиотека, включая её compile-time gates,
а настоящая функция `Noise3D` извлекается из `lib/util/commonFunctions.glsl`
без изменения её тела. Минимальный GLSL 4.3 wrapper подаёт одинаковые лучи,
фиксированное время и dither. Алиасы `texture2D/texture2DLod` соответствуют их
современным именам. Surface-функции библиотечного файла остаются в исходнике,
но не вызываются этим VL-pass.

Драйвер действительно компилирует и линкует каждый вариант. Проверяются
активные интерфейсы `IrliteLights`/`IrliteVlGlobals`. `glGetShaderSource`
сравнивается с полностью собранным fragment shader. В отчёте сохранены SHA-256
библиотек, Noise3D, переданного и прочитанного у драйвера GLSL; рядом лежат
сами исходники и compile/link logs. Это проверка доставки standalone-шейдера
драйверу, а не проверка установки shaderpack в Iris.

Fixtures используют настоящие std430 light/cluster SSBO и std140 globals UBO,
детерминированный шум 128×128, cookie array, raw shadow atlases и min/max mips.
В библиотеку передаются 16 источников. Проверяются point/spot, stride 1/2/3/8,
noise OFF/ON/morph, cookie, освещённые/закрытые/пограничные shadow samples,
нулевые noiseAmount/tipBoost, включённый/выключенный Hi-Z, mixed point+spot с разными tiers, диапазонами,
плотностью/анизотропией, частью отсутствующих shadow maps и cookie inversion.
Дополнительные случаи — оси около `abs(axis.y)=0.99`, узкие конусы,
cookie при runtime shadows OFF и при tile=-1. Отдельные shader builds снимают
только `IRLITE_VL_SHADOWS`, как это делает boolean shader option Iris; они
сравниваются в случаях shadows OFF и проверяют путь без compiled shadows.

Float RGBA32F readback сравнивает baseline и current, а также current с тремя
принятыми оптимизациями, выключенными через внутренние defines. Порог на канал:
`abs(delta) <= 2e-5 + 5e-4 * max(abs(a), abs(b))`. Отдельно считаются max absolute,
max relative и RMS; NaN/Infinity — ошибка. Проверки энергии не дают пройти
случайно пустому lit-pass и требуют фактического влияния shadows/noise/morph/cookie.
При `-IsolateChanges` каждый внутренний switch дополнительно выключается
по отдельности. Превью baseline/current и усиленной ×1000 разницы сохраняются
для одной фиксированной сцены; тонирование PNG служит только просмотру,
численная проверка использует исходные линейные float-значения.

## GPU timing

По умолчанию 40 warmup A/B pairs, затем 80 измеряемых pairs. Каждый query содержит
четыре draw; отчёт делит время batch на четыре. Порядок чередуется AB/BA.
Fixture upload, переключение программы, readback и `glFinish` находятся вне
query. Асинхронно выданные результаты читаются после завершения GPU; никаких
GL-скобок в этот момент не открыто. Сохраняются все сырые batch durations.

Нулевой query на непустом draw не считается бесплатным рендером: отклоняется
вся пара A/B, её сырые значения остаются в CSV. Отчёт показывает accepted/rejected
pairs; при потере более 10% тест завершается ошибкой. В финальном измерении
предпочтительны полностью принятые окна. Результат — отдельные медианы на
синтетическую сцену и отношение A/B; это **не измерение FPS в игре**.

Сохранённые измерения от 2026-09-11 на RTX 4080 SUPER / NVIDIA 610.47:

| Отчёт в `build/` | Viewport | Warmup / measured pairs | Float comparisons |
| --- | --- | --- | --- |
| `vl-gpu-isolated/report.json` | 256×144 | 40 / 80 | 774 |
| `vl-gpu-repeat/report.json` | 256×144 | 80 / 120 | 918 |
| `vl-gpu-final/report.json` | 256×144 | 80 / 120 | 330 |
| `vl-gpu-final-512/report.json` | 512×288 | 40 / 80 | 330 |
| `vl-gpu-final-512-repeat/report.json` | 512×288 | 60 / 100 | 789 |

Во всех пяти отчётах comparisons прошли, `maxAbs=5.960464477539063e-8`,
все query pairs приняты. Первые два отчёта сохраняют эксперимент с дополнительным
`OCCLUDED_SKIP`: повтор подтвердил регрессию lit/cookie, и этот кандидат удалён.
Их GLSL-снимки отличаются от финальной библиотеки с тремя принятыми оптимизациями;
для её A/B использовать последние три отчёта и проверять `currentSha256`.

Эффект mixed при 512×288 неустойчив: первый final run дал 1.273088 → 1.295744 мс
(+1.78% времени), повтор — 1.334528 → 1.260160 мс (−5.57%). Это не подтверждённый
выигрыш mixed-сцены. Отдельные switches в сценах без соответствующего вида света
также дают несколько процентов разброса; такие малые отличия нельзя надёжно
приписать одной оптимизации, учитывая шум GPU и изменение компоновки программы
драйвером. Сырые пары сохранены для проверки; игровой A/B остаётся отдельной задачей.

## Ограничения и реальный A/B

Fixtures не воспроизводят весь shaderpack, Iris transformations, сцену Minecraft,
геометрию глубины, temporal history, TAA, composite upsample или давление игровых
атласов на VRAM/cache. Шум и cookie используют float-текстуры. Cookie 32×32
с NEAREST отличается от игровых cookie 512×512 с LINEAR. Атласы 384×384
существенно меньше игровых; базовый spot tile 96×96 не воспроизводит игровую
разметку из степеней двойки. Hi-Z flags сравниваются, но harness не доказывает
фактическое выполнение веток `segLit`/`segOcc`: у типичных spot fixtures
`dMax=range` блокирует их guard, а mixed не имеет счётчиков попаданий в эти ветки.
Эти неизменённые ветки требуют отдельной сцены для самостоятельной проверки Hi-Z.
Плоскость конца лучей фиксирована. Mixed scene
разнообразнее одинаковых ламп, но также синтетическая. Два размера viewport
помогают обнаружить зависимость результата от числа пикселей; не заменяют игру.

Сентябрьский baseline 26 spots доказал цену целого `deferred2` около 0,08672 мс
в окне 600 кадров. Июльская запись сообщает baseline 2,761 мс, differential cost
теней 1,486 мс и bare pass 1,494 мс: это другой workload, сравнивать числа между
сессиями нельзя. 1,486 мс — **разность**, shadows-OFF там было бы около 1,275 мс.
Bare pass не доказывает чистый ALU-предел: остаются dither/depth, SSBO/cluster и
потенциально cookie. Разности отдельных эффектов не складываются.

**Global VL intensity=0 не выключает GPU-марш:** `VlGlobalsBuffer.set` поднимает
ноль до `1e-6`, а библиотека умножает итоговый цвет лишь после полного марша.
Для отключения всего прохода предназначен compile option `IRLITE_VOLUMETRIC`.
Для измерения принятой VL-оптимизации сохранять эффекты и интенсивность одинаковыми.

Игровой A/B требует одинаковых мира/камеры/FOV, framebuffer и фактического VL
viewport, настроек, источников, shader hashes и режима profiler. Семь feature
sweep-вариантов для этого не нужны: baseline/current, при необходимости A/B/A.
Снять metadata после прогрева, отслеживать drift внутри окна, требовать 600
полных GPU-кадров **и наличие deferred2 в каждом**. Стартовый JSON сессии этого
не гарантирует. Обычный режим CSV analyzer сохраняет прежнее поведение с нулём
для отсутствующей GPU-метрики; для A/B применять его строгие проверки:

```powershell
& ./tools/analyze-profile.ps1 -Capture <capture-directory> -RequireGpuMetric deferred2 -RequireCompleteWindow -WarmupFrames 240 -MeasureFrames 600
```

Строгий режим отклоняет отсутствующую метрику и неполное окно; четыре regression
checks покрывают пропуск метрики/окна и допустимое настоящее нулевое значение.
`profileVlSweep=false`; deferred2, composite1 и frame-time анализировать отдельно.
