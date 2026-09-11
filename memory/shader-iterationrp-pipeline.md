---
name: shader-iterationrp-pipeline
description: "IterationRP base pipeline IRLite hooks into — Composite/Soild_FS (deferred opaque), Volumetric_FS (fog), Gbuffers/Entities_FS; the pack symbols/conventions the injection treats as a contract, plus the exact hook anchors."
metadata:
  node_type: memory
  mod_scope: shader-inject
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: phase3-shader
  modified: 2026-07-24T10:46:39.551Z
---

IterationRP base pipeline that IRLite injects into — documented SEPARATELY from the inject itself. Parent: [[MEMORY]]. The inject is in [[shader-irlite-glsl]] / [[shader-shadow-sampling]] / [[shader-volumetric]] / [[shader-settings]]. Source of truth for hooks: patches/iterationrp.irlpatch (see [[patcher]]). Pristine pack lives at Shadres/Original/IterationRP/shaders (folders refactored 2026-06-11, see [[sync-workflow]]); the dev copy is hand-edited under Shadres/Modification/<pack>, then diffed into the .irlpatch.

IterationRP = a heavy path-traced/deferred Iris pack by Tahnass (option.iterationRP_VERSION="iterationRP by Tahnass"). Deferred: gbuffer programs write G-data into colortex; composite programs read it back and do all lighting. IRLite adds its point/spot lighting AFTER the pack's own sun/held/PT lighting, into the same `color` accumulator, in the composite passes.

THREE HOOKED PROGRAMS (only these are touched):

