---
name: project-performance-redesign-stage1a
description: "Перф-редизайн IRLite — этап 1а (reuse неизменившегося shadow-overlay) реализован на машине ZoGa, незакоммичен; core→1.1.6"
metadata:
  node_type: memory
  type: project
  originSessionId: d1e7cdfd-f32e-4ff7-b882-ade7b40dbd90
  modified: 2026-09-11T16:59:37.133Z
---

Перф-редизайн IRLite по плану `project-docs/bbs-irlights-addon/performance-redesign.md`. Цели (НЕ достигнуты): shadow bake 14→5–9 мс, VL ×2–4. Этапы 0 и 1а сделаны; 1б–4 открыты.

**Где живёт код.** Реализовано в сессии 2026-09-11 на ДРУГОЙ машине (ZoGa), снимок `C:\Users\qualet\Desktop\IRLights` (source: `C:\Users\ZoGa\Downloads\IRLights`). Изменения stage-0 + stage-1а **НЕзакоммичены**. **ПЕРЕНЕСЕНО НА ЭТОТ ПК (qualet) 2026-09-11**: HEAD совпадали (addon `701336f`, core `50671efd`), оба дерева были чисты → побайтовая копия изменённых+новых файлов из снимка (core 15 файлов, addon 28), локальный `.git` не тронут. core 1.1.6 переопубликован в mavenLocal (был только 1.1.5), аддон собран `-Pmc=1.20.4` на JDK 21 (Loom 1.15.5 требует 21; core на JDK 17/Loom 1.9.2) → `irlite-1.1.5+mc1.20.4.jar` с вложенным `irl-core-1.1.6.jar`. Локально всё так же НЕзакоммичено (как в снимке). На GitHub этого НЕТ. Остальные 3 репо снимка чисты (irlights e8b5a9f, flashlight d7af972c, dof e27a808). Коммиты/push/ветки не трогались [[project-workstation-resync-2026-09-10]]. Готча JAVA_HOME: по умолчанию был JDK 8 → для сборок явно указывать jdk-17 (core) / Adoptium jdk-21.0.12 (addon).

**Этап 0 (был в дереве до сессии):** покадровый GPU/CPU-профайлер, CSV-захват, shadow-таймеры, baseline-доки.

**Этап 1а — reuse целого неизменившегося overlay.** core 1.1.5→**1.1.6** (обе dep аддона переключены; версия аддона осталась 1.1.5). Контракт в `project-docs/irl-core/caster-revisions.md`, `casterRevisionSchema=1`:
- `CasterRevision` — known/UNKNOWN + 6 доменов: transform, evaluated pose, morph, geometry, material/cutout, resources. Ноль допустим; UNKNOWN не кодируется случайным числом.
- `ShadowCasterSource.revision` — новый опциональный метод, default UNKNOWN (старые источники живут).
- `ShadowOverlayCache` — хранит только успешно завершённый overlay; явное сравнение members (не `IdentityHashMap.equals`): identity caster, ревизии, bounds/face mask, light/static signature, tile, identity static-основы, partial-tile. UNKNOWN/null/повтор identity → запрет reuse.
- `ShadowBaker` — 1 раз/кадр берёт состояние каждого динамического caster; при полном совпадении завершённого overlay пропускает copy/draw/pyramid/moments. BBS-объекты НЕ уводятся в static layer. Инвалидация: смена мира/качества, off шейдеров/кэша, вытеснение/handoff tile, снятие фильтров, сбой прохода (`ShadowRenderer` монотонный failure stamp). Готовность texture/FBO/program проверяется до reuse.
- BBS-адаптер (только `2.3.1-1.20.4`, ориентир BBS 2.3 сверен по javap): `IRLiteBbsCasterSource` (isStatic=false не меняется), `BbsModelSilhouette` (cubic ModelForm без actions/animator/IK/physics/constraints/look-at/attachments; настоящий resetPose/applyPose+getPose, restore в finally; geometry memo 1 кадр), `BbsMobSilhouette` (стандартный villager MobForm — полный EntityRenderDispatcher с CPU-only VertexConsumer, включая ground shadow). Конечные дочерние Point/Spot-формы разрешены (при bake геометрию не льют). Всё прочее → UNKNOWN (каждый кадр).
- Мискины ресурс-ревизий: `TextureShadowRevisionMixin`, `ModelVaoShadowRevisionMixin`. Reload/смена Iris pipeline → бамп ресурсной ревизии.
- Счётчики: `caster.known`, `caster.unknown`, `sp.reuse`, `pt.reuse`. Флаги: `-Dirlite.noOverlayReuse=true` (контроль A/B), `-Dirlite.checkCasterRevisions=true` (10 диагн. проверок, лог `caster-revision-checks: PASS 10`).

