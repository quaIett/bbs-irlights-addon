# VL: следующий проход оптимизации

Версия core/IRLite остаётся 1.1.6. Эталон — Complementary Reimagined r5.8.1.
Half-resolution, bilateral reconstruction, blue noise, cluster cull и Hi-Z
уже реализованы; повторно их внедрять не требуется.

## Приоритеты

| Приоритет | Место | Изменение | Проверка / статус |
| --- | --- | --- | --- |
| P1 | Шаг полностью в тени | Не считать два exp и point HG для нулевой visibility | Отклонено после повторного GPU A/B: регрессия lit/shadows-off, кандидат удалён |
| P1 | Gobo на spot | Подготовить basis/fY/scale/sin/cos один раз на источник вместо каждого шага | Реализовано; прежние UV и порядок float-координат, reuse готового shadow basis |
| P2 | Point shadow | Вынести block→atlas decode и textureSize из шага; face selection и clamp оставить per-tap | Реализовано; 4 576 363 бит-точных проверки / 1 004 640 samples PASS |
| P2 | Выключенные компоненты | Не считать noise при amount=0 и tip exp при boost=0 | Реализовано; точный ноль без epsilon, shadow/noise strides сохранены |
| P3 | Noise uniform ALU и Hi-Z duplicate taps | Hoist uniform noise constants; reuse одинаковых clamped texels | Отложено до GPU-замера первых изменений |
| P3 | Froxel/history/новый интегратор | Меняет архитектуру и требования к изображению | Отдельный этап, не часть точных локальных оптимизаций |

## Исходные измерения и ограничения

В старом июльском отчёте тяжёлый ракурс доходил до 8,2 мс deferred2. Свип
для другого ракурса: baseline 2,761 мс, дифференциальная стоимость теней
1,486 мс, bare-вариант 1,494 мс. Исходного июльского CSV здесь нет: эти
цифры сохранены в memory, а не заново измерены. Bare-вариант всё равно
содержит подготовку прохода и возможные texture reads, поэтому 1,494 мс
нельзя считать доказанным чистым ALU-пределом.

Сентябрьские 0,09 мс deferred2 относятся к другой сцене с 26 спотами и не
описывают тяжёлую VL. Нулевая global intensity не выключает работу марша:
Java поднимает её до 1e-6, GLSL умножает результат в самом конце.

Для новых правок сохраняется исходный shader в tools/vl/reference. Проверка
должна сравнивать результат и время на одних входах, с теми же шагами,
noise/shadow stride, dither, cutoff и разрешением. Standalone GPU harness
проверяет реальный GLSL/драйвер в синтетической сцене; он не заменяет игровой
A/B и не измеряет FPS всего Minecraft.

## Отклонённый кандидат

OCCLUDED_SKIP проходил сравнение изображения, но не прошёл проверку скорости.
На RTX 4080 SUPER в повторном isolated-тесте (80 warmup pairs, 120 timing pairs):
lit-cookie без кандидата 0,3695 мс против 0,3884 мс с ним (+5,1% стоимости),
VL shadows-off 0,2916 против 0,3095 мс (+6,1%). В полностью закрытой сцене
выигрыш только 2,4%, mixed-edge почти без разницы. Поэтому код этого кандидата
удалён; исходный порядок HG, lightT, shadow sampling и накопления сохранён.
Меньше выражений в исходнике не гарантирует более быстрый GPU-код.

## Результат финального варианта

GPU: NVIDIA GeForce RTX 4080 SUPER, OpenGL 4.3, драйвер 610.47. Во всех трёх
прогонах используется одна финальная библиотека SHA-256
`64fe36fe94398bbdf592e5e0390d889658cb19e7dcbe68a576fc967bd89c64cd`.
Прежняя библиотека зафиксирована в `tools/vl/reference/irlite_lights.glsl`.

| Сцена | 256×144: до → после, мс | 512×288: до → после, мс | Повтор 512×288: до → после, мс |
| --- | ---: | ---: | ---: |
| Point, edge, noise+morph | 0,3730 → 0,3370 | 1,6244 → 1,3865 | 1,6311 → 1,4227 |
| Spot+cookie, lit | 0,3960 → 0,3644 | 1,7486 → 1,5469 | 1,7640 → 1,5290 |
| Spot+cookie, occluded | 0,2989 → 0,2738 | 1,2681 → 1,1185 | 1,2952 → 1,1107 |
| Spot+cookie, zero knobs | 0,3185 → 0,2597 | 1,2522 → 1,1208 | 1,3562 → 1,0223 |
| Mixed+Hi-Z flag | 0,3190 → 0,2990 | 1,2731 → 1,2957 | 1,3345 → 1,2602 |
| Spot+cookie, shadows off | 0,3149 → 0,2881 | 1,2602 → 1,2556 | 1,3065 → 1,1978 |

В point и spot с тенями стоимость синтетического VL снижается примерно
на 8–15%. Это снижение времени конкретного standalone shader draw, не рост
игрового FPS. Для mixed результат нестабилен: +1,8% стоимости в первом 512
прогоне и −5,6% при повторе; подтверждённый выигрыш для mixed не заявляется.
Изолированные заведомо неактивные switches также дают разброс 4–7% между
окнами: небольшие разницы нельзя уверенно приписывать конкретной правке.
Для shadows-off и zero knobs разброс заметен; все результаты сохранены.

