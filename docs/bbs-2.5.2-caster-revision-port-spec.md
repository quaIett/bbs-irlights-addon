# ТЗ: порт силуэт-адаптера ревизий кастеров на BBS 2.5.2-1.20.4

Статус: DONE 2026-09-12 (реализовано и проверено dev-клиентом на BBS 2.5.2 и 2.3.1;
см. §9). Открыт только пользовательский ин-гейм A/B на инстансе Prism (§9.4).
Автор постановки: A/B-сессия 1.1.5 vs 1.1.6 от 2026-09-12.

## 1. Проблема (диагноз подтверждён замером)

Reuse неизменившегося shadow-overlay — headline-выигрыш перф-редизайна
(irlite **1.1.6** / irl-core **1.1.6**, этап 1а; ин-гейм 09-11 на BBS 2.3.1:
bake 7.889→0.006 мс, frame −44.6%). Механизм: `ShadowCasterSource.revision()`
возвращает `CasterRevision`; если ревизия KNOWN и совпала с прошлым кадром,
`ShadowBaker` переиспользует overlay (пропуск copy/draw/pyramid/moments).

**Гейт `AUDITED`** в `BbsModelSilhouette` (стр. 40-41):
```java
static final boolean AUDITED = ... "2.3.1-1.20.4".equals(bbs.version);
```
true ТОЛЬКО для точного `2.3.1-1.20.4`. Оба семплёра (`BbsModelSilhouette`,
`BbsMobSilhouette`) при `!AUDITED` сразу отдают `CasterRevision.UNKNOWN`.

Инстанс BBS обновлён до **`bbs-2.5.2-1.20.4`** → `AUDITED=false` → все кастеры
UNKNOWN → reuse не включается никогда → **1.1.6 идентична 1.1.5**.

Замер 2026-09-12 (та же статичная сцена, Complementary+IRLights, RTX 3060 Laptop,
854×480, VSync off), p50 по ~70/23 окнам профайлера:

| Метрика | A = 1.1.5 | B = 1.1.6 |
|---|---|---|
| frame → FPS | 47.27 мс → 21.2 | 47.35 мс → 21.1 |
| gpu bake | 20.78 мс | 21.07 мс |
| cpu pipeline | 17.62 мс | 18.22 мс |
| deferred2 (VL) | 5.82 мс | 5.76 мс |

Счётчики B за всю сессию: **`sp.reuse = 0`** (мин=макс), `caster.unknown` до 2222,
`sp.dyn` до 1408. Это не баг логики reuse — консервативный гейт бережёт от
замороженных теней на непроверенной версии BBS. Джава/VL-микроопты дают только шум.

## 2. Цель

Расширить поддержку ревизий на BBS `2.5.2-1.20.4`, СОХРАНИВ консервативную
корректность (никаких залипших/отставших теней). Приёмка — см. §6.

## 3. Дрейф API 2.3.1 → 2.5.2 (ФИНАЛЬНАЯ таблица; сверено по sources-jar
`bbs-fs/build/libs/bbs-2.5.2-1.20.4-sources.jar`, побайтово совпадающему с jar
инстанса, и по diff исходников 2.3.1→2.5.2)

### 3.1 `mchorse.bbs_mod.cubic.ModelInstance` — конфиг ушёл в `ModelConfig`
Все поля конфига переехали в `public final ModelConfig config` (value-tree),
наружу — геттеры:

| 2.3.1 (поле) | 2.5.2 | адаптер |
|---|---|---|
| `procedural` | `isProcedural()` | мост |
| `culling` | `isCulling()` | мост |
| `scale` | `getScale()` | мост (заменил cfgScaleField/Getter в IRLiteBbsCasterSource) |
| `texture` | `getTexture()` (= `config.texture` либо новое поле `baseTexture`) | мост |
| `view` | `getView()` — НЕ удалён (null, пока `config.lookAt.head` пуст) | мост, гейт look-at сохранён |
| `itemsMain`/`itemsOff` | `getItemsMain()`/`getItemsOff()` | мост |
| `armorSlots` | `getArmorSlots()` | мост |
| `model` | было `IModel` уже в 2.3.1 — дрейфа НЕТ; `Model` (cubic) на месте | без изменений |
| — | НОВОЕ: `getWeldBindings()` (welds → CubicCubeRenderer деформирует кубы по позе, hybrid VAO+CPU) | гейт: welds ≠ ∅ → UNKNOWN |
| — | НОВОЕ: `partialVaos` (shape-keyed меши → hybrid) | гейт: `!getVaos().isEmpty() && !isVAORendered()` → UNKNOWN |
| `materials`, `materialTextures`, `getMaterialTexture`, `getVaos`, `isVAORendered` | на месте | без изменений |

