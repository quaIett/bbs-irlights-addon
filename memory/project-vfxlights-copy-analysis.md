---
name: project-vfxlights-copy-analysis
description: Анализ мода VFX-LIGHTS (автор Xavin) на предмет заимствований из IRLite — вердикт и доказательства
metadata:
  node_type: memory
  type: project
  originSessionId: c1fb91f7-ed7c-47ef-a761-e11d5a69b937
  modified: 2026-07-26T14:01:12.549Z
---

Анализ 2026-07-26: `C:\Users\Qualet\Desktop\Exosed_IRLIghts` = VFX-LIGHTS-0.1.0-1.20.1.jar (свет, BBS-аддон, автор Xavin) + BBS-VFX-build-1.2 (физика/смиры — чист, света нет). Декомпилят/распаковка в scratchpad сессии c1fb91f7.

**Вердикт:** не побайтовая кража Java; самостоятельная реализация, построенная на изучении IRLite изнутри (наши .irlights-патчи с комментами шипятся в нашем jar — оттуда читали). Архитектурный скелет и все точки интеграции — наши; ~70-80% их кода (фичи, тени, математика) — своё.

**Прямые доказательства (в их файлах):**
1. `iterationrp.vfxpatch`: «the same hook the IRLite light mod uses on this pack», «the exact expression IRLite feeds its light pass» (worldPos+MVI[3] анти-боб).
2. `vfxlights_lights.glsl:956`: «IRLite guards the same way ("AE poisoning prevention")» — дословная цитата нашего коммента из `iterationrp-irl-dof.irlights:488`; их коммент начинается нашей фразой «NaN/Inf insurance».
3. `complementary.vfxpatch`: все 3 якоря дословно = наши (mainLighting.glsl: include после ggx.glsl; diffuse после строки `finalDiffuse = sqrt(max(...))`; spec после `color.rgb += lightHighlight;`) — производный патч.

**Скопированная архитектура:** Form→collect→SSBO camera-relative→патч-DSL с якорями (`@target`, after-ops, блоки `<<<`/`>>>`)→общий GLSL через include; Iris-миксин ProgramSamplers.Builder#build@HEAD+addDynamicSampler; BBSMod.onInitialize@TAIL регистрация форм (те же ID point_light/spot_light); ExtraFormSection.initiate@TAIL; WorldBlockChangeMixin (имя 1:1, target setBlockState(II)+isClient = наша версия ДО фикса 1.1.4); имена классов PointLightForm/UIPointLightFormPanel 1:1.

**Их своё:** SSBO binding 5/64 ламп/15×vec4; in-memory патчинг через IncludeGraph.readFile (у нас физическая запись пака); тени = 2D-атлас+кубогран-тайлы+PCSS+цветные витражи; area/ambient/barn/IES/cel/температура/dispersion/bounce/flare/water/char-mask; свой не-Iris рендер-путь (LightCompositor); патч Eclipse (мы не покрываем).

Атрибуции наружу ноль (fabric.mod.json authors=[Xavin], LICENSE-файла в jar нет). MIT-претензия слабая (дословный код почти не копирован, кроме производного complementary-патча); моральная — очевидная, источник признан в их же комментах.