| Отчёт build/ | Warmup / timing pairs | Framebuffer comparisons | Валидные timing pairs |
| --- | ---: | ---: | --- |
| vl-gpu-final | 80 / 120 | 330 PASS | 120/120 в каждом из 6 окон |
| vl-gpu-final-512 | 40 / 80 | 330 PASS | 80/80 в каждом из 6 окон |
| vl-gpu-final-512-repeat | 60 / 100 | 789 PASS, с isolated switches | 100/100 в каждом из 24 окон |

Максимальное абсолютное расхождение во всех финальных прогонах:
5,960464477539063e-8; NaN/Inf и провалившихся компонентов нет. GL compile/link
PASS, включая отдельные builds без compiled VL shadows. Допуски и пределы
покрытия приведены ниже. Сборка `irlite-1.1.6+mc1.20.4.jar` PASS, SHA-256
`4B4101D6E911629A372995A0D83C93D08E8FE9D2906DC147224F28738DD5A41C`;
вложенное ядро 1.1.6 совпадает с локальным JAR, bytecode Java 17.

В `deliverables/optimization-2026-09-11/gpu/` сохранены финальные и два
диагностических isolated-отчёта, raw CSV, входной/driver GLSL и compile logs.
Диагностические vl-gpu-isolated/vl-gpu-repeat содержат ещё отклонённый
OCCLUDED_SKIP: не путать их с окончательным кодом. Игровой A/B остаётся открыт.

## Проверки и повторение

- `tools/verify-vl-gpu.ps1`: скрытый GLFW/OpenGL 4.3 context, фактическая
  production-библиотека GLSL и оригинальный CR Noise3D, реальные SSBO/UBO,
  cookie/shadow/noise textures. Проверяется также источник, принятый драйвером.
  Float framebuffer сравнивается до tone mapping; NaN/Inf запрещены.
  Присутствие эффектов подтверждается ненулевой энергией и отличием fixtures.
- `tools/vl/verify-point-hoist.ps1`: все 30 blocks, шесть faces, разные размеры
  атласа, axis ties/signed zero, near/depth/clamp boundaries, source guards.
- `tools/verify-vl-patch.ps1`: реальный patch engine применяет main и CR+DOF
  к чистому CR; все 434 shader-файла main совпадают с Modification, VL main/combo
  совпадает, редактор содержит тот же patch. DOF-tail сохранён.
- `tools/verify-profiler.ps1`: прежние 23 Java-проверки и CSV-регрессия,
  дополнительно четыре проверки строгого GPU-окна. Для игрового CSV используйте
  `tools/analyze-profile.ps1 -RequireGpuMetric deferred2 -RequireCompleteWindow`
  вместе с обычными параметрами пути и размера окна. Отсутствующий GPU-pass
  отклоняется, явный нулевой результат допустим.

Для локальных standalone-проверок требуется JDK 17+ (на этом ПК JDK 21).
GPU-script использует кешированные LWJGL/GLFW 3.3.2 из GradleUserHome, Windows
natives и исходную `Shadres/Modification/ComplementaryReimagined`.
Пример: `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/verify-vl-gpu.ps1
-JavaHome 'C:/Program Files/Java/jdk-21' -Width 512 -Height 288`.

GL_TIME_ELAPSED охватывает только draw, batch делится на число draws.
Порядок A/B меняется, readback и fixture uploads остаются вне таймера.
Невалидный нулевой timer отбрасывает всю пару; требуется минимум 90% валидных
пар, raw CSV сохраняется. Медианы нельзя сравнивать между разными сценами,
разрешениями или настройками как единое ускорение игры.

Ограничения fixtures: 16 ламп, атлас 384×384 (spot tile 96), cookie 32×32
с NEAREST; игровые атласы и cookie 512×512 с LINEAR создают другую нагрузку
на память. Harness проверяет один VL invocation, а не Iris transforms,
deferred2 depth reconstruction, bilateral upsample, TAA или весь кадр.
Поэтому совпадение float-выхода здесь не закрывает игровую проверку краёв
теней/Hi-Z и не обещает такой же выигрыш на всех видеокартах.
Комбинации с флагом Hi-Z сравнены, но попадание во все segLit/segOcc branches
не доказано: часть fixtures отсеивается range guard. Эти ветки не менялись.

## Установка и следующий шаг

Пилот охватывает Complementary Reimagined r5.8.1. Main-патч синхронизирован
с копиями редактора и CR+DOF; другие шейдерпаки не портированы. Новая библиотека
также копируется в локальный runtime CR. Замена JAR сама по себе не изменяет
уже пропатченный shaderpack: примените обновлённый patch к чистому CR или
обновите соответствующий файл в точно такой же копии CR r5.8.1.

Игровой gate остаётся открытым: одинаковые мир/камера/разрешение, 48 steps,
те же strides/dither/noise/morph/Hi-Z, intensity > 0 и одинаковые effects.
Сравнить тяжёлую VL-сцену, point/spot/mixed и cookie shadows-off/unmapped,
проверить изображение и полный CSV с deferred2. Не использовать intensity=0
как выключатель стоимости VL; для отдельного no-VL контроля выключается
compile-option IRLITE_VOLUMETRIC, но он не является A/B этих оптимизаций.
