---
name: plan-post-trilogy-port-rollout
description: "Согласованный план (2026-07-22) — мёрж optimization/octahedral-point-shadows в master + тираж на все версии; порядок фаз, топология веток по репо"
metadata:
  node_type: memory
  type: project
  originSessionId: 948352e7-4426-4c2f-8cf0-8247db0dfab1
  modified: 2026-07-22T12:46:23.472Z
---

Согласованный с юзером 2026-07-22 план закрытия хвоста «мёрж на main + порт на все версии». Скоуп версий = 4 (1.20.1, 1.21.1, 1.21.4, 1.21.11) подтверждён.

**Why:** финальная работа сессии (CR-синхра трилогии 7/7 + W2 + bake-track + UI-рефактор) сидит на локальной ветке `optimization/octahedral-point-shadows`, не влита в канон и не тиражирована. Порт = тираж С master, поэтому порядок жёсткий: сначала канон, потом тираж.

**How to apply:**

## Фаза A — ✅ DONE + PUSHED 2026-07-22 (двойной ff в канон)
Выполнено ступенчато (ff+publish локально → push по явному «да» юзера):
1. `irl-core`: `main` ← ff `optimization/octahedral-point-shadows` (+24). Итог **main = ef76cdb**; `publishToMavenLocal` (1.1.3 в mavenLocal, JDK21, BUILD SUCCESSFUL). Push `d5e40ad..ef76cdb` → origin/main == main, 0 unpushed.
2. `bbs-irlights-addon`: `master` ← ff `optimization/octahedral-point-shadows` (+61). Итог **master = b8a8fdc** (пинит irl-core:1.1.3 impl+include). Push `34bb996..b8a8fdc` → origin/master == master, 0 unpushed.
- Оба ff чистые (mb==tip канона), деревья были чисты, рантайм PASS 07-21. Локальные ветки optimization целы (=каноническим tip) → откат тривиален пока не тронуты. bump = 5 мест, см. [[reference-core-versioning]].

## Фаза B — тираж (порядок репо задан юзером)
1. **Аддоны** (core прошит сюда как фундамент) → 2. **Редактор** (irlights) → 3. **DoF** (bbs-dof-addon), последним и ПО ЖЕЛАНИЮ.
- **СКОУП СЕССИИ 2026-07-22 (директива юзера): ТОЛЬКО шаг 1 (аддон). Редактор (шаг 2) + DoF (шаг 3) — ОТДЕЛЬНЫЕ сессии, строго по команде.** Аддон-тираж = ЗАВЕРШЁН: master (канон, фаза A) + port/1.21.1 (эта сессия, рантайм PASS); других аддон-веток нет (BBS макс 1.21.1). Core-ветки 1.21.4/1.21.11 подтягивать при тираже РЕДАКТОРА, не аддона.
- Внутри шага 1 порядок версий: эталон `1.21.1` (разошёлся 27/20) → отладить процесс → `1.21.4`/`1.21.11` → `1.20.1` (особая: деп-матрица+LWJGL, см. [[project-port-1201]]).
- Дельта master→порты: core 1.1→1.1.3 (W2+block-rebake), bake-track (partial-tile/half-res EVSM/pose-reach), VL 3b/3c, UI-рефактор настроек (BBS-модуль/пресеты/outline→UBO), CR-синхра трилогии (6 паков).

## Топология веток (выяснено 2026-07-22, remote у всех = origin под quaIett)
- `irl-core`: ветки `1.21.1`, `1.21.4`, `1.21.11`, `main` (=1.20.x) + активная `optimization/octahedral-point-shadows`. Per-version ветки для всех трёх 1.21.x УЖЕ есть.
- `bbs-irlights-addon`: ТОЛЬКО `port/1.21.1` (+ master, debug/cluster-heatmap, optimization). Веток 1.21.4/1.21.11/1.20.1 НЕТ.
- `irlights` (редактор): полный набор `port/1.20.1`, `port/1.21.1`, `port/1.21.4`, `port/1.21.11` + main. Фича-ветки optimization НЕТ → чистый тираж.
- `bbs-dof-addon`: `master` + `port/1.21.1`. Фича-ветки НЕТ → чистый re-sync (7 DoF-комбо, инжект в 2 патчах).

