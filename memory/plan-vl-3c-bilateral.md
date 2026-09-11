---
name: plan-vl-3c-bilateral
description: "3c ЗАВЕРШЁН+ЗАКОММИЧЕН 2026-07-18 (core db0d3e2 / addon f8d2fb7 / editor 5dd3207): bilateral upsample VL в CR, протокол юзера PASS — deferred2 3.40→0.97 ms (×3.5) на 0.5, bilateral +0.09 ms, края чистые. Half = дефолт. Открыто: тираж (вкл. +DOF-форк = re-patch). Детали/готчи внутри; next = bake-трек."
metadata: 
  node_type: memory
  type: project
  originSessionId: 1a47d7b9-f46d-49f3-a5ce-79b7f33d8d56
  modified: 2026-07-20T11:06:59.900Z
---

# VL 3c: bilateral upsample + полурез

СТАТУС 2026-07-18 (сессия 3, ЗАВЕРШЕНО): ЗАКОММИЧЕНО core db0d3e2 + addon f8d2fb7 (ветки optimization/octahedral-point-shadows) / editor 5dd3207 (main). ПРОТОКОЛ ЮЗЕРА PASS: deferred2 медиана 3.40 ms (1.0, bilinear) → 0.97 ms (0.5, bilateral) = ×3.5; цена bilateral в composite1 +0.09 ms (0.19→0.28); свипы обоих прогонов ~0.95 идентичны (bilateral на марш не влияет — подтверждено); визуально края чистые, остаточные пиксели «еле заметны» (принято). Half (0.5) = рабочий дефолт: дефайн уже 0.5, оверрайдов в конфигах чистого пака нет, юзер может вернуть 1.0 в экране Iris. Весь контракт ниже реализован, пилот CR. Изменённые файлы: composite1.glsl + irlite_lights.glsl (комментарий UBO) + deferred2.glsl (только шапка-комментарий) в Shadres/Modification; core VlGlobalsBuffer.java (0x3F→0x7F + javadoc); addon LightCollector.java (VL_BILATERAL | 64, dev-килл-свитч -Dirlite.vlNoBilateral=true, restart-only) + VlSweep.java (зеркало bit6); editor LightDriver.java (| 64). Патч отреген (21 ops, 2183 строки), byte-proof чист (дифф ПУСТОЙ, даже без lang-residue), синк run/shaderpacks + prism + copy-patches 7/7 md5. Сборки: core publish + addon + editor PASS (editor пересобран ПОСЛЕ copy-patches — бандл!). e2e quickplay+профайлер PASS: composite1 с bilateral = 0.04-0.05 ms (шум), свип 7 конфигов без выпадений bit6.

## ДЕЛЬТЫ ОТ ИСХОДНОГО КОНТРАКТА (итог workflow-ревью 32 агента, 9 minor → 2 кодовых + 3 комментарных фикса)
1. Метрика глубины = ИСТИННАЯ view-Z: nf/(f − z(f−n)), НЕ GetLinearDepth*far пака (тот = 2df/(d+f) → сигма дрейфовала бы ×0.5 у камеры / ×2 у far). Сигма честно в блоках, дефолт 1.5, оверрайд irlite_vlD.y (0=дефолт; сеттера в Java НЕТ — y пишется 0, мёртвого пламбинга не добавляли).
2. Depth-fetch с bias +0.25: ivec2(tapUv*view + 0.25) — texel-центр low-res при R=0.5/0.25 попадает РОВНО на угол full-res пикселей (2t+1), float-округление флипало выбор по колонкам (13/960 на 1920w); bias детерминирует пиксель внутри footprint, при R=1.0 floor(t+0.75)=t — маппинг не меняется.
3. bit6-off путь = буквально прежний texture2D (бит-идентичность); degenerate-фолбэк wsum<1e-3 → чистый bilinear.

