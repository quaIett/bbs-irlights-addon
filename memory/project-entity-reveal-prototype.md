---
name: project-entity-reveal-prototype
description: "Прототип reveal-in-light для CR (MC 1.20.4): сущность рисуется только в конусе спота, мягкий дизер-край. Ветка feature/entity-reveal-spotlight-cr, локально, НЕ закоммичено."
metadata:
  node_type: memory
  type: project
  mod_scope: addon
  originSessionId: 864e6f4f-a5ed-4531-b3f0-f27d96198b93
  modified: 2026-07-23T19:39:46.015Z
---

Задача (юзер 2026-07-23): быстрый прототип для показа клиенту — спотлайт «проявляет» только ту часть сущности, что попадает в его конус; вне конуса сущность не рисуется (сквозь неё виден фон). Пак **ComplementaryReimagined** (forward-путь → `discard` в gbuffers_entities реально вырезает пиксель). Скоуп строго: BBS-аддон под **MC 1.20.4**, только CR, локальная ветка без пуша. Предшествующий recon+план+адверсариал-проверка — воркфлоу reveal-in-light-recon.

Ветка: `feature/entity-reveal-spotlight-cr` от master (аддон). НЕ закоммичено (юзер: «оставим так, жди дальнейшей команды»). Коммит-политика [[commit-checkpoints]] — ждать явного чекпоинта. ВСЕ правки в одном файле: `patches/complementaryreimagined.irlights` (Java не трогается вообще).

Реализация (чисто шейдерная): тумблер `IRLITE_ENTITY_REVEAL` (голый `//#define`, default OFF) + 2 слайдера — `IRLITE_REVEAL_FEATHER` (ширина мягкого края, доля конуса rim→axis, деф 0.35, [0.05..0.70]) и `IRLITE_REVEAL_DENSITY` (плотность заливки, `pow(reveal, 1/density)`, деф 2.0, [1.0..4.0]). Механизм: `irlite_lightSurface` отдаёт `out float coverage = max` по спотам `smoothstep(0, feather, edge)`, где edge=(theta−cone.x)/(1−cone.x); в mainLighting под `#if defined GBUFFERS_ENTITIES && defined IRLITE_ENTITY_REVEAL` — дизер-`discard` (IGN по gl_FragCoord, `if (pow(coverage,1/density) < ign) discard`). Настоящей альфы НЕТ — entity-проход непрозрачный, не блендит; дизер+TAA даёт эффект прозрачности (TAA ОБЯЗАТЕЛЕН, иначе зерно). Гейт `GBUFFERS_ENTITIES` (не nonTerrain) → только сущности, не рука/блоки. Shadow-проход не задет (shadow.glsl не инклудит mainLighting/irlite_lights).

Свойства/ограничения (адверсариал-проверка): эффект на ВСЕ сущности (дропы, name-теги, 3-е лицо) — демо-сцену держать чистой (один моб); частично вырезанный моб бросает ПОЛНУЮ тень; coverage требует спот+радиус+конус одновременно (радиус спота должен дотягиваться до всего моба, иначе низ исчезнет); point-лампы не проявляют. В игре: Apply Patch → Iris reload → включить тумблер + TAA + спот. Визуально юзером на момент паузы ЕЩЁ НЕ подтверждён.

Не-в-MVP (после апрува): per-light reveal-флаг (`cookie.w` bit1 + Java UI, bit0 занят invert), blue-noise вместо IGN (`irl_blueNoise` не биндится в entity-проходе), soft radius-edge, учёт shadow/gobo-гейтов, фильтр по entityId, тираж на др. паки. Билд/запуск-окружение (JDK-matrix, без-рестарта цикл) → [[feedback-addon-runclient-command]]. Контракт GLSL света → [[shader-irlite-glsl]], forward vs deferred по пакам → [[complementary-pipeline]].
