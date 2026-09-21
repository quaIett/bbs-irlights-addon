# Порт аддона на BBS FS 2.7 (все четыре MC)

Этап 1 — совместимость: `master` (1.20.1 / 1.20.4), `port/1.21.1`, `port/1.21.11`. Миграция
остальных точек на Addon API вынесена в этап 2 (список ниже).

## Источник BBS 2.7

- `bbs-fs-2.7-1.20.1-build` — worktree на `origin/1.20.1` (998c9d585, «Merge branch 'dev' into 1.20.1»).
- `bbs-fs-2.7-1.20.4-build` — worktree на 13bd62dab; после релиза это же состояние лежит в `origin/master`.
- `bbs-fs-2.7-1.21.1-build` — `origin/1.21.1` (d697ac7db), `bbs-fs-2.7-1.21.11-build` — `origin/1.21.11`
  (9fb7f936a, эта ветка ушла на один коммит дальше остальных).
- Собранные `libs/bbs-2.7-<mc>.jar` (+ sources) — на них переключён селектор `-Pmc` в `build.gradle`
  каждой ветки. На `port/1.21.1` перевязаны и кросс-проверочные 1.20.1/1.20.4: код теперь требует
  API 2.7 и против 2.6 не соберётся.

## Что сломалось в 2.7 и как починено

| Слом | Решение |
| --- | --- |
| `UIReplaysEditor.ReplayCategory` (enum) удалён — вкладки стали контрактом API | вкладка «Light» регистрируется в `RegisterTrackCategoriesEvent` как `TrackCategory("irlights:light")`; миксины `ReplayCategoryMixin` и `ReplayCategoryInvoker` удалены, BBS сам строит кнопку, цифровой шорткат и правило «показывать, пока есть такие треки» |
| `TrackKind.BODY_PART` и `TrackId.bodyPart` удалены вместе со строками-заголовками частей тела | группы треков лампы переведены на `UIKeyframeSheet.Section` (2.7): id `irlights_section/<путь формы>/<группа>`, секция ставится в `UIKeyframeSheetMixin` при создании строки |
| `UIKeyframes.getAutoKeyframeTick()` теперь `Float` (дробные тики) | `UIReplaySelectionKeyframeFactory` читает `Float` |
| `BBSSettings.recordingPoseTransformOverlays` разделён на `recordingPoseOverlays` + `recordingTransformOverlays` | обновлены `ProfilesBbsTest` и `SpotGuideKeyframesTest` |

Правило вкладки: BBS спрашивает «чей это трек?» одним `TrackId`, а свойства лампы называются
как всегда (`color`, `intensity`...), поэтому по адресу это не решить. `LightTrackLayout.decorate`
запоминает адреса треков ламп того каталога, который BBS построила последним, и правило читает
этот список — каталог таймлайна всегда строится непосредственно перед раскладкой треков по вкладкам.

Миксин `UIReplaysLightCategoryMixin` заменён на `UIReplaysLightFoldsMixin`: от него осталось только
раскрытие главных секций при первом показе таймлайна.

## Проверки (все четыре версии MC)

- `gradlew build`: PASS на 1.20.1, 1.20.4, 1.21.1, 1.21.11 (перед 1.21.x — `publishToMavenLocal`
  соответствующей ветки core; в конце mavenLocal возвращён на линию 1.20.x, bytecode major 61).
- `verify-profiles-bbs.ps1`: 2362 проверки на каждой версии PASS.
- `ReplayPortApiCheck`: 19 bytecode-анкеров на версию PASS (анкеры перевязаны на
  `TrackCategories`/`TrackCategory`/`RegisterTrackCategoriesEvent`, `UIKeyframeSheet.section` и
  конструктор `UIKeyframeSheet(TrackDescriptor)`).
- `verify-spot-guide.ps1`: 39 проверок на версию PASS.
- `verify-replay-port.py`: вложенный core, 7 патчей, mixin-классы PASS на всех четырёх.
- `runClient` на BBS 2.7: 1.20.4, 1.21.11 и 1.21.1 стартуют без единой ошибки миксинов
  (Iris + Sodium, Sound engine started); тестовые клиенты закрыты через CloseMainWindow.
  Известный шум, не дефект: отсутствие `categories.json` в тестовом профиле BBS и нерезолвящиеся
  штатные uniform'ы CR (`BIOME_PALE_GARDEN`, `endFlashIntensity`) на 1.21.1. Игровая приёмка — за
  пользователем.

## Открыто

1. **BBS Refreshed UI (`refreshedui` 1.2.0-fs2.6) не грузится на 2.7**: его собственный
   `UIReplaysEditorMixin` ссылается на удалённый `ReplayCategory` и роняет клиент на этапе APPLY.
   Это отдельный мод (не standalone-редактор IRLights: тот от BBS не зависит и объявляет
   `breaks: bbs`). Из `run/mods` он убран в `run/mods-disabled`; его порт — отдельная задача,
   возвращать jar в `run/mods` только по слову пользователя.
2. **Кастеры**: на master силуэт-аудит 2.7 сделан (`docs/bbs-2.7-caster-audit.md`, мост пишет
   `audited` на 2.7-1.20.1/1.20.4). Линия 1.21.x не аудирована намеренно — своего аудита она не
   проходила и на 2.6, ревизии там UNKNOWN и бейк полный.
3. **Этап 2 — переезд на Addon API** там, где 2.7 уже даёт контракт, а мы всё ещё в миксинах:
   регистрация форм (`BBSModMixin` → `RegisterFormsEvent`), рендереров и панелей
   (`FormUtilsClient.<clinit>`, `UIFormEditor.<clinit>` → `RegisterFormRenderersEvent`,
   `RegisterFormEditorsEvent`), локализация (`L10nMixin` → `RegisterL10nEvent`), раздел палитры
   (`ExtraFormSectionMixin` → `RegisterFormSectionsEvent`), тег отрисовки формы
   (`FormUtilsClientMixin` redirect → `FormRenderEvents.BEFORE/AFTER`). Реестры BBS больше не
   заполняются в статических инициализаторах, так что наши `<clinit>`-инъекции работают только
   потому, что `setup()` ничего не очищает.
4. Игровая приёмка порта на всех четырёх версиях — за пользователем.
