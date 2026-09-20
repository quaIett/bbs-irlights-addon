# Силуэт-аудит кастеров на BBS FS 2.7 (master, MC 1.20.1 / 1.20.4)

Продолжение `bbs-2.5.2-caster-revision-port-spec.md`: тот же гейт `AUDITED`, та же схема
«неизвестное = UNKNOWN». Аудит нужен потому, что версия BBS входит в гейт буквально: на
непроверенной версии `BbsSilhouetteBridge.audit()` печатает `NOT audited`, все кастеры
становятся UNKNOWN и тень печётся полностью каждый кадр (корректно, но без переиспользования).

Статус: DONE для `2.7-1.20.1` и `2.7-1.20.4`. Открыт cubic-самотест (нужен блок с cubic
ModelForm в тестовом мире) и вся линия 1.21.x (см. §5).

## 1. Дрейф 2.6 → 2.7 в зоне адаптера

Сверено по diff `bbs-fs` 0ee948b36 → 13bd62dab, по классам, которые читают
`BbsModelSilhouette`, `BbsMobSilhouette`, `Bbs*PoseScratch` и `IRLiteBbsCasterSource`.

| Изменение | Влияние на силуэт | Что сделано |
| --- | --- | --- |
| НОВОЕ `FormPoseEvents` (TRANSFORM, PARENT_FRAME, MODEL_POSE, CLAIM_CHAIN, PIVOT_OFFSETS, ANCHOR, ACTOR_BEFORE) — точки, куда аддон вешает свои вклады в позу | слушатель двигает трансформ, кости или якорь из СВОЕГО состояния, которого нет ни в одной нашей сигнатуре — заявленный сценарий это физический аддон, шагающий симуляцию каждый кадр | новый гейт: любой зарегистрированный слушатель → UNKNOWN для всех кастеров |
| `Form.additionalOverlays` + `syncOverlayTracks()` — треки pose-оверлеев из настроек записи | `MobFormRenderer` теперь складывает их в ОДНУ позу и пушит её (`MobRenderContext.push(rig, combined, null)`); наш mob-пробник пушил `pose` и `poseOverlay` по отдельности и не видел оверлеи | пробник повторяет `getCombinedPose()`: `syncOverlayTracks()` + `MobPoseApplier.merge(pose, poseOverlay, additionalOverlays)`, overlay = null |
| cubic-путь: `ModelFormRenderer.getPose()` сам складывает `additionalOverlays` | наш пробник зовёт настоящий `getPose()`, а сигнатура позы берётся с ВЫЧИСЛЕННЫХ групп | покрыто без правок; добавлен регресс-кейс в самотест |
| `ModelConfig.proceduralBones` + `ModelInstance.getProceduralBones()` (процедурные кости) | работают только при `isProcedural()`, а он уже гейт; плюс аниматор обязан быть ровно `Animator` | без изменений, добавлено как маркер layout |
| `Model.shiftGroup/setTextureSize/regenerateQuads`, `ModelGroup.generateQuads`, `ModelCube.hiddenUVs/name/shift`, `ModelUV.flipX/flipY/rotate90`, `ModelInstance.rebakeGroups` — редактор моделей правит геометрию НА МЕСТЕ | CPU-путь: сигнатура геометрии и так читает живые `cube.quads` (вершины, нормали, uv) и `group.initial` каждый кадр; VAO-путь: `rebakeGroups` удаляет и заново заливает VAO, а на `upload/delete` висит ревизионный миксин | без изменений |
| `FormRenderer.createEvaluatedTransform` = `createTransform()` + событие TRANSFORM | сам `createTransform()` читает ровно то, что мы хешируем (transform + overlay + additionalTransforms) | покрыто гейтом событий |
| `ModelPhysicsCache` (CLAIM_CHAIN), `ModelPivotFrames` (PIVOT_OFFSETS), `FilmMatrices` (ANCHOR), `FilmEntityRenderer` (ACTOR_BEFORE) | те же события | тот же гейт |
| `CubicModelAnimator` — прогресс интерполяции через `segment.getInterpolationProgress(true)` | вычисление анимации, пробник зовёт настоящий путь | без изменений |
| `FormRenderer` render-last / `pickable`, `RenderAttachment` | когда и чем рисуется, не какой силуэт | без изменений |
| `Transform`, `Pose`, `PoseTransform`, `Texture`, `ModelVAO`, `ModelGroup.offset` | не менялись | layout-проверка прежняя |