## БОЕВОЙ ПАК (уточнение юзера 2026-07-20)
Актуальный/боевой пак = **`bbs-irlights-addon/run/shaderpacks/ComplementaryReimagined_IRLights`** (dev-копия под runClient), НЕ prism-форк. Следствия: оверрайда IRLITE_VL_RESOLUTION в его .txt нет → действует дефолт дефайна **0.5** (irlite_lights.glsl:41), bilateral bit6 в GLSL есть → **полурез + bilateral боевые, рычаг снят**. Устаревшая формулировка ниже («у юзера стоит 1.0», «рекомендация переключить») относилась к prism-форку ComplementaryReimagined_IRLights+DOF (там 1.0 намеренно, старое поколение) — это отставшая копия, не эталон замеров.

## ОТКРЫТОЕ ПОСЛЕ 3c
- Тираж bilateral на остальные паки (Photon = MISS by design). ВАЖНО: у юзера в prism живёт форк ComplementaryReimagined_IRLights+DOF = СТАРОЕ поколение IRLite (либа без F0/F1/F2 и UBO-эры, deferred2 без blue-noise) — слепой синк 3 файлов СЛОМАЕТ его, нужен полный re-patch; его конфиг оставлен на RESOLUTION=1.0 намеренно. BSL_IRLights+DOF.txt держит RESOLUTION=100 (процентная шкала BSL — не трогали).
- Сигма-сеттер в Java (vlD.y) — только если юзер попросит ручку.
- 0.25 = opt-in per-shot (вердикт judges не менялся).
- NEXT SESSION (по слову юзера): bake-трек — [[project-shadow-bake-perf-audit]] C10 per-face block-cull + пропуск бейка граней без кастеров (sphereTouchesFace уже считает; см. канон-строку octahedral в MEMORY.md). Bake 3.4-4 ms GPU = теперь крупнейший GPU-потребитель IRLite после среза VL.

## ГОТЧИ СЕССИИ 3 (новые)
- Два loom-билда одной MC-версии ПАРАЛЛЕЛЬНО = гонка за ~/.gradle/caches/fabric-loom/minecraftMaven → NoSuchFileException; строго сериализовать addon→editor.
- PowerShell разбирает -Pmc=1.20.4 на «-Pmc=1» — gradlew ТОЛЬКО через Git Bash (подтверждена готча feedback-addon-runclient-command и для build).
- Grep/rg молчит внутри Shadres/ из репо аддона (gitignore) — читать по абсолютному пути или искать из дира Shadres.
- Лок loom-кэша при пурже = осиротевшие демоны → gradlew --stop в ОБОИХ репо, повторить.

## ПОЧЕМУ ЭТО ГЛАВНЫЙ РЫЧАГ (данные профайлера 2026-07-18, реальная сцена юзера)
deferred2 (VL-марш) на RESOLUTION **1.0** (так у юзера) = 2.761 ms; «голый марш» без единого тапа = 1.494 ms (54% = ALU-пол). ВСЁ это масштабируется с числом пикселей → 0.5-рез режет ~в 4× число пикселей марша: ожидание ~0.8-1.0 ms вместо 2.76. Это больше, чем все точечные оптимизации вместе (cluster-cull 0.44 ms, Hi-Z 0.18 ms, morph 0.36 ms). Bilateral делает полурез визуально бесплатным (убирает ореолы на краях). Разбивка для справки: тени 1.486 ms (53.8%), шум 0.164, морф 0.361 (теперь деф. OFF), Hi-Z экономит 0.182, cluster-cull 0.441. Bake теней 3.4-4 ms GPU — ОТДЕЛЬНЫЙ будущий трек (C10 per-face cull + пропуск граней без кастеров), НЕ этот.