## Блокеры / открытые для B — РАЗВЕДАНЫ 2026-07-22
- **Асимметрия аддон↔core — СНЯТА** (чтение build-line.ps1). Гипотеза верна: аддон version-agnostic. build-line собирает «линию» = {core-worktree нужной ветки → publish в изолир. mavenLocal → consumer build с `-Pmc=<Pmc>` при необходимости}. Матрица: 1.20.4/1.20.1 = core `main` → аддон `master` -Pmc=X; 1.21.1 = core `1.21.1` → аддон `port/1.21.1`; 1.21.4/1.21.11 = аддона НЕТ (BBS макс 1.21.1, только редактор). ⇒ отдельные ветки аддона 1.21.4/1.21.11/1.20.1 НЕ заводить. Тираж «аддоны» = ОДНА ветка `port/1.21.1` (1.20.x уже в каноне через master).
- **copy-patches.ps1 — ЕСТЬ** (`irlights/tools/copy-patches.ps1`, не в BBS/). Односторонний byte-синк .irlights: `bbs-irlights-addon/patches/*` → редактор bundled resources + MD5-верификация. Покрывает addon→editor В ПРЕДЕЛАХ одной версии. НЕ решает cross-version тираж master→port (адаптация per-version якорей) — этот перенос через cherry-pick/merge коммитов патчей или руками; способ решить в шаге 1/2 B.
- **bbs-dof-addon без origin-веток** (git branch -r пусто на 07-22) — на шаге 3 (DoF, опц.) уточнить/завести remote до любого push.

## Топология веток — сверено 2026-07-22 (факт git)
- `irl-core`: локальные 1.21.1(832e5f1) / 1.21.4(7eebbe6) / 1.21.11(9d80fd7) / main(ef76cdb=origin) / optimization(ef76cdb); origin: 1.21.1,1.21.4,1.21.11,main.
- `bbs-irlights-addon`: master(b8a8fdc=origin) / optimization(b8a8fdc) / port/1.21.1(4de6d74=origin) / debug/cluster-heatmap(25a10ab); origin: master,port/1.21.1.
- `irlights`: main(cbe3291) / port/1.20.1(f59ed3f) / port/1.21.1(1dbf2e5) / port/1.21.4(43539ec) / port/1.21.11(46c7b4d) — все =origin. Фича-ветки нет → чистый тираж.
- `bbs-dof-addon`: master(4968042) / port/1.21.1(b49c176); origin-веток НЕ видно.

## Тираж порт/1.21.1 — план переноса (understand-wf 5 изм., 2026-07-22; НА СОГЛАСОВАНИИ, правок НЕ делалось)
Жёсткий порядок (зависимости компиляции): **CORE → addon Java → addon .irlights**. addon-порт не собрать, пока core-ветку 1.21.1 не подтянуть.

