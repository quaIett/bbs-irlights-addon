# Memory Index

## Правило ведения индекса
MEMORY.md <= 15 КБ (флор ~70 тем), одна строка/память, ноль декора ([[feedback-memory-strict-style]]). `_session-log.md` (журнал сессий), `_index-closed.md` (закрытые темы) — не грузятся. В шапке только «Последнее». Хуки — короткая фраза без коммит-хешей (они в топиках+git). Тема закрылась -> строку в `_index-closed.md`. Потолок превышен -> prune, не наращивать.

## Правило загрузки памяти
- (Инструкция исключительно для ChatGPT; Claude пропускает.) В начале сессии читать память проектов из указанных пользователем каталогов `.claude/projects`, обновлять её там же; общий склад и три точных пути: [reference-chatgpt-memory-location](reference-chatgpt-memory-location.md).

Объединённая база: 2 мода — IRLite (BBS-аддон), IRL-redactor (ImGui-редактор) — + ядро irl-core. Старт «поменяй X» -> [reference-edit-routing-by-area](reference-edit-routing-by-area.md). Не грузятся: `_archive/`, `_session-log.md`, `_index-closed.md`. Инфра (3 memory-дира = 1 склад через junctions): [reference-memory-junctions](reference-memory-junctions.md). Фаза 2026-07-02: чиним ТОЛЬКО main/master; порт на ветки/редактор — строго по команде.

