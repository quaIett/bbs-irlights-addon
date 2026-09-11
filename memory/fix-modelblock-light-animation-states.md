---
name: fix-modelblock-light-animation-states
description: "ModelBlock-свет не следовал за animation states формы; фикс в LightCollector.walk (scanner-path) — applyStates/unapplyStates, master commit 8bc936d."
metadata:
  node_type: memory
  mod_scope: IRLite-only
  type: project
  originSessionId: cf54ebf5-7b00-49de-a563-00db68755a25
  modified: 2026-07-22T23:57:15.305Z
---

Баг: PointLightForm/SpotlightForm, размещённая через BBS ModelBlock, НЕ анимируется animation states формы (видимая модель едет за анимацией, свет стоит). Связано: [[addon-light-collection]], [[addon-forms]], [[addon-architecture]], [[fix-bone-attached-light-deadzone]].

КОРЕНЬ (неочевиден). В BBS `AnimationState`/`Form.states` накладывается на Value-поля формы (`transform`, `visible`, ...) как ВРЕМЕННЫЙ runtime-override, живущий ТОЛЬКО внутри окна `FormRenderer.render()`:
`applyStates(transition)` (стр ~93) -> рендер, где `form.transform.get()` = анимированный -> `unapplyStates()` (стр ~140) откатывает `setRuntimeValue(null)`. Вне окна `form.transform.get()` = БАЗОВАЯ поза.
`LightCollector` (scanner-path, владеет ModelBlock-светом + dashboard-preview реплеями) работает на `renderWorld @HEAD` — целиком ДО рендера форм — и `walk()` читал `form.transform.get()` напрямую = база. Отсюда баг. Механика наложения: `StatePlayer.assignValues` -> `FormProperties.applyProperties` -> per-channel `property.setRuntimeValue(interpolated)` (pose-bone каналы самосбрасываются перед накоплением, поэтому applyProperties идемпотентен — двойной вызов scanner+render безопасен).
Render-path (живые акторы / in-world film-реплеи) бага НЕ имел: `registerLight` читает `context.stack.peek()`, а стек формируется рендерерами уже ПОСЛЕ `applyStates` (родитель применяет до обхода детей).

ФИКС (master аддона, commit 8bc936d, 2026-07-23, только per-mod — irl-core 1.1.3 НЕ трогали): `LightCollector.walk()` зеркалит окно рендера — `form.applyStates(transition)` перед чтением трансформа, `form.unapplyStates()` в `finally` после обхода поддерева, re-apply per-form на рекурсии (совпадает с вложенностью FormRenderer: родитель охватывает детей). `transition`(=tickDelta) проброшен из `collect()` в `scanBlockEntities` и `scanFilmReplays` (оба зовут walk). visible-проверка перенесена ПОСЛЕ applyStates (состояние может анимировать `visible`), как в FormRenderer. No-op для форм без активных statePlayers -> нулевой риск для не-анимированных.
Покрывает scanner-кейсы: свет как root ModelBlock-формы И как дочерняя форма (BodyPart без bone), с animation state на любом уровне дерева.

Статус: compileClientJava OK; байт-верифай (`applyStates(F)`/`unapplyStates()` в LightCollector.class); runClient 1.20.4 поднялся, наших исключений в логе за сессию НЕТ; юзер принял («отлично, коммитим»). Референс BBS: sources jar 2.2.1 (боевой pin = bbs-2.3.1-1.20.1/1.20.4, sources только 2.2.1 — классы states/* близки).

Открыто: тираж на порт-ветки по команде (аддон `port/1.21.1`; редактор main/порт-ветки — там свой сбор LightScene/PlacedLight, механику states адаптировать отдельно). НЕ пушено (commit локальный на master).
