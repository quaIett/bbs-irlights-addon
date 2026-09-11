---
name: fix-cookie-render-path-spotlights
description: "Gobo/cookie не проецировался на спотах, зарегистрированных render-path'ом (спот на BodyPart-кости, живые актёры, in-world реплеи) — render-path звал no-cookie перегрузку registerSpot; фикс = резолв cookie в SpotlightFormRenderer.registerLight. master=1a23b6b, port/1.21.1=5e73427."
metadata:
  node_type: memory
  mod_scope: IRLite-only
  type: project
  originSessionId: e03cea10-d762-4777-a89f-a8a58a7b30e5
  modified: 2026-07-23T20:11:12.703Z
---

Баг (репорт user): gobo/cookie-текстура НЕ работает на спотлайтах, прикреплённых к форме через бодипарты. Связано: [[project-spotlight-gobo-cookie-plan]], [[fix-modelblock-light-animation-states]], [[addon-forms]], [[addon-light-collection]], [[reference-edit-routing-by-area]].

КОРЕНЬ. Спот регистрируется в SSBO ДВУМЯ путями, и они разошлись по cookie:
- Scanner-path (`LightCollector.emitSpot`) резолвит `CookieArray.resolve(form.cookie.get())` и зовёт ПОЛНУЮ перегрузку `LightRegistry.registerSpot(... cookieLayer, cookieRot, cookieScale, cookieFlags, identity)`.
- Render-path (`SpotlightFormRenderer.registerLight`) звал КОРОТКУЮ перегрузку `registerSpot(...shadows, identity)` — core делегирует её с cookie `-1F,0F,1F,0F` (маска ВЫКЛ). Отсюда: свет есть, gobo нет.
Спот на BodyPart с НЕ-пустой костью (`bone`) сканер ВСЕГДА пропускает (`walk()` делает `continue` для bone-частей — у сканера нет позы скелета) и делегирует render-path через гейт `boneAttached` в `AbstractLightFormRenderer.render3D` (`form.getParent() instanceof BodyPart && !bone.isEmpty()`). Поэтому именно bone-attached спот (репорт user) шёл render-path'ом = без cookie. Тот же дефект у ВСЕХ render-path спотов: живые актёры, in-world film-реплеи.

ФИКС (per-mod, аддон; core НЕ трогали — cookie-перегрузка registerSpot там уже есть): в `SpotlightFormRenderer.registerLight` добавлен резолв cookie (зеркало emitSpot) + переключение на полную перегрузку `registerSpot` с `(float)cookieLayer, cookieRot, form.cookieScale.get(), cookieFlags`. +1 импорт `qualet.irlite.client.light.cookie.CookieArray`. 13 строк, идентичны на master и порте (cookie-часть версионно-нейтральна; на порте отличается лишь блок направления — context.world, см. [[fix-render-path-light-world-pos-1211]]). `CookieArray.resolve` безопасен на null-link (→ -1); зовётся на render thread (uploads to GL) — как scanner.

СТАТУС/ТИРАЖ (user снял 1.21.x-табу командой «то же для 1.21.1»):
- master `1a23b6b` — 1.20.4 IN-WORLD PASS у user («Отлично»); 1.20.1 тем же коммитом. compileClientJava OK, runClient 1.20.4 стартовал чисто (наших исключений нет; NPE `link is null` в UITexturePicker = известный BBS-внутренний шум, не наш).
- port/1.21.1 `5e73427` — compileClientJava + build OK (против core 1.21.1). IN-WORLD НЕ проверялся (рантайм проекта только 1.20.4, см. [[feedback-addon-runclient-command]]); фикс = зеркало подтверждённого 1.20.4.
- НЕ запушено (master +2 vs origin, port/1.21.1 +1).

СБОРКА (user «собери все версии аддоном в нашу папку» → выбор Desktop\IRLights\BBS Addon): 3 jar вручную (НЕ build-trilogy — тот fail-fast и на каждой линии собирает редактор ПЕРЕД аддоном; editor port/1.20.1 пинит stale core 1.1 → бамп до 1.1.4 = невыверенный дельта → риск завалить сборку аддона 1.20.1). Рецепт ручной сборки только аддона: (1) core 1.21.1 publish JDK21 → аддон port/1.21.1 `build -x test -Pmc=1.21.1` JDK21; (2) core main publish JDK17 (main = Loom1.9/Java17!) → сброс loom remap-кэша `.gradle/loom-cache/remapped_mods/**/irl-core*` + Gradle module-cache `~/.m2`... нет: `~/.gradle/caches/modules-2/files-2.1/org.qualet/irl-core` (слот 1.1.4 переписывался main↔1.21.1, module-cache мог держать чужой вариант) → аддон master `build -x test -Pmc=1.20.4` и `-Pmc=1.20.1` JDK21. Раскладка: `Desktop/IRLights/BBS Addon/1.20.x/` (irlite-1.1.4+mc1.20.4 + +mc1.20.1 сосуществуют) + `.../1.21.1/`. Байт-верифай nested core (major класса LightRegistry: 61=Java17/1.20.x, 65=Java21/1.21.x; `unzip -p jar cls | od -An -tu1 -j6 -N2`) — 1.20.x=61, 1.21.1=65, верно. VERSION=mod_version=1.1.4.

Открыто: пуш по команде; in-world 1.21.1 при желании user; порт на др. ветки аддона нет (BBS макс 1.21.1 — тираж аддона ЗАКРЫТ на master+port/1.21.1).
