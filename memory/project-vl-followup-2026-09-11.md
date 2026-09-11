# VL follow-up, 2026-09-11

## Запрос и версия

Пользователь отдельно выделил VL как второй по стоимости компонент после
бейка и разрешил субагентов. Предыдущие CPU/overlay изменения сохранены.
Версия core/IRLite и tools/VERSION остаётся 1.1.6. Собран addon для MC 1.20.4;
порт на другие MC-линии и общий релиз не выполнялись. Коммитов/push нет.

## Что изменено

Пилот: Complementary Reimagined r5.8.1, только VL-половина
`code/bbs-irlights-addon/Shadres/Modification/ComplementaryReimagined/shaders/lib/irlite/irlite_lights.glsl`.

- Cookie/gobo: basis/fY/scale/sin/cos готовятся один раз на источник;
  при наличии spot shadow используется готовый basis. Порядок вычисления
  мировых координат и UV сохранён, surface-cookie helper не менялся.
- Point shadow: block/tier decode и textureSize вынесены из шага марша.
  Cube face selection, UV clamp и depth sampling остаются на каждом tap.
- Точные gates: при noiseAmount=0 не считается noise, при tipBoost=0
  не считается tip exp. Epsilon/параметры качества не добавлялись.
- IRLITE_VL_COOKIE_HOIST, IRLITE_VL_POINT_HOIST, IRLITE_VL_ZERO_KNOBS:
  внутренние compile switches default1 для воспроизводимого A/B, без нового UI.
- OCCLUDED_SKIP удалён после GPU-проверки: совпадал по изображению, но
  замедлял lit-cookie примерно на 5–7%, shadows-off на 6%; выгода на полностью
  закрытых лучах лишь 2–3%. Не возвращать без новых измерений.

Прежний порядок HG/lightT/visibility/transmittance, число steps, strides,
integrator, cutoff и разрешение сохранены. Surface helpers не менялись.
Half-res/bilateral, blue noise, cluster cull, spot Hi-Z уже были реализованы.
Froxel/history, новая интеграция и порт на остальные шесть shaderpacks отложены.

## Файлы и доставка

Main `bbs-irlights-addon/patches/complementaryreimagined.irlights` регенерирован,
синхронизирован в `irlights/src/client/resources/assets/irl-redactor/patches/`
и `bbs-dof-addon/patches/complementaryreimagined-irl-dof.irlights`.
DOF-tail сохранён. Скрипты генерации CR и DOF теперь используют относительные
пути вместо старого C:/Users/Qualet. Отдельные JAR редактора/DOF не пересобраны.

Локальный runtime
`bbs-irlights-addon/run/shaderpacks/ComplementaryReimagined_r5.8.1_IRLights/`
получил ту же VL-библиотеку. Мир/конфигурация не менялись. Важно: Git игнорирует
Modification и run; их изменённый GLSL отдельно включён в перенос.
Новый JAR содержит свежий CR patch, но для существующего shaderpack требуется
re-patch чистого CR или обновление GLSL в точно такой же копии r5.8.1.

Артефакт: `deliverables/optimization-2026-09-11/irlite-1.1.6+mc1.20.4.jar`.
Папка переноса: Desktop/IRLights-changes-1.1.6-2026-09-11, staged-копия в
deliverables. README и manifest обновляются; содержит изменённые файлы четырёх
репозиториев, полную claude-memory, тесты, GPU-отчёты и JAR. Это overlay на
существующий проект, не полный checkout. Для продолжения нужен pristine CR
и локальные BBS-библиотеки из основной копии проекта.

## Доказательства и ограничения

Приоритеты, окончательные GPU-цифры и команды:
`code/bbs-irlights-addon/docs/performance-vl-2026-09-11.md`.

