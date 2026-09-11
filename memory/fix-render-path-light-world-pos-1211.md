---
name: fix-render-path-light-world-pos-1211
description: "Render-path свет (морф-актор/реплей) на 1.21 гулял: reconstruction из camera.getRotation рассинхронилась со стеком; фикс = читать позицию/направление из context.world."
metadata:
  node_type: memory
  mod_scope: IRLite-only
  type: project
  originSessionId: 8683a041-6a2e-483f-87ed-9bec291078aa
  modified: 2026-07-23T18:15:00.587Z
---

Баг (ветка port/1.21.1, MC 1.21.1): свет, прикреплённый через BodyPart к форме, при морфе в форму «гуляет рядом» с истинным местом; ГАЙД корректен. На 1.20.1/1.20.4 бага нет. Связано: [[addon-light-collection]], [[addon-forms]], [[fix-modelblock-light-animation-states]].

КОРЕНЬ. Render-path позиция считалась в `IRLightPositionResolver.resolve` (+ направление спота в `SpotlightFormRenderer.registerLight`) как `inverseViewRot * context.stack.peek`. На 1.20 `inverseViewRot` = `RenderSystem.getInverseViewRotationMatrix()`, которую ВАНИЛЬ снимает с ФАКТИЧЕСКОГО стека в `GameRenderer.renderWorld` (`peek().getNormalMatrix().invert()`) — всегда согласована. В 1.21 метод УДАЛЁН из RenderSystem; порт РЕКОНСТРУИРОВАЛ матрицу из `new Matrix3f().rotation(camera.getRotation())`. Реконструкция точна только для ГЛАВНОГО прохода мира. Для морф-актора (особенно bone-attached, рендерится через `ModelFormRenderer` с rig-суб-стеками/матрицами костей) она расходится со стеком → остаточный поворот → свет дрейфует; гайд рисуется прямо в `context.stack`, потому стоит.
Доп. факт MC: в 1.21 сменилась семантика `Camera.setRotation` — `rotationYXZ(π−yaw, −pitch, 0)` (было `rotationYXZ(−yaw, +pitch, 0)`), view строится как `camera.getRotation().conjugate()`. Т.е. `getRotation()` теперь несёт 180°-флип, раньше применявшийся отдельно в renderWorld.

ФИКС (version-proof). BBS ведёт ВТОРОЙ параллельный стек `FormRenderingContext.world` (class_4587) в АБСОЛЮТНЫХ мировых координатах: база = интерполир. world-поза актора + `rotateY(-bodyYaw)` (ставится в `FormRenderingContext.set` при entity!=null && type∈{ENTITY,MODEL_BLOCK}); к нему применяются ТЕ ЖЕ bone/part/form-трансформы, что и к `context.stack` (`FormRenderer.render`, `renderBodyPart`, `ModelFormRenderer.renderBodyParts` пушат в ОБА). Читаем позицию `context.world.peek().getPositionMatrix().transformPosition(new Vector3f())` и направление `...transformDirection(new Vector3f(0,0,1))` — БЕЗ камеры/реконструкции, совпадает с местом гайда by construction. Гейт `context.world!=null && type==ENTITY`; иначе fallback = старая camera-reconstruction (MODEL_BLOCK bone-attached форс-рендер не трогаем — им владеет сканер/остаётся старое поведение). Бонус: render-path стал roll-independent (см. коммент про roll в [[addon-light-collection]] LightCollector).

Затронуто: `src/client/java/qualet/irlite/client/light/IRLightPositionResolver.java` (позиция), `.../forms/SpotlightFormRenderer.java` (направление, +import Vector3f).

Статус (2026-07-23): ✅ DONE — в игре PASS (морф, свет не гуляет, краша нет), закоммичено `c76a73f` на `port/1.21.1` и ЗАПУШЕНО в origin (вместе с ранее локальным f7e08e7). jar `irlite-1.1.4+mc1.21.1.jar` (sha 390f0bb) задеплоен в 3 места: `build/libs`, Prism-инстанс `C:\PrismLauncher\instances\1.21.1\minecraft\mods` (старый 1.1.2 → `.jar.bak`), релизный склад `C:\Users\Qualet\Desktop\IRLights\BBS Addon\1.21.1`. Рантайм инстанса = BBS 2.3.1 + Iris 1.8.14-beta + Sodium 0.8.12-beta (compile против 2.2.1; поле `world` есть в обоих). Тираж на 1.21.4/1.21.11 и редактор — по команде. Тот же паттерн (context.world) стоит применить и на master (1.20.x работает через ванильную матрицу, но world надёжнее/roll-free) — по желанию.

ГОТЧА СБОРКИ (наступили в этой сессии). mavenLocal `org.qualet:irl-core:1.1.4` — ОДНА координата на все MC-линии (per-MC билд, но общий artifactId+version), перезаписывается тем, кто публиковал последним. Был перезаписан 1.20.x-сборкой (master-сессия) → порт забандлил 1.20.x-core → рантайм-краш `NoSuchMethodError RenderSystem.getModelViewStack()` в `ShadowRenderer.applyMatrices` (при бейке теней спота). Причина: сигнатура сменилась 1.20 `getModelViewStack():MatrixStack(class_4587)` → 1.21 `getModelViewStack():org.joml.Matrix4fStack`. ЛЕЧЕНИЕ перед сборкой порта: в `../irl-core` `git switch 1.21.1` (ветка = MC-линия, версия там уже 1.1.4) → `./gradlew publishToMavenLocal` (JDK21) → тогда remapJar аддона бандлит верный core. Верификация: `javap -c ShadowRenderer | grep getModelViewStack` должно показать `Matrix4fStack`. Обратно к 1.20.x: переопубликовать core с master/нужной ветки. См. [[reference-core-versioning]]. Ветка irl-core оставлена на `1.21.1` (была `1.21.11`).