## РЕКОН (верифицирован 2026-07-18, content-якоря; номера строк могли дрейфнуть)
- **Композит-сайт**: `Shadres/Modification/ComplementaryReimagined/shaders/program/composite1.glsl` ~:344-354, гейт `#if defined IRLITE_ACTIVE && defined IRLITE_VOLUMETRIC`: `vec3 irliteVL = texture2D(colortex10, texCoord).rgb;` — ПРОСТОЙ bilinear, в LINEAR-пространстве ПОСЛЕ `color = pow(color, vec3(2.2))` (:342), затем underwater mult / lava zero, затем `color += irliteVL;`. composite1 = full-res (DRAWBUFFERS:0). colortex10 = RGB16F (`lib/pipelineSettings.glsl:11`), nearest-флага нет.
- **Согласованная глубина на сайте**: `float z1 = texelFetch(depthtex1, texelCoord, 0).r;` (composite1 ~:105-106) — deferred2 марширует по OPAQUE-глубине (его depthtex0 на deferred-стадии = opaque; «beams deliberately continue behind translucents»), в composite1 согласованная = **depthtex1** (НЕ z0!). Линеаризатор в том же файле (~:51-53): `float GetLinearDepth(float depth) { return (2.0*near)/(far+near-depth*(far-near)); }`. Рядом есть viewPos1/lViewPos1 через gbufferProjectionInverse (~:196-199) и DH/Voxy min-merge dhDepthTex1/vxDepthTexOpaque (~:202-214) — учесть при выборе метрики.
- **Готового bilateral в паке НЕТ** (grep bilateral|depthWeight|edgeWeight = 0). Ближайший образец идиомы reject: `lib/materials/materialMethods/reflectionBlurFilter.glsl` (normal-similarity reject + дремлющий depth-reject `abs(GetLinearDepth(...)-linearZ0)*far > 2.0`). Образец gap-fix: composite1 ~:146-160 (4-диагональный re-sample для half-res отражений, не depth-aware).
- **RESOLUTION-плумбинг**: `#define IRLITE_VL_RESOLUTION 0.5 // [1.0 0.5 0.25]` (lib :41 — ВНИМАНИЕ: дефайн-дефолт 0.5, но у юзера в Iris-конфиге стоит 1.0); `size.buffer.colortex10 = IRLITE_VL_RESOLUTION IRLITE_VL_RESOLUTION` (shaders.properties:133); program-тумблеры :121-123. RESOLUTION структурен (buffer alloc) — остаётся компильным, переключение через экран Iris с F3+R (одноразово).
- **Reduced-res готчи deferred2** (уже дважды кусали): texCoord = full-screen uv (noperspective, deferred2:28), gl_FragCoord = pass-res, viewWidth/viewHeight = full-res; прецедент решения = uv-параметризованный irlite_clusterMaskFetch(vec2 uv) в либе.
- **Дизер-сайт** (для опционального stretch-пункта): deferred2.glsl :56-68, blue-noise texelFetch по gl_FragCoord & 127, дизер входит ТОЛЬКО стартовым офсетом марша; per-step стратификация конфликтует со stride-кэшами shadowVis/noiseVal.

## КОНТРАКТ (заморозить в начале сессии, детали дорешает имплементация)
1. **Bilateral upsample в composite1** вместо простого texture2D: 4 соседних texel-центра low-res colortex10 (координаты через textureSize(colortex10,0)), вес = bilinear-вес × depth-вес; depth-вес = гауссиан/rational от |GetLinearDepth(z1_на_uv_центра_сэмпла) − GetLinearDepth(z1_текущего)| (глубина сэмпла = depthtex1 В ТОЧКЕ uv low-res центра — отдельного low-res depth НЕ существует и НЕ нужен); нормировка суммой весов; **degenerate-фолбэк**: если сумма depth-весов < eps (суб-тапная геометрия: заборы/листва) → чистый bilinear (как сейчас). Сигма глубины — в блоках, стартовое ~1.0-2.0.
2. **Управление**: flags bit6 = bilateral enable, DEFAULT ON, БЕЗ UI-тумблера (политика чистки UI 2026-07-18: технические фичи без ручек; core деф. флагов 0x3F → 0x7F). Сигму МОЖНО положить в свободный irlite_vlD.y (0 = использовать дефолт), UI-слайдер НЕ добавлять без просьбы юзера.
3. **bit6-off путь бит-идентичен** текущему bilinear (регрессионный гейт, как во всех фазах).
4. Пилот CR; Photon при тираже = MISS by design (нет RESOLUTION, нативный quarter-res fog — из mega-research).
5. **Рекомендация юзеру по итогам**: переключить IRLITE_VL_RESOLUTION 1.0 → 0.5 в экране Iris (одноразовый F3+R) — с bilateral это и есть главный выигрыш. 0.25 = opt-in per-shot (вердикт judges: на 1/16 тонкие лучи могут регрессировать даже с bilateral).
6. **Опциональный stretch** (если сессия идёт легко): stratified per-step jitter БЕЗ tent-реконструкции (вердикт judges mega-research: jitter отгружать один, tent прототипировать отдельно); помнить конфликт со stride-кэшами — стратифицировать только march-позицию, кэш-каденции не трогать.