Point CPU/source: 4 576 363 бит-точных проверки / 1 004 640 samples PASS,
30 blocks, шесть faces, четыре размера атласа, signed zero/axis ties,
near/depth/clamp boundaries. Patch engine: main/combo применяются, все 434
shader-файла main совпадают с Modification, VL main/combo/editor согласован.
Сборка addon и bytewise nested core 1.1.6 PASS; core не менялся в VL-проходе.

Standalone GPU harness (`tools/verify-vl-gpu.ps1`, `tools/vl/VlGpuTest.java`):
скрытый GLFW на RTX 4080 SUPER, фактический полный GLSL и CR Noise3D, реальные
SSBO/UBO/textures, float framebuffer parity и alternating GPU timer A/B.
Baseline сохранён в `tools/vl/reference/`; не перезаписывать при следующих
оптимизациях. Raw CSV, shader sources, driver sources и report.json сохраняются
в build/vl-gpu*. Первые isolated отчёты содержат отклонённый OCCLUDED_SKIP,
для финальных цифр брать отчёты final. Синтетическое ускорение не равно FPS.

Final256/512/512-repeat: 330/330/789 framebuffer comparisons PASS, maxAbs
5,96e-8; все timing пары валидны (120/80/100 на окно). Point/spot с тенями:
около 8–15% меньше времени синтетического draw. Mixed512 нестабилен:
первый +1,8% стоимости, повтор −5,6%; для mixed выигрыш не утверждается.
Небольшие isolated-разницы находятся внутри наблюдаемого разброса окон.
Final shader SHA256: 64fe36fe94398bbdf592e5e0390d889658cb19e7dcbe68a576fc967bd89c64cd.
Final addon SHA256: 4B4101D6E911629A372995A0D83C93D08E8FE9D2906DC147224F28738DD5A41C.
Fixture atlas384/cookie32NEAREST отличаются от игровых; все Hi-Z fast paths
не доказаны. Полный Iris pipeline и игровой FPS не проверялись.

Таймеры первоначально выдавали нули на коротком draw. Harness делает batch
из нескольких draw и делит время; невалидная пара целиком исключается,
для PASS нужно >=90% валидных пар. Окончательные прогоны должны сохранять raw
CSV и число принятых пар. Не выдавать пропавший GPU-замер за ускорение.

`tools/analyze-profile.ps1` теперь умеет -RequireGpuMetric deferred2
-RequireCompleteWindow: GPU-pass обязан присутствовать во всех выбранных
полных кадрах. Явный 0 допустим; отсутствующий metric не равен нулю.
Четыре новые strict-CSV регрессии дополняют прежние 23 profiler checks.

Июльский deferred2 до 8,2 мс взят из старой memory, исходного CSV здесь нет.
Bare sweep 1,494 мс нельзя считать чистым ALU floor: в нём остаются setup и
texture/buffer reads. Сентябрьские 0,09 мс — другая сцена с 26 spots.
Global intensity=0 НЕ выключает march: Java clamp до 1e-6, масштабирование
в конце GLSL. Для no-VL контроля выключать IRLITE_VOLUMETRIC compile option;
для old/new A/B держать intensity>0 и все effects/steps одинаковыми.

## Следующее действие

Игровой A/B тяжёлой сцены открыт: одинаковые мир/камера/разрешение и эффекты,
point/spot/mixed, cookie с тенями и без, narrow cones, morph/noise/Hi-Z,
визуальная проверка и полный CSV с deferred2. В этом проходе Minecraft
не запускался; OpenGL проверялся отдельным harness. На другой GPU повторить
standalone тест перед выводами о скорости. Не понижать качество ради цифр.

Для этого ПК JDK21: C:/Program Files/Java/jdk-21, GradleUserHome:
IRLights/.gradle-user, local Maven: IRLights/.maven-local. Скрипты используют
LWJGL/GLFW3.3.2+natives-windows из заполненного Gradle-кэша. При sandbox
AccessDenied cached JAR проверку запускать с разрешённым доступом к кэшу;
не считать один exit0 при stderr AccessDenied чистым результатом.