### 1. CORE (irl-core main ef76cdb → ветка 1.21.1 832e5f1) — ФУНДАМЕНТ
**✅ DONE 2026-07-22 (НЕ запушено): merge-коммит 127cd07 на ветке 1.21.1; build зелёный (compileJava под yarn 1.21.1, 0 mapping-ошибок за 26s); publishToMavenLocal OK (irl-core:1.1.3 = линия 1.21.1 СЕЙЧАС в mavenLocal — перезаписал main-линию; republish main перед сборкой 1.20.x!). Резолв факт: конфликтов 4 (не 6): FramePipeline/LightRegistry/ShadowBaker = `git checkout --theirs` (0 render-hits, main надмножество); ShadowRenderer = ручной sed (2 atlas-блока → main, шимы Matrix4fStack/MatrixStack scratch сохранены из auto-merge, 43 render-hits); build.gradle/gradle.properties auto-merged ВЕРНО (порт-toolchain Loom1.15/Java21/Iris1.8.8 цел, ver→1.1.3, БЕЗ маркеров). PointShadowArray удалён (atlas). РАНТАЙМ НЕ проверен — нужен полный стек addon-порта.**
- Метод = ОДИН `git merge main` в ветку 1.21.1 (история линейна от форка cf4ad94; 27 коммитов 878eb27..ef76cdb; НЕ cherry-pick — накопит 6 конфликтов многократно).
- Тени near-lockstep ФАКТ-подтверждено: main И 1.21.1 = raw-GL (BufferBuilder/Tessellator/GL30), форк ДО рерайта рендера 1.21.5 → hunk-by-hunk.
- Конфликты ровно в 6 файлах: build.gradle→оставить 1.21.1-сторону (Loom1.15/Java21/Iris1.8.8, skip main); gradle.properties→взять 1.1.3; FramePipeline/LightBuffer/LightRegistry/ShadowBaker→взять main (надмножество, camera-relative идентична); **ShadowRenderer.java→взять main-фичедельту + РУЧНОЙ render-API шов** (RenderSystem.getModelViewStack→org.joml.Matrix4fStack; Tessellator.getInstance().begin(...) вместо getBuffer()+begin()) — единственный high-risk шов ядра.
- PointShadowArray.java удаляется (atlas-миграция) — принять. Новые файлы (VL-стек BlueNoiseTexture/ClusterGridBuffer/VlGlobalsBuffer, atlas DepthTileAtlas/PointDepthAtlas, ShadowVramBudget/Rect/AllocLog/BakeProbe, blue_noise_128.raw, gen-blue-noise.py) — чисто.
- ГЕЙТ: compile-pass `gradlew build -Pmc=1.21.1` (yarn 1.20.4→1.21.1 renames в MC-типизир. BlockShadowCollector/BlockShadowCache) → publishToMavenLocal. Только зелёный core → addon.