## ПРОЦЕСС (выжимка всех готч сессий 2026-07-17→18)
- GLSL правится в `bbs-irlights-addon/Shadres/Modification/ComplementaryReimagined/` (composite1.glsl; либа только если нужен общий хелпер/флаг-бит — блок UBO уже объявлен в либе и включён в composite1? ПРОВЕРИТЬ: UBO-блок гейтится IRLITE_ACTIVE в либе, composite1 включает либу — да, но убедиться что irlite_vlC доступен в composite1-TU; если либа не включена в composite1 — объявить блок локально по контракту из VlGlobalsBuffer.java javadoc).
- Реген: `tools/gen-complementary-patch.ps1` → patches/complementaryreimagined.irlights; byte-proof: `java -cp tools/build PatchHarness patches/complementaryreimagined.irlights Shadres/Original/ComplementaryReimagined <scratch>/out` + `git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol <out>/shaders Shadres/Modification/.../shaders` = ПУСТО (известный residue: 1 CR-строка en_US.lang в strict-режиме без флага — игнорить). Синк либы/composite1 в run/shaderpacks/ComplementaryReimagined_IRLights И C:/prismlauncher/instances/BBS/minecraft/shaderpacks/... (md5-verify), затем `powershell -File C:/Users/Qualet/Documents/Project/Minecraft/BBS/irlights/tools/copy-patches.ps1` (pwsh НЕ существует).
- Java-правка core (bit6 деф.) → publishToMavenLocal → ПУРЖ loom-кэшей: аддон `.gradle/loom-cache/remapped_mods/remapped/org/qualet/irl-core-a73a62ac/1.2`, редактор `.gradle/loom-cache/remapped_mods/net_fabricmc_yarn_1_20_4_1_20_4_build_1_v2/org/qualet/irl-core/1.2`. Лок-ошибка кэша («файл занят другим процессом») = осиротевший демон → `gradlew --stop` в ТОМ репо, повторить.
- JAVA_HOME = C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot для ВСЕХ gradlew. Сборки: core publish; addon `build -Pmc=1.20.4`; editor `build`. Рантайм: `runClient -Pmc=1.20.4` (Git Bash, лог run/runclient-console.log, фоном).
- **Числа до/после — ПРОФАЙЛЕРОМ**: `-Dirlite.profileVl=true` (или автотест `./gradlew runClient -Pmc=1.20.4 -Pquickplay="Testing" -PclientJvmArgs="-Dirlite.profileVl=true"`); сравнивать медиану deferred2. Каналы: лог = правда, чат = итог свипа, HUD = live.
- Коммиты только чекпоинтами по подтверждению; память → repo memory/ → отдельный «memory:»-коммит; индекс MEMORY.md держать < ~17.5KB.

## Контракт UBO (актуальный, зеркало VlGlobalsBuffer.java)
std140 binding 7 (64Б): vec4 irlite_vlA (intensity,maxDist,tipBoost,tipRadius); vec4 irlite_vlB (noiseAmount,noiseScale,noiseSpeed,frameIndex wrap4096); uvec4 irlite_vlC (stepMax,shadowStride,noiseStride,flags: bit0 shadows / bit1 noise / bit2 blueNoise / bit3 ditherTemporal / bit4 clusterCull / bit5 hiZSkip / bit6 bilateral, деф. 0x7F); vec4 irlite_vlD (x noiseMorph деф.0, y сигма bilateral в блоках (0=дефолт 1.5; Java-сеттера нет, пишется 0), z/w reserved).

Связь: [[plan-vl-refactor-research]] (все фазы, mega-research вердикты по bilateral/stratified), [[plan-vl-profiler]] (инструмент замера + замеры 2026-07-18), [[sync-workflow]], [[reference-edit-routing-by-area]].