Ревизии считаются только для кастеров типа MODEL_BLOCK (`IRLiteBbsCasterSource.revision`),
поэтому дрейф плёночного пути (FilmPlayerPose, FilmMatrices) на них не влияет.

## 2. Гейт слушателей поз

Fabric держит слушателей события в приватном массиве, длина которого — единственный способ
спросить «есть ли кто-нибудь на этом событии». `BbsSilhouetteBridge.poseListeners()` читает
её у всех семи событий один раз за вызов ревизии; `-1` (прочитать не удалось) делает версию
НЕ аудированной целиком, а не молча считает событие пустым. Сам BBS в эти события только
инвокает, так что на чистой сборке значение 0.

## 3. Правки

- `BbsSilhouetteBridge`: `POSE_EVENTS` + `EVENT_HANDLERS` + `poseListeners()`; маркеры 2.7
  (`ModelInstance.getProceduralBones`, `Form.additionalOverlays`); в `audit()` добавлено
  требование читаемого зонда и версии `2.7-1.20.1` / `2.7-1.20.4` с проверкой layout.
- `BbsModelSilhouette`, `BbsMobSilhouette`: гейт `poseListeners() != 0 → UNKNOWN`.
- `BbsMobSilhouette`: пробник пушит объединённую позу, как это делает 2.7.
- `BbsMobSilhouetteChecks` (PASS 11 → 14), `BbsModelSilhouetteChecks`: кейс «трек pose-оверлея
  меняет сигнатуру» (через `BBSSettings.recordingPoseOverlays`, с восстановлением) и проверка,
  что зонд слушателей читается и пуст.

Байткод-чекер ссылок из спеки 2.5.2 не нужен: там сборка компилировалась против 2.3.1, а
работала на 2.5.2; здесь каждая линия MC компилируется против своего jar BBS 2.7, и это
проверяет компилятор.

## 4. Проверки

- `caster-revision bridge: bbs 2.7-1.20.4 audited` и `bbs 2.7-1.20.1 audited` в dev-клиенте.
- `caster-revision-checks: PASS 14` (mob-путь, villager MobForm, мир Flat, BBS 2.7-1.20.4) —
  включая новый кейс pose-оверлея и зонд слушателей.
- Сборки 1.20.1 и 1.20.4, `verify-profiles-bbs` (2362), `ReplayPortApiCheck` (19),
  `verify-spot-guide` (39), `verify-replay-port.py` — PASS после правок.

## 5. Открыто

1. **Cubic-самотест на 2.7 не прогонялся**: в тестовых мирах (`Flat`, `Room`) сейчас нет блока
   с cubic ModelForm — сэмплер cubic просто не доходит до известной ревизии. Нужен один блок с
   моделью вроде `mobs/chair` в мире, дальше проверка запускается сама
   (`-Dirlite.checkCasterRevisions=true`).
2. **Ин-гейм подтверждение выигрыша**: счётчики `caster.known` / `sp.reuse` и `gpu bake` на
   статичной сцене — как в §9 спеки 2.5.2.
3. **Линия 1.21.x не аудирована и на 2.7**: `port/1.21.1` получил те же правки (гейт событий,
   объединённая поза, кейсы самотеста), но список аудированных версий там намеренно пуст —
   у 1.21.x другой ванильный рендер и Iris, и своего силуэт-аудита эта линия никогда не
   проходила. `port/1.21.11` вообще не содержит силуэт-сэмплеров. Аудит 1.21.x — отдельная задача.