### 3.2 `utils.pose.Transform` — дрейф, которого в первой версии ТЗ не было
`rotate2` УДАЛЁН; добавлены `rotationMode` (enum EULER/QUATERNION) и `quat`.
Все чтения `Transform.rotate2` (Signature.transform, BbsMobSilhouetteChecks)
падали бы `NoSuchFieldError`. Сигнатура теперь хеширует translate/rotate/scale +
(rotate2 | rotationMode+quat) через мост.

### 3.3 `cubic.data.model.ModelGroup` — новое транзиентное поле `offset`
IK-stretch сдвиг (пишет только IK; `reset()` обнуляет; рендер применяет в
`ICubicRenderer.offsetGroup` ДО translate). Сигнатура позы хеширует `offset`;
`BbsModelPoseScratch` сохраняет/восстанавливает его как `orient`.

### 3.4 `Model.applyPose` — QUATERNION-ветка
Сигнатуры `resetPose/applyPose/getOrderedGroups/topGroups/textureWidth` не
менялись; в QUATERNION-режиме `orient` композируется из `quat`. Проба зовёт
настоящий `getPose()`+`applyPose()`, поэтому семантика ветки покрыта автоматически;
самотест (§9) проверяет quaternion-позу отдельно.

### 3.5 `ModelFormRenderer` — evaluateChannels + apply*Once
`resetPose → animator.applyActions → applyPose(getPose())` вынесено в
`evaluateChannels` (семантика та же). IK/physics/constraints (`apply*Once`)
при пустых `form.ik/physics/constraints` — no-op (`ModelIKRuntime.apply`:
compiled=null/пустые chains → return; `ModelPhysicsRuntime.apply` то же;
`ModelConstraintsRuntime.apply`: bones пусто → return). Debug-оверлеи:
`ModelIKDebug.enabled`/`ModelPhysicsDebug.enabled` УДАЛЕНЫ (гейтятся
`BBSSettings.ikDebug.enabled` + непустыми цепями, т.е. при пустом ik — не рисуют);
адаптер читает старые флаги через мост (на 2.5.2 = false).

### 3.6 `MobForm`-путь — цел
`MobFormRenderer` статики/`ensureEntity`/`entity` на месте (миксин-аксессоры
применяются), `LivingEntityRendererMixin` применяет позу через
`lerpRotation/addRotation/getEulerRotation` (quaternion-aware), наша проба идёт тем
же `dispatcher.render`. Изменения render3D (FormTranslucentQueue, restore model-view)
силуэт-нейтральны.

### 3.7 Прочее на месте
`Form.statePlayers`, `ModelForm.*` (+`windControlOverride`), `Texture.uploadTexture/
setSize/setParameter/generateMipmap/delete` (ревизионный миксин применяется; новое
`hasTranslucency()` не используется), `ModelVAO.upload/delete`, `TextureManager.
textures/animatedTextures` (error-текстура больше не кладётся в `textures`, failed
→ `get(link)==null` → UNKNOWN, консервативно), `BBSModClient.getModels().models`.

## 4. Файлы (реализовано)

- НОВЫЙ `src/client/java/qualet/irlite/client/light/BbsSilhouetteBridge.java` —
  version-bridge: MethodHandle на каждый дрейфующий член, резолв ОДИН раз при
  инициализации класса; `AUDITED` = версия ∈ {`2.3.1-1.20.4`, `2.5.2-1.20.4`} И
  layout членов совпадает с проаудированным (legacy: rotate2 без offset/welds;
  modern: rotationMode+quat+offset+getWeldBindings). Лог при старте:
  `[irlite] caster-revision bridge: bbs <ver> audited | NOT audited: <что не так>`.
  `reportFailure` — первый сбой пробы печатается один раз (stack trace).