**Проверки (снимок):** core build+publishToMavenLocal OK; addon build -Pmc=1.20.4 OK; 34 CPU-теста overlay; 15 тестов профайлера; CSV-регрессия; 10 in-client проверок villager; ровно один вложенный core 1.1.6 (SHA совпал), Java 17/major 61. Итого 59 проверок + регрессия. Готча: переиздание core под той же версией оставляет старый Loom remap — удалять точечно `.../irl-core-<hash>/1.1.6` и пересобирать аддон [[reference-core-versioning]].

**In-game (финал `capture-13240775783976152988.csv`, окно [241,841), 600 кадров):** каждый кадр `caster.known=26, caster.unknown=1, sp.reuse=26`; spot copy/draw/pyramid/moments не выполнялись. CPU pipeline avg 1.44 мс / p95 1.90; CPU frame avg 7.52 / p95 10.35; GPU bake avg ~0.005 мс. Baseline эт.0 `capture-1195174666892912146.csv`: bake ~8.15 мс — но условия РАЗНЫЕ (854×480 vs 2560×1361, другая камера/плеер). **Подтверждён факт пропуска работы; количественное ускорение сопоставимым A/B НЕ доказано.**

**A/B ДОКАЗАН на этом ПК 2026-09-11** (после переноса; сцена с villager MobForm+spot, PASS 10). Оба прогона идентичны (окно [241,841), 600 кадров, 0 пропущено, shadowQuality:3, VL off, softness 0.1), различается только `overlayReuseDisabled`. Результат (`build/performance/A3-reuse-on.json` / `B-reuse-off.json`): **GPU bake 7.889→0.006 мс** (≈1350×; в B: bake-spot-evsm 3.48 + copy 1.43 + draw 1.78 + pyr 1.20); **CPU pipeline 6.93→1.68 мс**; **CPU frame 16.41→9.09 мс (−44.6%, ≈61→110 FPS-экв)**. A: sp.reuse=25/кадр, B: sp.copy=25/кадр (полный бейк каждый кадр). Готча замера: пустая/кастомная сцена даёт caster.known=0 (все UNKNOWN) → reuse не срабатывает и A/B нулевой; нужен именно стандартный villager MobForm или статичный cubic ModelForm (первые 2 прогона на «неправильной» сцене дали 0 known). analyze-profile.ps1 запускать через `powershell` (pwsh нет в PATH), сначала создать `build/performance`.

**Открыто:** полный визуальный набор (движение света/caster/камеры, появление/исчезновение, cutout/material/reload, края/швы, смена настроек, tile handoff); прогретый A/B с `-Dirlite.noOverlayReuse=true`; in-game cubic-путь (сцена дала только MobForm); отдельный point-сценарий; этапы 1б–4. Команды сборки/запуска/A/B — в снимке `documentation/BUILD-AND-CONTINUE.md` (JDK 21, `--no-daemon`, локальные кэши). Опора теней [[plan-irl-core-shadow-extraction]], [[project-shadow-bake-perf-audit]].
