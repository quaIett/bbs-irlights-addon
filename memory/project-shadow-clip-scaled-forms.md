---
name: project-shadow-clip-scaled-forms
description: "Тень увеличенной формы обрезалась/исчезала: 2 виновника (dynRect-сциссор для динамиков + hitbox-сфера для всех). ОБА закрыты 08-10: тогбл Partial shadow updates + честная сфера (foldFormChain, ревью 10 confirmed исправлены). ИН-ГЕЙМ PASS 08-10 (оба виновника)."
metadata:
  node_type: memory
  type: project
  originSessionId: 1cf29b35-d4e1-4d9e-8cd4-818cfc94ebc0
  modified: 2026-08-10T08:31:13.243Z
---

Симптом (2026-08-10): увеличение формы модел-блока → тень обрезается или исчезает. ДВА независимых виновника, оба найдены и закрыты в одной сессии.

ВИНОВНИК 1 (подтверждён ин-гейм A/B): partial-tile dynRect-сциссор спот-оверлея — обрез динамик-кастеров ровной кромкой, зависит от позиции субъекта. Закрыт живым тогблом **Partial shadow updates** (BBS-настройки → shadows, `shadow_partial_tile`, default ON): core `ShadowConfig.shadowPartialTile()` (optional default) + гейт в ShadowBaker :889 рядом с NO_PARTIAL; -Dirlite.noPartialFilter остался аварийным оверрайдом. Перф-прикидка: выигрыш только спот+движущийся субъект; MEDIUM ~0.2-0.5 мс/кадр, ULTRA×3-4 спота ~5-15 мс/кадр → default ON.

ВИНОВНИК 2: cull-сфера кастера = hitbox (0.5×1.8, чисто ручное поле) × блок-трансформ; рисовка же добавляет form.transform+overlay+additionalTransforms (аддитивный фолд) × config.json scale × body-parts. Заниженная сфера бьёт 5 гейтов (range/cone/face/dynRect/dirty-sig; face-гейт = ровный обрез по 45°-шву кубмапы поинта).

ФИКС СФЕРЫ (DONE 08-10, addon-only, собран; ревью-wf 10 confirmed / 4 refuted, все исправлены, финальный verify 5/5 OK):
- `foldFormChain`: фолд form.transform+overlay+additional (аддитивно, как FormRenderer.createTransform: scale add().sub(1), translate add) × cfg-scale; **храповик ≥1** покомпонентно (сфера только растёт против старой — регрессии исключены); `foldSignY` для зеркальных (négatif Y) форм; cfg-scale через **версионный мост** `modelConfigScale` (2.3=поле scale / 2.4=getScale(), рефлексия с кэшем — прямой field-read падал бы NoSuchFieldError на BBS 2.4 прод-инстанса!) во ВНУТРЕННЕМ try (дрейф гасит только cfg-фактор).
- `expandInnerBox`: |R_form|-экспансия + центр T_fold + colY·(ihy·signY); модел-блок = внутренняя цепочка → прежняя внешняя (signed scale для центра, abs для экстентов, R_b по колонкам JOML column-major).
- Реплеи и морф-энтити (Morph.getMorph игрока / ISelectorOwnerProvider.getOwner().getForm(), БЕЗ check()): non-identity цепочка → raw emit с yaw-симметризацией (hh=corner radius+|горизонт. смещение центра|); **identity fast-path → старый emitFromBox** (иначе dynRect раздувался ×4 на КАЖДОМ обычном реплее).
- ПОПУТНО НАЙДЕН И ИСПРАВЛЕН предсуществующий баг: drawModelBlock применял t.translate ДВАЖДЫ (feet + applyTransform) против 1× у BBS-рендера → тень любого модел-блока с translate≠0 смещена на вектор translate. Фикс парный: feet без translate + pivot сферы 1× (двигать только вместе, иначе cull-blink).

ТЕЛЕМЕТРИЯ (шаг 1 сессии): -Dirlite.clipTelemetry, core ShadowClipTelemetry + хуки ShadowBaker; пробная сфера ×2+2; строка `[irlite] shadow-clip:` 1/с при сигнале; (src)-нота удалена из аддона после фикса (фолд теперь в сфере). Критерий финала: range/cone/face-miss → 0 на репро-сцене.

ГОТЧИ СЕССИИ: BBS 2.3.1 (compile jar) vs 2.4 (прод) дрейф ModelInstance.scale; сборка флешлайта пользователем перепубликовывает core в mavenLocal чистым main (мои правки core были незакоммичены) → чужая публикация тихо откатывает core; loom remap-кэш не видит переопубликованный core той же версии (канон: чистить `.gradle/loom-cache/remapped_mods/.../org/qualet` при остановленных демонах; залоченный демоном jar умирает в pending-delete и воскрешает дир).

ИН-ГЕЙМ PASS (08-10): «тени рисуются корректно что с тогблом, что без»; cone/face/rect-miss = 0 всю сессию (до фикса: 3658/—/756). ОТКРЫТО: geometry-AABB из кубов ModelInstance (authored-large bbmodel с hitbox 0.5×1.8 БЕЗ скейла — не покрыт, только слак); поворот overlay/additional не фолдится (слак); ЗАКОММИЧЕНО 08-10: core main 50671ef (телеметрия+тогбл), addon master c876984 (тогбл UI) + 701336f (сфера+1x-translate); НЕ запушено; тираж на порт-ветки по команде.