- `BbsModelSilhouette.java` — все чтения через мост; новые гейты welds/hybrid;
  `offset` в сигнатуре позы; `Signature.transform` мостовой; трассировка причин
  UNKNOWN (`-Dirlite.debugCasterRevisions=true`, каждая причина печатается один раз:
  `[irlite] caster-revision(cubic|mob): UNKNOWN because ...`); хук самотеста.
- НОВЫЙ `BbsModelSilhouetteChecks.java` — самотест cubic-пути (PASS 14 на 2.5.2):
  повтор, трансформы блока/формы, поза (euler rotate/translate, quaternion-режим на
  2.5.2), восстановление живой позы модели (current/color/lighting/orient/offset
  по identity и значениям), изоляция живого offset, shape key, цвет, visible,
  unsupported IK → UNKNOWN, всё восстановлено, resource reload.
- `BbsMobSilhouette.java` — трассировка причин; `BbsMobSilhouetteChecks.java` —
  проверка второй ротации: rotate2 (2.3.1) либо quaternion (2.5.2). PASS 11 на обеих.
- `BbsModelPoseScratch.java` — capture/restore `offset`.
- `IRLiteBbsCasterSource.java` — `revision()` в try/catch (RuntimeException|
  LinkageError → UNKNOWN + reportFailure; AssertionError самотеста пропускается
  намеренно); `modelConfigScale` → мост; debug-счётчик собранных model-block
  (`[irlite] caster-collect: model blocks N (was M)` при изменении набора).
- `build.gradle` — dev-knob `-PbbsRuntimeJar=<jar>`: компиляция остаётся на
  `libs/bbs-2.3.1-1.20.4.jar`, runClient грузит указанный BBS (modCompileOnly +
  modLocalRuntime). Так один билд прогоняется против 2.5.2 без правки libs.

## 5. План работ — выполнен

1. Инвентаризация: diff sources-jar 2.3.1 vs 2.5.2 (36 файлов в зоне адаптера) +
   javap; результат — §3.
2. Version-bridge — `BbsSilhouetteBridge` (§4).
3. Аудит силуэт-нейтральности после applyPose на 2.5.2 — §3.4–3.6; неизвестное
   (welds, hybrid, look-at, items/armor, procedural, IK/physics/constraints, state
   players, actions) = UNKNOWN.
4. `AUDITED` = множество версий + проверка layout (§4).
5. Самотесты: mob PASS 11, cubic PASS 14 (новый) — на 2.5.2 и 2.3.1.
6. Ин-гейм — §9 (dev-клиент), пользовательский A/B на Prism — открыт.

## 6. Критерии приёмки

- Самотест PASS на 2.5.2 (и регресс 2.3.1, если версия ещё поддерживается).
- Ин-гейм статичная сцена на 2.5.2: `sp.reuse > 0`, `caster.unknown ≈ 0`,
  `gpu bake p50 → ~0`, измеримый FPS-выигрыш vs 1.1.5 (ожидание порядка
  bake 21→~0 мс, frame ~47→~30 мс на этой сцене — свести профайлером как 09-12).
- НИ ОДНОГО замороженного/отставшего силуэта: анимация/морф/поза/экипировка/
  кастомное имя обязаны оставаться UNKNOWN → dyn-rebake каждый кадр (INVARIANT 2).
- Один jar грузится и на 2.3.1, и на 2.5.2.

## 7. Риски / готчи

- **Семантический дрейф**, не только сигнатурный: покрыт §3.4–3.6; правило
  «неизвестное = UNKNOWN» + layout-проверка в `AUDITED` (сборка BBS с той же
  строкой версии, но другим набором членов → NOT audited, лог при старте).
- `view` НЕ удалён (§3.1) — гейт look-at работает через `getView()`.
- Материалы/VAO: в hybrid-режиме VAO-группы биндят per-material текстуры, а CPU-
  половина рисует текущей — поэтому hybrid → UNKNOWN целиком.
- Mixin-аксессоры проверены применением на 2.5.2 (mob-самотест зовёт
  `irlite$ensureEntity/entity/pose/overlay`).
