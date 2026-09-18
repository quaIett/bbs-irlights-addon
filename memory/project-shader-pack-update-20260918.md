---
name: project-shader-pack-update-20260918
description: "Обновление патчей и Original/Modification по шести архивам Prism 1.21.11(2); игровые проверки открыты."
metadata:
  node_type: memory
  type: project
---

09-18 пользователь поручил обновить патчи и папки под архивы C:/PrismLauncher/instances/1.21.11(2)/minecraft/shaderpacks. Выполнено на addon master; core/порты/DOF не менялись, commit/push отсутствуют.

Версии: ComplementaryReimagined r5.9.3; BSL v10.1.5; Solas V3.7b; Photon v1.3b; RethinkingVoxels r0.1-beta9; Bliss V2.1.2. Последние три уже совпадали с базой GLSL, у Bliss дополнительно 3 файла архива. IterationRP отсутствует во входной папке, не менялся.

Complementary: replay attachment colortex9 адаптирован под новый hand 036/0364, добавлен в новые basic/textured reflection branches и entities_translucent с native colortex13. Сохраняются native targets/depth. Генератор gen-complementary-patch.ps1 теперь пишет @packversion r5.9.3; регенерация byte-identical.
BSL: сохранён новый opaqueMask у block, перенумерованы targets opaque entities без старого VL attachment1. Новый upstream gbuffers_entities_translucent.glsl получил uniform irlite_replayId, IRLITE_NONTERRAIN, entityMask и replay attachment11 во всех ветках. gen-replay-patches.py воспроизводит новый patch (74ops).
Solas: удалён upstream Intel guard в shaders.properties; якорь и body адаптированы без лишнего #endif, VL toggles/размер буфера сохранены. 51ops.

Папки Shadres/Original и Shadres/Modification обновлены для6паков. Original byte-identical распаковке входных архивов. Прежние деревья/патчи/генератор/editor-patches/run-patches/patches.zip сохранены в Shadres/Backups/shader-update-20260918-194757. Не править pristine Original вручную.
Канонические patches/*.irlights синхронизированы в run/irlite/patches и irlights main bundled resources; patches.zip пересобран (7патчей в корне). JAR НЕ пересобирались; другие ветки и прежние run/shaderpacks НЕ обновлялись.
В Prism shaderpacks созданы6готовых папок <имя нового zip без .zip>_IRLights. Входные zip/toml не менялись, активный shaderpack не переключался. Повторно патчить готовые папки нельзя (есть irlite_patched.txt).

Проверки: Java PatchHarness применил6патчей непосредственно к zip;2468файлов round-trip и установленные паки byte-identical Modification; metadata markers исключены из сравнения. @packversion актуален. properties #if/#endif сбалансированы. Копии патчей+patches.zip сверены. git diff --check PASS.
GPU offline ShaderCompileCheck:288fragment-программ трёх изменённых паков;258PASS,30FAIL с идентичными диагностическими сообщениями на pristine (CLRWL/DH без runtime preprocessing, не новые регрессии). BSL блок/entities/entities_translucent,3мира×5конфигураций PBR/MCBL/TAA=45PASS. GLSL-библиотеки света не менялись. Iris runtime/linking/визуальная проверка OPEN у пользователя.
Отчёты build/shader-update-20260918/{verification,compilation}.json, compile-* и *-final.log; финальный BSL roundtrip в final-bsl/BSL. Устаревшие промежуточные Modification/Modification2/final/BSL внутри build не источник правды.

09-18 FOLLOW-UP: пользователь поручил пересобрать все аддоны/редакторы с новыми патчами на рабочий стол. DONE:4addon MC1.20.1/1.20.4/1.21.1/1.21.11 и5editor MC1.20.1/1.20.4/1.21.1/1.21.4/1.21.11. Версия1.1.7. Выдача Desktop/IRLights-1.1.7-new-shaders/{Addon,Editor}/<mc>/*.jar +README.txt +verification.json.
Канонические7патчей синхронизированы в активные addon port worktree1.21.1/1.21.11 и4editor port worktree. Main addon/editor уже синхронизированы выше; detached _wt-addon-1.20.x НЕ источник/не обновлялся. Исходники Java не менялись; прежние WorldLightGuideOverlay исправления включены во все4addon.
Последовательная сборка: release core _wt-irl-core-1.20.x/main79ec4ad,1.21.1/8768fda,1.21.4/b7a5265,1.21.11/53db073 publishToMavenLocal → потребители build --refresh-dependencies --no-daemon. JVM Gradle21.0.12.101; для1.20.x init-script options.release17. Экспериментальный core2.0 не использован. После завершения MavenLocal восстановлен на release1.20.x. Основной addon настроен последней своей сборкой на1.20.4.
Все9build PASS. Каждый JAR: ровно1вложенное release core1.1.7 SHA-byte-match своей линии,7patch byte-match canonical (всего63), mixin+dependency inventory PASS, recursive runtime class majors<=61 для1.20.x/<=65 для1.21.x (multi-release higher-version variants исключены), классы EVSM+profile-v2 на месте. Все4addon содержат WorldLightGuideOverlay.class. Десктоп-копии SHA проверены. Runtime этих9новыхсборок NOT_RUN;не заявлять визуальную приёмку.
Повторяемые helper и логи: addon/build/shader-release-20260918/{build-all.ps1,verify.py,finalize.py,java17.gradle,*.log}; backup-* содержит прежние port patch dirs. Git Java/source-код кроме shader resources не менялся; commit/push не выполнялись.

ЧЕКПОИНТ09-18: пользователь явно поручил закоммитить вообще все оставшиеся изменения. Шейдерные патчи/генератор/память фиксируются в3ветках addon и5ветках editor; существующий CpuProbe/profile61.1 код — отдельно вcore2.0,7логов прежних проверок портов — в3release ветках core. Без push,без восстановления stash,без новых функциональных правок/пересборок. Runtime ограничения прежние. Финальный рабочий статус всех14существующих рабочих копий трилогии проверяется на CLEAN.