## Журнал сессий
Полный хронолог — в `_session-log.md` (не грузится, читать вручную). Последнее (09-16): РЕШЕНИЕ — портировать пакет v2 с другой машины (Desktop\IRLights-PerLight-VL-Outline-v2: per-light VL/Outline + списки реплеев для света и аутлайна, только Complementary, база = наш 09-15 до BBS 2.6/wave 2) на master; старт в НОВОЙ сессии по [[plan-port-perlight-v2]]. Моя struct-реализация профилей (SSBO 160 Б, core 1.1.7) откачена целиком, mavenLocal core 1.1.7 лишний; в дереве осталось только оформление треков (имена/цвета/группы: TrackCatalogMixin+LightTrackLayout, компилируется, НЕ закоммичено) [[project-per-light-profiles]]. Пред. (09-12 #2): ПОРТ АДАПТЕРА РЕВИЗИЙ НА BBS 2.5.2 — DONE. BbsSilhouetteBridge (MethodHandle-мост + layout-проверка AUDITED для 2.3.1/2.5.2), гейты welds/hybrid, ModelGroup.offset в позе, rotate2→rotationMode+quat; самотесты mob PASS 11, cubic PASS 14 (новый); dev-клиент на 2.5.2 (-PbbsRuntimeJar): known 25/кадр, sp.reuse 25/кадр, gpu bake 21→0.01 мс; регресс 2.3.1 PASS; jar 1.1.6 в инстансе Prism (бэкап в scratchpad); код НЕ закоммичен; трассировка -Dirlite.debugCasterRevisions. ОТКРЫТО: пользовательский A/B на Prism [[project-caster-revision-gated-bbs231]]. Пред. (09-12 #1): ИН-ГЕЙМ A/B 1.1.5 vs 1.1.6 на BBS 2.5.2 — A≈B (frame p50 47.3 мс ≈ 21 FPS), корень = AUDITED заужен на 2.3.1 (sp.reuse=0, caster.unknown до 2222) [[project-caster-revision-gated-bbs231]]; окно игры под computer-use недоступно (юзер держал сцену руками); профайлер пишет окна в latest.log; инстанс в тест-состоянии (моды=1.1.6, shaders on, vsync off), откат в `<scratchpad 2282deae>/mods-backup-orig`. Пред. (09-11 #3): VL-СКОУП — наложен обновлённый пакет (4 репо): микро-оптимизации irlite_lights.glsl (gobo/point-hoist, zero-knobs; OCCLUDED_SKIP отклонён), GPU-harness ~8-15% дешевле point/spot. МЕНЯЛИСЬ shader+патч+пак(64fe36)+редактор+DOF; core нет. Собран irlite-1.1.6; ИГРОВОЙ A/B (deferred2, intensity>0) ОТКРЫТ = текущий тест [[project-vl-followup-2026-09-11]]. Пред. (09-11 #2): ПРОДОЛЖЕНИЕ ОПТИМИЗАЦИЙ — наложен пакет `Desktop\IRLights-changes-1.1.6-2026-09-11` поверх 1а (addon→1.1.6): compact cluster-upload −95%, host-cell фикс BlockShadowCache, VBO-классификация ShadowRenderer, scratch LightCollector + P2; собран irlite-1.1.6+mc1.20.4.jar (бандл честный), CPU-harness PASS. ИГРОВОЙ A/B/FPS+визуал НЕ проверены = текущий скоуп [[project-perf-followup-2026-09-11]]. Пред. (09-11 #1): ПЕРФ-РЕДИЗАЙН ЭТАП 1а сделан на ДРУГОЙ машине (ZoGa), НЕзакоммичен — снимок правды `C:\Users\qualet\Desktop\IRLights`. Reuse целого неизменившегося shadow-overlay: core 1.1.5→1.1.6, CasterRevision/ShadowOverlayCache/ShadowCasterSource.revision, ShadowBaker пропускает copy/draw/pyramid/moments; BBS-адаптер 2.3.1 (cubic + villager MobForm CPU-eval). 59 проверок + CSV-регрессия PASS; in-game 600 кадров sp.reuse=26/кадр. A/B ДОКАЗАН ин-гейм 09-11 (villager-сцена): GPU bake 7.889→0.006 мс, CPU frame 16.41→9.09 мс (−44.6%, ≈61→110 FPS); этапы 1б–4 открыты. ПЕРЕНЕСЕНО на этот ПК 09-11 (побайтовая копия из снимка, core 1.1.6 в mavenLocal, аддон собран на JDK 21 → irlite-1.1.5+mc1.20.4 с вложенным core 1.1.6); локально НЕзакоммичено, GitHub не трогали [[project-performance-redesign-stage1a]]. Пред. (09-10): РЕСИНК рабочей копии с источника правды `Desktop\IRLIghts_new` (08-30): 5 репо --ff-only (addon +97), склад 109→153, core 1.1.5 в mavenLocal [[project-workstation-resync-2026-09-10]]. Пред. (08-11): ЭКСПЕРИМЕНТ contact-размытие теней (dev photon_v1.3b_IRLights ONLY, патчеры не тронуты): новый оценщик полутени mode-тогблом IRLITE_SHADOW_PENUMBRA_MODE (1=contact default: backprojection-валидность + вес 1/p² + центр-тап + стратификация + raw-фолбэк внешней кромки, point на zPersp; 0=classic для A/B) + слайдер IRLITE_CONTACT_BLOCKER_TAPS; ревью-wf 8 confirmed закрыты + verify-wf 3/3 (mode 0 = classic parity); ИН-ГЕЙМ PENDING; вахта: крутые склоны у лампы (обрыв каёмки), зерно спот-бленд-зоны [[project-contact-penumbra-experiment]]. Пред. (08-10a/b/c — 16px-откат, тень большой формы, флешлайт 5 jar; 07-25) — в _session-log.

## Открытые хвосты
- DOF-аудит 08-10: всё актуально (master=origin=e27a808, комбо 7/7 = регенерация из main, гейт зелёный); `build-bbs-pack.ps1` переведён на `-Pmc=universal` для DOF (был default 1.20.4 с точным пином → пак 1.20.x не встал бы на 1.20.1), тег v1.0.0 подтянут локально. Открыто: jar в инстансе BBS = предрелизный `dev-1` (пользователь решил не трогать) [[project-dof-combo-sync]].
- ✅ DOF ЗАКРЫТ + ОТРЕЛИЖЕН (07-26): ин-гейм PASS, universal-jar доказан побайтово, 4 цели -Pmc, PRIVATE репо quaIett/bbs-dof-addon, релиз v1.0.0 с 4 jar — первый релиз в экосистеме [[project-dof-1211-port]].
- ✅ ТИРАЖ РЕДАКТОРА ЗАКРЫТ (S1+S2+S3) [[plan-editor-tiraz-sessions]]; разблокирован Заход 2 редизайна [[project-editor-redesign]] + DoF (Phase B шаг 3, отд. сессия, remote не заведён).
- UBO-миграция: переснять профайлер волны 1 (замер загрязнён); волны 2/3 (surface INTENSITY/SPECULAR/TOON; SHADOW_*) не начаты.
- cherry-pick 3a1ad5e (иконка light-формы) в master.
- LOD-тиры: I5 визуально НЕ закрыт (ретест 64-spot после caster fix); тираж на 6 паков не делался.
- Тираж на порт-ветки (block-rebake гейт, partial-tile, W2): все порт-ветки обоих репо ещё на core 1.1.
- Состояние (07-23): core main=6cebbc5 (cookie-mirror, НЕ запушен); addon master=1a23b6b + port/1.21.1=5e73427 (cookie render-path фикс, НЕ запушено); CR-синхра трилогия 7/7 влита ранее.

## Маршрутизация и стратегия (читать первой)
- [project-java-optimization-2026-09-11-iter1](project-java-optimization-2026-09-11-iter1.md) — Последнее09-11: Java-итерация1,6/7кандидатов реализованы,1.1.6; сборки/harness/review PASS, отдельный JAR6B154C1B+перенос; игровой A/B OPEN, runtime VL не заменён.
- [plan-port-perlight-v2](plan-port-perlight-v2.md) — СТАРТ СЛЕДУЮЩЕЙ СЕССИИ: порт пакета Desktop\IRLights-PerLight-VL-Outline-v2 (per-light VL/Outline + «только выбранные реплеи») с BBS 2.3.1/база 09-15 на master (BBS 2.6 + wave 2). Все факты, шаги, готчи.
- [project-per-light-profiles](project-per-light-profiles.md) — 09-15/16: моя struct-реализация профилей (160 Б) СПИСАНА в пользу v2-порта; из неё оставлено оформление треков (TrackCatalogMixin+LightTrackLayout, в рабочем дереве, не закоммичено) + знания о BBS 2.6 API.
- [plan-per-light-profiles-and-colored-shadows](plan-per-light-profiles-and-colored-shadows.md) — перф-прикидка 09-10: цветные тени/витраж (НЕ начата) + per-light профили (закрывается v2-портом).
- [project-performance-redesign-stage1a](project-performance-redesign-stage1a.md) — АКТИВНЫЙ перф-редизайн: этап 1а (reuse неизменившегося shadow-overlay, core→1.1.6) перенесён на этот ПК + A/B ДОКАЗАН (bake 7.9→0 мс).
- [project-perf-followup-2026-09-11](project-perf-followup-2026-09-11.md) — АКТИВНЫЙ продолжение оптимизаций поверх 1а (пакет `Desktop\IRLights-changes-1.1.6-2026-09-11`, наложен на этот ПК 09-11, addon→1.1.6): compact cluster-upload −95%, host-cell фикс BlockShadowCache, VBO-классификация ShadowRenderer, scratch LightCollector + P2. CPU-harness PASS; ин-гейм визуал ПРОВЕРЕН 09-11 (регрессий нет, лог чистый). OPEN: количественный FPS-A/B vs 1.1.5-билда.
- [project-vl-followup-2026-09-11](project-vl-followup-2026-09-11.md) — АКТИВНЫЙ VL-скоуп (наложен 09-11, пилот Complementary r5.8.1): микро-оптимизации irlite_lights.glsl (gobo/point-hoist, zero-knobs gates), OCCLUDED_SKIP отклонён; GPU-harness ~8-15% дешевле point/spot (float parity 5.96e-8). МЕНЯЛИСЬ shader+патч (49b8b7)+пак(64fe36)+редактор+DOF-комбо; core не менялся. Собран irlite-1.1.6 (4B4101). ИГРОВОЙ A/B (deferred2) ОТКРЫТ.
- [project-caster-revision-gated-bbs231](project-caster-revision-gated-bbs231.md) — DONE 09-12: порт адаптера ревизий на BBS 2.5.2 (BbsSilhouetteBridge, самотесты PASS, reuse доказан dev-клиентом bake 21→0.01 мс, jar в инстансе Prism); ОТКРЫТ пользовательский A/B на Prism; трассировка причин UNKNOWN `-Dirlite.debugCasterRevisions=true`; отчёт docs/bbs-2.5.2-caster-revision-port-spec.md.
- [plan-post-trilogy-port-rollout](plan-post-trilogy-port-rollout.md) — АКТИВНЫЙ план тиража: фаза A мёрж core+addon → фаза B аддоны→редактор→DoF; топология веток, блокеры.
- [plan-editor-tiraz-sessions](plan-editor-tiraz-sessions.md) — АКТИВНЫЙ (Фаза B шаг 2): тираж РЕДАКТОРА main→4 порт-ветки, 3 сессии; core-staleness матрица, канон запушен 07-23.
- [reference-core-versioning](reference-core-versioning.md) — версии irl-core (1.1.3); ось либы != ось продукта; бамп = 5 мест; готча nested-jar.
- [reference-edit-routing-by-area](reference-edit-routing-by-area.md) — что-где менять (патчер+свет+тени=core; caster/UI per-mod); команды сборки.
- [project-workstation-resync-2026-09-10](project-workstation-resync-2026-09-10.md) — ИНФРА: источник правды = `C:\Users\qualet\Desktop\IRLIghts_new` (снимок 08-30); что подтянуто локально 09-10, где бэкап-рефы, новые пути JDK, origin отстаёт (core +3 / addon +6 / editor +2).
- [project-github-repos](project-github-repos.md) — 4 репо под owner quaIett (заглавная I): трилогия public + bbs-dof-addon private; origin/ветки, gh CLI; запрет на патч IterationRP СНЯТ (07-26).
- [project-vfxlights-copy-analysis](project-vfxlights-copy-analysis.md) — анализ VFX-LIGHTS (Xavin): архитектура+якоря взяты из IRLite, 3 прямых цитаты в их файлах; вердикт внутри.
- [project-dof-combo-sync](project-dof-combo-sync.md) — комбо IRL+DOF = тело main-патча + DOF-хвост; регенерация tools/gen-combo.ps1, гейт verify-combos.ps1; готчи Solas V3.7.
- [project-dof-1211-port](project-dof-1211-port.md) — DOF на 1.21.1+BBS 2.4 матрицей -Pmc на master (порт-веток НЕТ); готчи Iris-депа и пина манифеста.
- [project-flashlight-addon](project-flashlight-addon.md) — 4-й мод: аддон-фонарик (item→спот из глаз по взгляду) на irl-core; MC 1.20.1–1.21.1 + 1.21.11 (per-era src/ + universal-jar, 08-10); свой mixin renderWorld HEAD priority 900 → тот же bake+flush редактора.
- [project-irl-sync-strategy](project-irl-sync-strategy.md) — карта дрейфа аддон<->редактор; универс-jar отменён -> per-MC.
- [plan-irl-core-library-extraction](plan-irl-core-library-extraction.md) — Tier1+2 выносы DONE; core-API список; Tier3 не делалось.
- [tool-build-trilogy-script](tool-build-trilogy-script.md) — build-trilogy.ps1: трилогия на все MC; per-MC core = publishToMavenLocal.
- [tool-build-bbs-pack-script](tool-build-bbs-pack-script.md) — build-bbs-pack.ps1: core+4 аддона 1.20.x.

## IRL-redactor

### Тени (оркестрация физически в irl-core)
- [plan-irl-core-shadow-extraction](plan-irl-core-shadow-extraction.md) — КАНОН теней: оркестрация в core + шов ShadowCasterSource + 5 инвариантов; Ф4 тираж open.
- [project-shadow-bake-perf-audit](project-shadow-bake-perf-audit.md) — живой док перфа бейка; Tier-1/2 done; открыт C10.
- [plan-shadow-bake-track](plan-shadow-bake-track.md) — ПЛАН бейка: профайлер → C10 → per-face фильтры → BBS-probe; вердикты «не переоткрывать».
- [addon-shadows](addon-shadows.md) — референс бейк-движка (ShadowBaker/Renderer, пресеты, кэш); caster cap = nearest-128.
- [shadow-distance-quality-plan](shadow-distance-quality-plan.md) — качество на дали (Ф1-2 done, Ф3 open).
- [plan-shadow-lod-tiers](plan-shadow-lod-tiers.md) — LOD-тиры I1-I4 + caster fix закоммичены; тираж по команде.
- [plan-point-shadow-atlas-merge](plan-point-shadow-atlas-merge.md) — PointDepthAtlas 30 ламп; DONE; имплем-план = референс тиража.
- [plan-shadow-filtering-refactor](plan-shadow-filtering-refactor.md) — point-фильтрация ЗАВЕРШЕНА (MSM4+cube-view); open: overlay-перф, спот на MSM.
- [project-point-shadow-fix-backlog](project-point-shadow-fix-backlog.md) — бэклог А-Д (А done, Б-Д нет) + НЕ ТРОГАТЬ.

### Порты / редактор / движок / интеграции
- [project-irlite-base-ported](project-irlite-base-ported.md) — КАНОН движка+редактора: BBS-free свет (LightScene/PlacedLight/LightDriver).
- [project-editor-vs-replay-screen-conflict](project-editor-vs-replay-screen-conflict.md) — редактор в Replay Mod (PASS); Фаза 3 курсор open.
- [project-editor-guide-overlay](project-editor-guide-overlay.md) — гайды света + драг спота как ImGui-оверлей; a7859ed PASS; хвост: закрытый редактор + point radius.
- [project-editor-free-camera](project-editor-free-camera.md) — свободная камера в редакторе; default-on, F toggle, hold-ЛКМ обзор; 9120755 PASS; порт-ветки open.
- [project-editor-slider-value-input](project-editor-slider-value-input.md) — средний клик по слайдеру = инлайн точный ввод; 3b69a0d PASS; порт-ветки open.
- [project-flashback-irlights-plan](project-flashback-irlights-plan.md) — PLAN-only: аддон под Flashback; kill-switch = SSBO b7.
- [project-imgui-axiom-collision](project-imgui-axiom-collision.md) — краш ImGui рядом с Axiom; try/catch+fallback.
- [project-auto-block-lights](project-auto-block-lights.md) — авто-свет от эмиссивных блоков, OFF; MAX_LIGHTS=2048; + фикс нумерации источников (b8068b1).
- [project-spotlight-gobo-cookie-plan](project-spotlight-gobo-cookie-plan.md) — gobo/cookie done; LRU done; per-pack recheck open.

### Forge / Sinytra Connector
- [project-forge-connector-compat](project-forge-connector-compat.md) — аддон на Forge 1.20.1 через Connector beta.48 (fmj fabricloader >=0.15.0).

### Референсы / правила работы
- [reference-debug-ui-flags](reference-debug-ui-flags.md) — весь дебаг OFF; возврат -Dirlite.debug / -Dirlredactor.debug; holdBakeOnJoin true->false.
- [reference-bbs-fs-not-refreshed](reference-bbs-fs-not-refreshed.md) — референс BBS-кода = bbs-fs, не форк refreshed.
- [reference-macos-out-of-scope](reference-macos-out-of-scope.md) — macOS НЕ поддерживаем; MC_OS_MAC-гарды игнорировать.
- [feedback-no-per-session-branch](feedback-no-per-session-branch.md) — НЕ создавать ветку под сессию.
- [feedback-memory-strict-style](feedback-memory-strict-style.md) — память в строгом LLM-стиле, ноль декора.
- [feedback-visual-test-image-prompts](feedback-visual-test-image-prompts.md) — визуальные проверки = image-gen промпт (EN, EXPECTED/REGRESSION).
- [reference-imgui-font-glyph-range](reference-imgui-font-glyph-range.md) — шрифт = Latin-1+кириллица; спецсимволы = тофу.
- [iris-source-library](iris-source-library.md) — исходники Iris: PRIMARY 1.20.1 + fallback 1.7.2-1.20.4.

## IRLite — ядро BBS-аддона
- [reference-shadow-cap-vs-resolution](reference-shadow-cap-vs-resolution.md) — разрешение теней менять дёшево (textureSize), кап — нет (frozen GLSL ABI, потолок 64 из long-масок); пресет 16px пробовали и откатили.
- [project-shadow-clip-scaled-forms](project-shadow-clip-scaled-forms.md) — тень большой формы: ОБА виновника закрыты (тогбл shadow_partial_tile + честная сфера foldFormChain с мостом BBS 2.3/2.4); ИН-ГЕЙМ PASS; закоммичено (не запушено); открыт geometry-AABB для authored-large bbmodel.
- [project-shadow-softness-perf](project-shadow-softness-perf.md) — GPU-цена слайдера Softness (09-12): spot MED x2.9 при 0.8, дороже на высоком разрешении теней, кривая немонотонна (пик в бленд-зоне PCF+EVSM); бейк от Softness не зависит.
- [project-contact-penumbra-experiment](project-contact-penumbra-experiment.md) — эксперимент contact-оценщика полутени (dev Photon only, mode-тогбл 0/1, classic сохранён); ин-гейм PENDING, тираж по команде.
- [project-entity-reveal-prototype](project-entity-reveal-prototype.md) — reveal-in-light прототип (CR 1.20.4): сущность видна только в конусе спота, дизер-край + 2 слайдера; ветка feature/entity-reveal-spotlight-cr, НЕ закоммичено.
- [addon-architecture](addon-architecture.md) — всё через миксины; per-frame collect->bake->flush(SSBO7) до Iris.
- [addon-forms](addon-forms.md) — PointLightForm/SpotlightForm на BBS Form; маски->lightMask.
- [project-light-form-item-icon](project-light-form-item-icon.md) — иконка light-формы в инвентаре: DONE 3a1ad5e; хвост cherry-pick в master.
- [addon-light-collection](addon-light-collection.md) — SCANNER vs RENDER, дедуп; MAX_LIGHTS=2048.
- [fix-modelblock-light-animation-states](fix-modelblock-light-animation-states.md) — ModelBlock-свет не ехал за animation states; фикс walk() (PASS).
- [fix-render-path-light-world-pos-1211](fix-render-path-light-world-pos-1211.md) — render-path свет на 1.21 гулял; фикс = context.world (port/1.21.1, PASS). Готча: mavenLocal core 1.1.4 per-MC (1.20 class_4587 vs 1.21 Matrix4fStack).
- [fix-cookie-render-path-spotlights](fix-cookie-render-path-spotlights.md) — gobo не работал на render-path спотах (BodyPart-кость/актёры/реплеи): render-path звал no-cookie registerSpot; фикс SpotlightFormRenderer (master 1.20.4 PASS + port/1.21.1); ручная сборка аддона рецепт.
- [addon-ui-config](addon-ui-config.md) — IrliteConfig, BBSSettings-категории, L10nMixin, гайды.
- [project-refactor-origin](project-refactor-origin.md) — IRLite = рефактор IRLEngine (uniform->SSBO7 + патчер).
- [commit-checkpoints](commit-checkpoints.md) — (feedback) коммиты только в чекпоинты по подтверждению; gitignore shaders/ -> git add -f.
- [feedback-addon-runclient-command](feedback-addon-runclient-command.md) — (feedback) рантайм = runClient -Pmc=1.20.4, Git Bash, лог в фоне.
- [fix-bbs24-uitrackpad-limit](fix-bbs24-uitrackpad-limit.md) — BBS 2.4-1.20.1 сдвинул limit() в generic UINumericInput -> NoSuchMethodError; рефлексивный limit + ASM-чекер дрейфа.

## irl-core — общее ядро
- [patcher](patcher.md) — DSL .irlights (@target/@packversion/@marker, after/before/replace); validate-first. CONTRACT_VERSION=1.
- [addon-light-buffer-ssbo](addon-light-buffer-ssbo.md) — std430 LightBuffer: binding7, header 16B + 6×vec4/96б; MAX_LIGHTS=2048; байт-в-байт. UBO IrliteVlGlobals. (v2-порт дописывает профили ХВОСТОМ, записи не трогает.)

## Шейдер-инжект — общие контракты
- [plan-lens-flare](plan-lens-flare.md) — PLAN-only lens flare; open: SSBO-слот.
- [shader-irlite-glsl](shader-irlite-glsl.md) — контракт irlite_lights.glsl: struct 6×vec4, #define-опции, per-light математика.
- [shader-shadow-sampling](shader-shadow-sampling.md) — GLSL-чтение теней; гард: vlParams.w<0 ДО int().
- [shader-volumetric](shader-volumetric.md) — волюметрика Beer-Lambert/HG; VL-noise done на CR, порт в 5 паков open.
- [plan-vl-refactor-research](plan-vl-refactor-research.md) — VL-рефактор до 3b DONE; тираж на 6 паков open; статусы/готчи внутри.
- [plan-vl-profiler](plan-vl-profiler.md) — профайлер DONE (-Dirlite.profileVl=true); Hi-Z закрыт (ALU-bound).
- [plan-vl-3c-bilateral](plan-vl-3c-bilateral.md) — 3c DONE; боевой пак = run/shaderpacks (полурез 0.5 + bilateral); контракт внутри.
- [shader-settings](shader-settings.md) — настройки в Iris UI; гоча: boolean #define только при голом #ifdef.
- [addon-iris-integration](addon-iris-integration.md) — (ref) 2 миксина биндят тени (ProgramSamplersBuilder + SamplerBindingCubeArray).
- [ref-irlengine-photon-patch](ref-irlengine-photon-patch.md) — (ref) старый IRLEngine->Photon как образец; adapt uniform->SSBO.
- [sync-workflow](sync-workflow.md) — dev-цикл шейдеров (Original/Modification/patches/run; Shadres gitignored); комменты <=1 строка.

## Шейдер-паки — пайплайны (контракт + якоря + статус)
- [project-photon-outline-switch-to-old](project-photon-outline-switch-to-old.md) — КАНОН outline (Fresnel rim, default OFF); Photon = 2 патча.
- [outline-target-entity-detection](outline-target-entity-detection.md) — IRLITE_OUTLINE_TARGET; done 5 паков; гоча PatchLibrary.extracted open.
- [photon-pipeline](photon-pipeline.md) — Photon deferred, 4 хука; порт done (20 ops). Спутник [photon-bugfix](photon-bugfix.md).
- [photon-bugfix](photon-bugfix.md) — трекер Photon; WATCH bob-flicker acne.
- [shader-iterationrp-pipeline](shader-iterationrp-pipeline.md) — IterationRP #430 native SSBO, 3 хука; done; VL unshadowed.
- [complementary-pipeline](complementary-pipeline.md) — Complementary forward #130; done (21 ops); VL half-res deferred2.
- [rethinkingvoxels-pipeline](rethinkingvoxels-pipeline.md) — RethinkingVoxels (CR-форк); done; дельты: composite.glsl, VL=colortex15.
- [bsl-pipeline](bsl-pipeline.md) — BSL v10 #120 CRLF; done (29 ops).
- [solas-pipeline](solas-pipeline.md) — Solas #130; done (19 ops, ru_RU); irislex.
- [bliss-pipeline](bliss-pipeline.md) — Bliss #120 dual-hook; CR-синхра DONE; «BSL-lib+4 дельты», TAAU cluster-fix; MVI[3] exactly-once.

## Закрытые темы
14 завершённых тем в `_index-closed.md` (не грузится); тема активна снова -> вернуть строку.

## Пользователь
- Пользователя зовут Qualet.