### 2. addon Java (master b8a8fdc → port/1.21.1 4de6d74)
**✅ COMPILE+JAR ЗЕЛЁНО 2026-07-22 (НЕ закоммичено): build -Pmc=1.21.1 SUCCESSFUL, jar irlite-1.1+mc1.21.1.jar, JiJ = РОВНО 1 irl-core-1.1.3 (nested-jar гигиена работает). Метод факт: copy-as-is через `git checkout master --` для version-agnostic (diff port→master = чистое надмножество, core готов): IrliteConfig/IrlightsAddon/IrlitePresets, VlProfiler/VlSweep/UIDebugSection/UIPresetSection, L10nMixin/UISettingsOverlayPanelMixin/WorldBlockChangeMixin, IrliteShadowConfig/LightCollector/IrliteClient, CapturedRenderingStateClusterMixin/CompositeRendererTimerMixin. MANUAL (per-version сохранён): GameRendererLightMixin (VL-грефт в RenderTickCounter-сигнатуру порта, НЕ master float+MatrixStack); IRLiteBbsCasterSource (checkout master + ВЕРНУЛ rotate2-хеш — BBS-1.21.1 реинстатил rotate2); AbstractLightFormRenderer.renderItemIcon (checkout master + адаптация Tessellator.begin/убрать .next() под 1.21.1 по эталону LightGuideRenderer). git rm BBSSettingsMixin + снят из irlite.mixins.json. json: fabric.mod.json +bbs-addon entrypoint (depends НЕ трогал); client-mixins +2 iris. build.gradle: coord 1.1→1.1.3 + nested-jar блок. ЕДИНСТВЕННЫЙ compile-фейл = CapturedRenderingStateClusterMixin: Iris 1.8.8 getGbufferModelView() возвращает Matrix4fc (не Matrix4f как master/Iris1.7.2) → фикс `new Matrix4f(...)` + import. РАНТАЙМ НЕ проверен.**
- copy-as-is (BBS/Java-агностик, все зависят от core из шага 1): main-java лот IrliteConfig+IrlightsAddon+IrlitePresets; client-new VlProfiler(638)+VlSweep(311)+UIDebugSection+UIPresetSection; mixin L10nMixin+UISettingsOverlayPanelMixin.
- manual (per-version дивергенция рядом с дельтой — прививать ТОЛЬКО дельту): LightCollector (push-блок VL-globals + MAX_DIST→public); IrliteShadowConfig (+shadowPoseReach/shadowsEnabled); IrliteClient (HudRenderCallback+installBakeProbe, лямбда 1.21.1=(DrawContext,RenderTickCounter)); IRLiteBbsCasterSource (COLLECT_DIST=256+hasShadowGeometry+poseReach-slack, СОХРАНИТЬ rotate2-хеш + light-relative anchor); AbstractLightFormRenderer.renderItemIcon (high-risk: адаптация Tessellator/vertex под 1.21 по эталону port-LightGuideRenderer); GameRendererLightMixin (грефт VlProfiler, СОХРАНИТЬ RenderTickCounter-сигнатуру); WorldBlockChangeMixin (тело invalidateChange, target идентичен).
- delete: mixin/BBSSettingsMixin.java + снять строку из irlite.mixins.json.
- json точечно: fabric.mod.json +entrypoint "bbs-addon":[IrlightsAddon] (НЕ трогать depends); client-mixins +2 iris-записи СИНХРОННО с портом классов.
- build.gradle: бамп core 1.1→1.1.3 (ТОЛЬКО после шага 1 publish) + nested-jar блок f7410b0 (outputs.upToDateWhen{false}+doFirst purge) — обязателен с бампом.
- Iris-миксины новые: CapturedRenderingStateClusterMixin (после core cluster: FramePipeline.onGbufferMatricesCaptured); CompositeRendererTimerMixin (high-risk: createProgram desc проверить под Iris 1.8.8, remap=false). Когорта VL-профайлера = dev-only (-Dirlite.profileVl) → можно ОТЛОЖИТЬ/пропустить.
- skip (do_not_touch, per-version/per-BBS): панели форм (UISection=BBS2.3.1), SpotGuideDrag (isActionsMode), IrliteBbsCompat/IrliteFormSections, SpotlightFormRenderer/IRLightPositionResolver/LightGuideRenderer (camera.getRotation adapt — эталон), ProgramSamplersBuilderMixin+SamplerBindingCubeArrayMixin.

### 3. addon .irlights (последними)
**✅ DONE 2026-07-22 (НЕ закоммичено): `git checkout master -- patches/ ':(glob)tools/gen-*-patch.ps1'` — 7 патчей обновлены, 5 gen модиф + 2 новых (gen-photon/gen-iterationrp), release.ps1 НЕ тронут. @packversion совпали (Bliss V2.1.2/BSL v10/Solas V3.7). Патчи не в jar (source-of-truth, рантайм) → компиляции не касаются.**
- Метод = copy-as-is байтов master: `git checkout master -- patches/ tools/gen-*.ps1`. НЕ cherry-pick (6 CR-sync коммитов на промежуточной эволюции 18 коммитов, трогают src/).
- Все 7 патчей ФАКТ version-agnostic (идентич @target/@packversion, 0 MC-токенов). Но контрактно зависят от Java (UBO-push, BBS-настройки) + core (IrliteVlGlobals std140 UBO binding7, IRLITE_CLUSTER binding6 wide W2, PointDepthAtlas). Лить ТОЛЬКО после core+Java, иначе unbound UBO / отсутств. atlas-семплеры (photon irl_pointEvsm) / мусор cluster.
- gen-photon-patch.ps1 + gen-iterationrp-patch.ps1 = новые файлы на порте (add). release.ps1 = skip (version-sensitive -Mc default 1.20.1). copy-patches.ps1 (irlights/tools/) неприменим master→port (он addon→editor в пределах версии) — пригодится на ШАГЕ 2 (редактор).