- `emitModelBlock.isStatic` по-прежнему `false` (INVARIANT 2) — не трогалось.
- Watch (draw-сторона, не revision): на 2.5.2 `FormTranslucentQueue` откладывает
  полупрозрачные слои в end-of-frame очередь, если во время нашего bake
  `BBSRendering.isRenderingWorld()` ложно; для текстур без полупрозрачных текселей
  (`Texture.hasTranslucency()==false`) путь немедленный. Ин-гейм визуал 09-11 на
  2.5.2 регрессий не показал.
- Байткод-чекер дрейфа (`<scratchpad>/checkrefs.py`, javap-based, идёт по
  наследованию mchorse-типов): 177 mchorse-ссылок силуэт-классов + IRLiteBbsCasterSource
  резолвятся против ОБОИХ jar (0 нерезолвленных; `ModelBlockEntity.getPos` —
  унаследован от MC, вне проверки).

## 8. Референсы

- Мост: `BbsSilhouetteBridge` (заменил образец `IRLiteBbsCasterSource.modelConfigScale`).
- Кросс-версия прецедент: memory `fix-bbs24-uitrackpad-limit`, `IrliteBbsCompat`.
- Спека шва + 5 инвариантов: `irl-core/docs/shadow-caster-seam-spec.md`.
- Сырьё замера-доказательства inert: `<scratchpad>/ab-results/{A-1.1.5,B-1.1.6}-full.log`
  (сессия 2282deae).

## 9. Результаты проверки 2026-09-12 (dev-клиент, `gradlew runClient -Pmc=1.20.4`,
Complementary_IRLights, shadow_quality 3, 854×480)

### 9.1 BBS 2.5.2 (`-PbbsRuntimeJar=<инстанс>/mods/bbs-2.5.2-1.20.4.jar`)
Мир `Flat` (25 villager MobForm-блоков + 1 spot; player неподвижен):
- `caster-revision bridge: bbs 2.5.2-1.20.4 audited`
- `caster-revision-checks: PASS 11` (villager MobForm)
- счётчики по 70-кадровым окнам: `caster.known 1750 | caster.unknown 70 | sp.reuse 1750`
  = 25 known + 25 reuse на кадр, 1 unknown (сущность игрока, UNKNOWN by design)
- gpu: `bake avg 0.01 p50 0.01 мс`, `bake-spot 0.00` (в A/B 09-12 было 21 мс);
  cpu: frame p50 14.3 мс, pipeline p50 1.4 мс.
Мир `Flat` с одним блоком, заменённым на cubic ModelForm `mobs/chair` (плоская
bbs.json-модель без config):
- `caster-revision-checks(cubic): PASS 14 (evaluated cubic ModelForm, live pose restored, bbs 2.5.2-1.20.4)`
- 25 known/кадр (24 villager + chair), `sp.reuse` 25/кадр, bake 0.01 мс.
Мир `Room` (film со сменой форм villager→enderman→piglin→snow_golem + ModelForm со
state players): трассировка показывает причины
(`mob is not a villager: minecraft:enderman`, `animation state players active`),
после остановки на villager — known 70/70, `sp.reuse 70`; point-свет с игроком в
радиусе честно остаётся `pt.dyn.f` (unknown-член запрещает reuse).

### 9.2 BBS 2.3.1 (регресс, штатный libs-jar)
`bridge: bbs 2.3.1-1.20.4 audited`, mob `PASS 11`, cubic `PASS 12` (quaternion-
и live-offset-проверки пропущены — членов нет на 2.3.1), `caster-collect: model
blocks 25`, 25 known/кадр (24 villager + chair), 1 unknown (игрок), bake 0.01 мс,
44 окна стабильно.

### 9.3 Статика
Компиляция против 2.3.1 зелёная; байткод-чекер 0 нерезолвленных против 2.3.1 и 2.5.2.

### 9.4 Открыто
Пользовательский ин-гейм A/B на инстансе Prism (сцена 09-12): ожидание
`caster.unknown ≈ 0` зависит от состава сцены — с `-Dirlite.debugCasterRevisions=true`
в JvmArgs инстанса лог покажет, какие кастеры и почему UNKNOWN (не-villager мобы,
items/armor в config модели, welds, state players, actions — всё by design).