1. Lib/Programs/Composite/Soild_FS.glsl — the main opaque deferred shading pass.
   - RENDERTARGETS 6; out framebuffer_mainOutput. Reads gbuffer via GetGbufferDataSoild() into `GbufferData gbuffer`.
   - Key locals at the inject point: worldPos = camera-RELATIVE world-space frag pos (= mat3(gbufferModelViewInverse)*viewPos — ROTATION-ONLY: the modelview translation column is dropped, and it carries the VIEW-BOBBING offset! True player-relative pos = worldPos + gbufferModelViewInverse[3].xyz — the pack's own idiom at the voxelPos/ShadowTracing call sites; absolute = cameraPosition + that. Found 2026-06-12: bare worldPos makes the light pattern wobble with bobbing ON). gbuffer.worldNormal world-space normal. gbuffer.material.roughness/metalness. color = running linear HDR radiance.
   - Overworld branch builds sunlight + specular, ending with the two lines IRLite anchors on:
       color = color * (1.0 - metalnessMask * METALMASK_STRENGTH) + textureLighting;
       color = color * gbuffer.albedo + sunlightSpecular;   <-- IRLite diffuse goes BEFORE this, specular AFTER.
   - Diffuse is injected pre-albedo-multiply intentionally? NO — see [[shader-irlite-glsl]]: diffuse is added to color BEFORE color = color*albedo+..., so IRLite diffuse is itself multiplied by gbuffer.albedo (acts like incoming light on the surface). Specular is added AFTER (already-shaded additive highlight, like sunlightSpecular).

2. Lib/Programs/Composite/Volumetric_FS.glsl — the fog / volumetric pass (RENDERTARGETS 6).
   - Locals at inject point: cameraPosition (uniform, absolute), rayWorldPos = worldDir * clamped view distance (camera-relative end of the view ray to the opaque/water hit), worldDir = normalized camera-relative view direction. color = scene radiance so far.
   - Anchor is the pack's own fog call:
       if (fogTimeFactor > 0.01 && isEyeInWater == 0) VolumetricFog(color, vec3(0.0), rayWorldPos, worldDir, globalCloudShadow, fogTimeFactor);
     IRLite volumetric is added AFTER it (additive inscatter). See [[shader-volumetric]].

3. Lib/Programs/Gbuffers/Entities_FS.glsl — entity/model-block gbuffer (shared by Entities/Spidereyes/Block/Hand programs).
   - Writes framebuffer_gsoildData = vec4(... , Pack2xU8_to_U16(vec2(parallaxShadow, v_materialIDs/255.0)), ...). materialID byte stored in the .z channel of colortex1.
   - IRLite's only edit here: replace the materialID pack with (v_materialIDs + 128.0)/255.0 — sets bit 7 of the material byte to flag "this fragment is an entity/model, not terrain". The pack's own decode masks materialID with &127 elsewhere, so materialID stays intact; bit 7 is free. Read back in Soild_FS for per-light entitiesOnly. See [[shader-irlite-glsl]] (irlite_nonTerrain).

PACK SYMBOLS THE INJECT DEPENDS ON (must exist in IterationRP for the patch to compile — the contract surface):
- struct GbufferData { vec3 albedo; vec3 worldNormal; vec3 vertexNormal; vec2 lightmap; float materialID; float parallaxShadow; Material material; } (Lib/GbufferData.glsl).
- struct Material { float roughness; float metalness; float emissiveness; float scattering; float reflectionStrength; }.
- vec3 SpecularGGX(vec3 n, vec3 v, vec3 l, float roughness, vec3 f0)  (Lib/Utilities.glsl) — reused by irlite_lightSpecular.
- float LinearDepth_From_ScreenDepth(float depth)  (Lib/Uniform/GbufferTransforms.glsl) — used by irlite_outlineFactor on depthtex0.
- #define FBTEX_GSOLID_DATA colortex1  (Lib/Settings.glsl) — the gbuffer data target; irlite_nonTerrain reads .z (materialID byte) from it.
- uniform vec3 cameraPosition (absolute), depthtex0, gl_FragCoord — standard Iris/pack uniforms.
- Both Soild_FS and Volumetric_FS already #include "/Lib/BasicFunctions/Blocklight.glsl" — that include line is the stable anchor the inject puts #include "/Lib/irlite_lights.glsl" AFTER (so the SSBO + functions are visible in main()).

HOOK ANCHOR INVENTORY (exact, from the .irlpatch — keep in sync with [[patcher]]):
- Soild_FS:      after  #include "/Lib/BasicFunctions/Blocklight.glsl"  -> include irlite_lights
                 before color = color * gbuffer.albedo + sunlightSpecular;  -> read irlite_nonTerrain + add IRLITE_DIFFUSE
                 after  color = color * gbuffer.albedo + sunlightSpecular;  -> add IRLITE_SPECULAR
- Volumetric_FS: after  #include "/Lib/BasicFunctions/Blocklight.glsl"  -> include irlite_lights
                 after  the VolumetricFog(...) call line  -> add irlite_volumetric
- Entities_FS:   replace the Pack2xU8_to_U16(vec2(parallaxShadow, v_materialIDs / 255.0)) with the +128.0 variant.
- shaders.properties + lang: see [[shader-settings]].

Порт — выполнено (лог в _archive):
- TAKE-3 reintegration phases 0–5 done from 2026-06-12, gate-verified in-game; #version 430 -> SSBO + samplerCubeArray native.
- Lessons: view-bobbing worldPos fix (Phase 2); outline excised then re-added.
- AE-crater pitfall: unbounded additive HDR outline term after albedo-multiply -> Exposure_CS tile-mean AE craters -> scene darkens. Fix = OLD fresnel outline integrated PRE-albedo (pre-multiply diffuse). Outline done 2026-06-29, LOCAL-ONLY (not in git; author Tahnass permission given, commit deferred). NOTE: IterationRP VL still UNSHADOWED (known-open). Cross-pack record = [[project-photon-outline-switch-to-old]].

ПОЛНАЯ СИНХРА С CR 2026-07-21 (**коммит 86fdd94**, ветка optimization/octahedral-point-shadows, рантайм PASS, НЕ пушено). 8-й (и последний DEFERRED) пак после Photon; поднят с ~3-поколенческого отставания + СЛОМАННЫЕ point-тени (7× снятый cube-array `irl_pointShadowArray` → sampler2D `irl_pointShadowAtlas`). lib 1280→1768. **МЕМ «iterationrp.irlights gitignored / НЕ коммитить» УСТАРЕЛ**: .gitignore правила НЕТ, патч git-tracked (b7c57ca→86fdd94), коммитится ИНЖЕКТ как остальные 6 (не приватный пак Tahnass). Understand-workflow 5 агентов + review-workflow 3+verify (**0 подтверждённых проблем**; единственная info-находка про `*5.0` энергобейзлайн опровергнута — байт-идентична Photon).
СТРАТЕГИЯ = CR-lib база + **секционная сборка** детерминир. `scratchpad/build-iterationrp-lib.ps1` (line-based marker-splice + per-line transforms, assert'ы exactly-once, финальные grep-проверки). Surface-half = ПОЛНАЯ замена hand-written блоком `scratchpad/itr_surface_block.glsl` (Photon-петля verbatim: cluster+P5+IRLITE_SHADOWS_LIVE+shadow-LOD, + IterationRP-shell). Дуал-верификация: `git diff --ignore-cr-at-eol` собранного vs CR-lib = РОВНО ожидаемые дельты. **ХУКИ ПРАВОК НЕ ТРЕБУЮТ** (ключ!) — OLD-gen сигнатуры совпали с CR-gen: Soild_FS 8-арг `irlite_lightSurface(worldPos+MVI[3], worldNormal, normalize(-worldPos), clamp(roughness,0.0015,0.9), f0=mix(0.04,albedo,metalness), nonTerrain, diff, spec)` (diffuse+outline pre-albedo, spec post), Volumetric_FS 4-арг `irlite_volumetric(MVI[3], MVI[3]+rayWorldPos, worldDir, dither)` (bob-safe, dither=hash — нет irl_blueNoise), Entities_FS +128 bit-7. Патч 11 ops (RETARGET 2026-07-24: 0.8.24→**0.8.26**, sliders anchor _X/_Y + dropped SSBO-feature op т.к. пак сам объявляет `CUSTOM_IMAGES SSBO`; детали в [[patcher]]), byte-proof ПУСТ. IN-GAME PASS 2026-07-24 (runClient 1.20.4/JDK21 quickplay=Testing: Iris pipeline overworld создан БЕЗ Failed-to-compile/link, irl-core point-evsm atlas quality ULTRA, все IRLITE_* опции разрешились в Iris-меню, clean shutdown exit0; визуал света НЕ проверялся — только компиляция/рантайм-инициализация. Безвредные WARN не наши: пак-опции PT_TEMP_COORD_OFFSET/END_PLANET_WEAK_DIFFUSE unresolved + Caxton trimToWidth на ShaderPackScreen). Dev-пак = run/shaderpacks/IterationRP_IRLights.
lib-ДЕЛЬТЫ (CR-lib → IterationRP): (1) DROP IRLITE_ACTIVE (DH_TERRAIN/WATER/VOXY_PATCH тут не определены; IterationRP свои = DISTANT_HORIZONS/VOXY); surface/VL-гейты `IRLITE_SURFACE_PASS`/`IRLITE_VL_PASS` **1:1 БЕЗ rename** (в отличие от Photon PROGRAM_DEFERRED4/COMPOSITE0 — самое крупное упрощение); shadow-gate drop `COMPOSITE1 ||`. (2) outline: DROP COMPOSITE1-хост+upsample(colortex10), **fold в diffuseOut** (8-арг сохр., call-site не тронут, NaN-guard покрывает), UBO-driven (bit8/target/bit9/bit10/vlE/vlF, fallback #define при !GLOBALS_OK), depth = `depthtex1`+`LinearDepth_From_ScreenDepth`+`UNIFORM_SCREEN_SIZE` (НЕ CR mat4 gbufferProjectionInverse — у IterationRP только gbufferProjectionInverse0/1 vec4/vec3; НЕ Photon combined_depth_tex). (3) specular = пак `SpecularGGX(n,v,l,roughness,f0)` angle-only → world-space (n,v,L) валидно; f0=mix(0.04,albedo,metalness) на хуке; НЕ CR view-space GGX, НЕ Photon get_specular_highlight; doSpec=roughness<=0.95 БЕЗ nonTerrain-гейта (deferred = real material). (4) colour **raw linear Rec.709** (нет rec2020/rec709 в паке; drop CR `pow(1/2.2)`, drop Photon `rec709_to_rec2020`) — surface+VL lightCol оба raw. (5) noise: inline `irlite_noise3D` (noise.png=**128px**=CR → делитель 128 + morph 512 verbatim, .r канал, `textureLod` @430), swap CR `Noise3D`→inline ×2 (нет пак-Noise3D). (6) strip ВСЕ 8 in-file `#extension` (SSBO/UBO/420pack/cube-array/gather/EXT_texture_array/gpu_shader5 = core @430; в отличие от Photon #400 — НОЛЬ wrapper-плумбинга). (7) **KEEP `readonly restrict`(b7)/`readonly`(b6) SSBO-квалиф** — парсятся @430 (пруф пак-SSBODeclare; в отличие от BSL #120 стрипа). (8) cluster импортирован, no-arg fetch divisor `viewWidth/Height`→`UNIFORM_SCREEN_SIZE.x/.y` (render-res, TAAU light-drop fix; робастно: native UNIFORM_SCREEN_SIZE=screenSize=viewWidth=no-op, FSR=fsrScreenSize=render-res; VL uv-вариант `irlite_clusterWideBase(texCoord)` не тронут). (9) DROP IRLITE_VL_RESOLUTION (native VL, нет reduced-res буфера); strip `// [..]` с 17 опций (кроме 4 слайдеров INTENSITY/SPECULAR_INTENSITY/TOON_BANDS/TOON_SMOOTH — иначе stale <pack>.txt override).
properties: flat `screen.IRLIGHTS` (CR-вербатим МИНУС IRLITE_VL_RESOLUTION) + 5 sub-screens удалены + sliders→4 (`scratchpad/flatten-itr-properties.ps1`, CRLF-preserve). lang не тронут (мёртвые sub-screen/outline ключи безвредны). iris.features.required=CUSTOM_IMAGES SSBO уже был. gen-iterationrp-patch.ps1 3 правки (L75-77 screen-end detector→flat`screen.IRLIGHTS ...IRLITE_SHADOWS`, L81 outline-assert→no-sub-screen-assert, L86 sliders-tail→IRLITE_TOON_SMOOTH). Java не трогал (bindAll pack-agnostic).
ГОЧА (важно на будущее): **double-VL был ЛОЖНОЙ тревогой** — grep `Volumetric_FS` матчит подстроку `SpecularVolumetric_FS.glsl` (composite43, reflection RT:8, БЕЗ IRLite); реальный `Volumetric_FS.glsl` ТОЛЬКО в composite51 (RT:6), инжект под `DIMENSION_OVERWORLD→VFOG` (overworld-only, guard не нужен). VolumetricFog-якорь = **7-арг** (добавлен fogTransmittance; старый 6-арг мем устарел). Рантайм: dev-копия run/shaderpacks/IterationRP_IRLights (=полный Modification, `.txt` удалён); лог run/runclient-console.log показал 0 Failed-to-compile/link, point-atlas 6144²+spot-EVSM+quality ULTRA→HIGH, BUILD SUCCESSFUL. ПОСЛЕ IterationRP → **Bliss** (последний; #120 dual-hook MVI[3] bobbing). → [[photon-pipeline]] (прецедент deferred), [[rethinkingvoxels-pipeline]]/[[bsl-pipeline]]/[[solas-pipeline]], [[shader-volumetric]], [[project-photon-outline-switch-to-old]], [[plan-vl-3c-bilateral]].

Связь: shader-inject (инжектируемый GLSL + .irlights, авторинг в IRLite, синк в redactor через copy-patches.ps1; redactor только потребляет). Дополняет [[project-port-1211]] (iterationrp как непокрытый пак ~12 ops) и [[reference-edit-routing-by-area]]. Источник: память IRLite.