### Рантайм-валидация + jar (2026-07-22)
- Локальный `runClient -Pmc=1.21.1` УПАЛ мгновенно (2s) на FabricLoader dependency-resolution — **НЕ баг тиража** (loader не дошёл до загрузки irlite/mixin-apply). Причина: `run/mods/` собран под MC 1.20.4, физически держит 7 jar с хардпином minecraft 1.20.x (worldedit, WorldEditCUI, caxton, curvefixer, refreshedui, shadertoggleaddon, irl_dof). Плюс в `run/mods/` НЕТ Iris → iris-миксины/патчи локально всё равно не проверить. Вывод: полноценный рантайм 1.21.1 требует настроенного окружения (Iris 1.8.8 + BBS-1.21.1 + совместимые аддоны), которого локально нет; feedback-протокол рантайма настроен на -Pmc=1.20.4.
- **РЕШЕНИЕ ЮЗЕРА: собрать jar, тест в его сборке Prism Launcher (1.21.1).** jar = `bbs-irlights-addon/build/libs/irlite-1.1+mc1.21.1.jar` (копия на Desktop). Самодостаточный: JiJ irl-core-1.1.3 (ровно 1) + bundled 7 master-патчей `assets/irlite/patches/*.irlights` (размеры сверены с диском). **✅ РАНТАЙМ PASS 2026-07-22 (юзер, Prism 1.21.1 — «всё норм»).** → готово к коммиту+push порт-веток по явному «да».
- **✅ ЗАКОММИЧЕНО+ЗАПУШЕНО 2026-07-22** (по явному «да» после PASS): core **origin/1.21.1 = 127cd07** (832e5f1..127cd07); addon **origin/port/1.21.1 = 395203b** (4de6d74..395203b, 37 файлов +7711/-1631). **АДДОН-ПОРТ 1.21.1 ЗАВЕРШЁН.** Аддон-тираж целиком закрыт: 1.20.x = master (канон, фаза A) + 1.21.1 = port/1.21.1; других аддон-версий нет.

### Коррекции устаревших фактов памяти (ФАКТ-проверено understand-wf 2026-07-22)
- **ProgramSamplersBuilderMixin + SamplerBindingCubeArrayMixin БАЙТ-ИДЕНТИЧНЫ master↔port** (пустой diff). Арность addDynamicSampler вынесена в core `org.qualet.irl.light.iris.IrlSamplersBind` (per-version core-файл). [[reference-edit-routing-by-area]] строка «ProgramSamplersBuilderMixin версионный, НИКОГДА не шарить» УСТАРЕЛА — миксин нейтрален, НЕ тиражировать.
- **Iris на порте = 1.8.8+1.21.1** (build.gradle порта И core-ветки 1.21.1), НЕ 1.10.7. Коммент build.gradle: «1.8.8 сохраняет 2-arg addDynamicSampler» → арность 2-arg как на 1.20.4. Прежние записи памяти про «Iris 1.10.7 / 4-arg» на 1.21.1 НЕВЕРНЫ.
- Открыто: точный desc CompositeRenderer.createProgram под Iris 1.8.8 ([[iris-source-library]] держит только 1.20.1/1.7.2) — проверить перед портом CompositeRendererTimerMixin.
- Артефакты анализа: journal.jsonl understand-wf в subagents/workflows/wf_e83a01ca-884/ (5 result-строк, полная дельта per-измерение).

Связь: [[reference-edit-routing-by-area]], [[reference-core-versioning]], [[tool-build-trilogy-script]], [[complementary-pipeline]] (база CR-синхры).
