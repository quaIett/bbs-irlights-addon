---
name: plan-settings-ux-and-ubo-migration
description: "UX-настроек: этапы 1-2 DONE 2026-07-21 (чистка мёртвых Iris-опций CR + IRLights = свой BBS-модуль с пресетами). План след. сессии: перенос оставшихся 21 из 24 шейдерных опций в UBO, 3 волны. БЛОКЕР: dev-пак CR разошёлся с Modification/патчем по W2."
metadata:
  node_type: memory
  type: project
  originSessionId: 55fd9707-761f-46e3-8398-c64bb853c211
  modified: 2026-07-21T00:11:26.258Z
---

# UX настроек IRLights + перенос шейдерных опций в UBO

Старт 2026-07-21 (подтверждённое переключение с перф-трека `optimization/octahedral-point-shadows`, все задачи ветки закрыты). Работаем ТОЛЬКО на Complementary Reimagined в дев-клиенте (директива юзера). Тираж на 6 остальных паков — не в этой задаче.

Макет-канон: `docs/irlights-settings-ux-v2.html` (untracked).

## СДЕЛАНО 2026-07-21 (не переделывать)

### Этап 1 — чистка мёртвых Iris-опций CR (ЗАКРЫТ)
Находка grep'ом: 9 VL-слайдеров в экране Volumetric объявлены `#define`, зарегистрированы в UI и **0 раз читаются в GLSL** — значения идут из UBO. Плюс `IRLITE_VL_INTENSITY` читается только как fallback при незабинденном UBO, а `IRLITE_VL_SHADOWS`/`_NOISE` — компиль-гейты, чей реальный вкл/выкл решает бит `vlC.w`.
Снято из UI 12 опций (не из `#define` — они остаются как fallback): `VL_STEPS`, `_MAX_DIST`, `_TIP_BOOST`, `_TIP_RADIUS`, `_SHADOW_STRIDE`, `_NOISE_AMOUNT`, `_SCALE`, `_SPEED`, `_STRIDE`, `_INTENSITY`, `_SHADOWS`, `_NOISE`. Экран Volumetric = 2 опции (`IRLITE_VOLUMETRIC` + `IRLITE_VL_RESOLUTION`).
Правки: `Shadres/Modification/ComplementaryReimagined/shaders/{shaders.properties,lang/en_US.lang}`; страж в `tools/gen-complementary-patch.ps1:80` перевешен со снятой опции на `IRLITE_TOON_BANDS`. Патч отреген (21 ops, 2154 строки), byte-proof ПУСТОЙ, dev-пак `run/shaderpacks/ComplementaryReimagined_IRLights` синхронизирован (md5 сверен).

### Этап 2 — IRLights = свой модуль настроек BBS (ЗАКРЫТ)
Было: 2 категории, навязанные модулю `bbs` через `BBSSettingsMixin`. Стало: собственный модуль через `RegisterSettingsEvent` (образец в дереве — `bbs-irl-camera`).
- `src/main/java/qualet/irlite/IrlightsAddon.java` — `BBSAddonMod` + `@Subscribe`, иконка `Icons.LIGHT`, 4 категории: `presets` / `volumetric` / `shadows` / `patcher`. Entrypoint `"bbs-addon"` в fabric.mod.json.
- `BBSSettingsMixin` УДАЛЁН (и из `irlite.mixins.json`).
- Ключи L10n: `bbs.config.irlite.*` -> `irlights.config.<категория>.<value>` (префикс = id модуля, `UIValueFactory.getValueLabelKey`). Снят суффикс «(live)» — он был нужен только чтобы отличать ручку от мёртвого Iris-двойника.
- `UISettingsOverlayPanelMixin` — врезка на категории `presets`/`patcher` + ГЕЙТ по `this.settings.getId()=="irlights"` (id категорий уникальны только внутри модуля).
- Миграция: файл модуля = `config/bbs/settings/irlights.json`; кросс-модульной миграции в BBS НЕТ, старый блок `irlite` в `bbs.json` затирается при следующей записи. Реализовано «прочитать легаси-блок и подставить как дефолты билдера», guard = наличие категории `volumetric` в своём файле (НЕ факт существования файла: нашёлся огрызок `irlights.json` от 2026-06-03 с одним `shadow_quality`, на нём наивный guard давал ложное «уже мигрировано»). Отработало, значения юзера доехали.

### Пресеты (ЗАКРЫТ)
`src/main/java/qualet/irlite/IrlitePresets.java` + `src/client/java/qualet/irlite/client/ui/presets/UIPresetSection.java` (два `UICirculate` сразу под заголовком секции через `options.addAfter`).
КЛЮЧЕВОЕ РЕШЕНИЕ: пресет НИГДЕ НЕ ХРАНИТСЯ — индекс вычисляется из самих ручек при каждой отрисовке (`quality()`/`style()`). Правило «тронул ползунок -> Custom» получается само, без флагов/коллбэков и без второго источника правды. Побочно снят риск рекурсии (программный `set()` дёргал бы тот же коллбэк, что ручная правка).
- Ось Quality: Performance / Balanced / Quality / Ultra / Custom. Члены: `vl_steps`, `vl_max_dist`, `vl_shadow_stride`, `vl_noise_stride`, `vl_shadows_live`, `shadow_quality`, `shadow_blocks`. Balanced == зарегистрированные дефолты (чистая установка сразу на именованном пресете).
- Ось Beam style: Clean / Dusty / Smoky / Custom. Члены: `vl_noise_live`, `vl_noise_amount`, `vl_noise_scale`, `vl_noise_speed`, `vl_tip_boost`, `vl_tip_radius`. Dusty == дефолты. Smoky = amount 1.0 / scale 0.5 / speed 3.0 / tip 2.0-2.0 (значения юзера 2026-07-21).
- НИ ОДИН пресет не трогает: `max_shader_lights` (дефолт 0 = без лимита) и `vl_noise_morph` (дефолт 0) — прямая директива юзера. Ни один не ставит `shadow_quality=ULTRA` (4096 на point-слои ~4.6 GiB = VRAM-коллапс, чинили 2026-07-19).
- Клик по позиции Custom = no-op (восстанавливать нечего); панель перестроится и метка вернётся к совпавшему пресету.
- Аккордеона/сворачиваемых секций во фреймворке BBS НЕТ (проверено: ни `UIFoldable`, ни `UICollapsible`; в самом BBS сворачивание дважды написано руками). Секция «Дополнительно» из макета НЕ реализована — после разбивки на категории, возможно, не нужна.

### Дебаг-секция (ЗАКРЫТ)
Стресс-тест удалён с корнем: кнопка, `client/diag/StressTestLights.java`, вызов `emit()` из `LightCollector`. `UIStressTestSection` -> `UIDebugSection`, единственная кнопка = Show/Hide performance overlay.
Потребовало сделать `VlProfiler` переключаемым в рантайме: `ENABLED` был `static final` (решался при загрузке класса) -> живое `volatile` поле, `-Dirlite.profileVl` = только стартовое состояние. ГОТЧА: переключение применяется на границе кадра, в начале `frameTick()`, ДО его собственного гарда — флип посреди кадра оставил бы незакрытый `GL_TIME_ELAPSED` (`endPass()` улетел бы в ранний return мимо закрытия), а применение после гарда никогда бы не включило профайлер обратно. `HudRenderCallback` + bake-probe теперь регистрируются всегда, гасятся изнутри; `renderHud` получил собственный гард.

## P0 W2-расхождение — ЗАКРЫТ 2026-07-21 (сессия 11)

**Вердикт: канон = СОДЕРЖИМОЕ dev-пака, канон-МЕСТО = Modification.** Мод уехал вперёд первым, шейдерная сторона не догнала (не наоборот): `b18a0c1` — предок HEAD irl-core, `WIDE_WORDS=64` пишется безусловно каждый кадр, и опубликованный в mavenLocal `irl-core 1.1.3`, на который аддон уже слинкован, **уже содержит W2**. Направление процесса при этом обратное (sync-workflow: правки в Modification -> gen-скрипт -> патч -> dev-пак), поэтому выравнивание = forward-port содержимого dev -> Modification.

Сделано: `cp` dev -> Modification (файл `lib/irlite/irlite_lights.glsl` целиком, 1714 -> 1769 строк), реген `tools/gen-complementary-patch.ps1` (21 op, 2154 -> 2209 строк), двойной byte-proof ПУСТ (patched-output vs Modification И vs dev-пак — все три стороны байт-идентичны). Диффа патча ровно 70/-15 = диффу GLSL, порт изолирован.

**Поправка к прежней оценке серьёзности: легаси-путь FAIL-OPEN, не fail-closed.** Строка `if (i < 64u && (irliteClusterMask[i>>5u] & ...) == 0u) continue;` — для `i >= 64` первый конъюнкт ложен, `&&` схлопывается, `continue` НЕ выполняется, лампа обрабатывается. Маска тайла — консервативный ускоритель поверх настоящих реджектов (dist2 >= r², ray-sphere/cone). Юзер со старым патчем получал КОРРЕКТНУЮ картинку, терялся только culling; мод при этом лил 147456 Б/кадр (97% SSBO) впустую. Severity = PERFORMANCE, не CORRECTNESS. Формулировка «тихая потеря cluster-cull» была верна, статус «красная» — завышен.

Перенесено ТРИ изменения, не два (третье в плане не значилось):
1. W2 `irlite_clusterWide[]` + `irlite_clusterWideBase()` x2 + word-walk в обеих петлях. Границы точные: max index `575*64+63 = 36863` = ровно последний элемент, нулевой запас, нулевой over-read. Обратная совместимость закрыта в ОБЕ стороны: новый пак + старое ядро -> `header.w == 0` -> гейт `irlClusterOn` ложен -> полная петля (fail-open); новое ядро + старый пак -> легаси-регион всё ещё dual-written (`ClusterGridBuffer.setTileBit` пишет `wide[]` ВСЕГДА, `maskX/maskY` дополнительно при bit<64).
2. `readonly restrict` на SSBO binding 7 (P5). Решение юзера 2026-07-21: переносить ДОСЛОВНО. Обоснование против опасения «GLSL 4.20 токен на #version 130»: `readonly` уже отгружен на binding 6 в боевом патче и компилируется у юзеров, а `restrict` — из того же production memory-qualifier'ов того же `GL_ARB_shader_storage_buffer_object`; драйвер, парсящий один, парсит и второй.
3. **posRadius pre-fetch** (`vec4 irlPosRadius = irlite_lights[i].posRadius;` до range-reject — 16 Б вместо 96 Б на отбракованную лампу). ТОЛЬКО в surface-петле; `irlite_volumetric` по-прежнему грузит структуру целиком. Асимметрия НАМЕРЕННО перенесена дословно — «гармонизировать» VL-петлю под видом синка = протащить непроверенное изменение.

`uvec2 irlite_clusterMasks[576]` фиксированного размера — ОБЯЗАТЕЛЬНО, не стиль: std430 разрешает один безразмерный хвостовой массив, и его слот занял wide-регион. W2 не черри-пикается половинами.

Хвосты после закрытия (мелкие, не блокеры):
- Два оверлоада `irlite_clusterMaskFetch()` стали мёртвым кодом (0 call sites) — читаются как живой API и введут в заблуждение следующего читателя.
- `run/irlite/patches/` — ЧЕТВЁРТАЯ копия патчей (14 файлов), CR-копия отстаёт. Это jar-extracted кэш с refresh-on-mismatch (`PatchLibrary`), самовылечится при пересборке — руками НЕ править.
- W2-Java живёт только на локальной непушнутой ветке `optimization/octahedral-point-shadows` ядра; `main` ядра = `2e57f8d`, `WIDE_WORDS` там нет вовсе. При директиве «чиним только main/master» W2 на main не существует.
- Сообщение коммита `b18a0c1` само себе противоречит: рекламирует A/B через тумблер `shader_light_clustering`, удалённый через 66 минут коммитом `95ba5bb`, и всё ещё несёт маркер «RED LINE: experimental zone».
- `BBS/VERSION` = 1.1.2, при `irl_core_version=1.1.3` в gradle.properties — файл протух, в «5 мест бампа» не входит.

## ВОЛНА 3 — DONE 2026-07-21 (сессия 11, НЕ закоммичена): shadows trim по просьбе юзера
Запрос: «оставить только вкл/выкл + размытие». Экран Iris по теням 5 -> 1 опция.
- Осталась на экране Iris только `IRLITE_SHADOWS` — как компайл-тайм escape hatch (гейтит `samplerCubeArray`, «the one construct a strict #version 130 driver could reject»). Коммент переписан: «Compatibility escape hatch, not the normal on/off».
- Живые ручки BBS в существующей категории `shadows`: `shadows_live` (bit13) + `shadow_softness` (`vlF.z` — свободный слот, UBO НЕ рос).
- `IRLITE_SHADOW_QUALITY`/`_BIAS`/`_NORMAL_OFFSET` захардкожены на дефайнах пака (2/0.05/0.05), сняты с экрана, аннотации `// [..]` СРЕЗАНЫ (иначе персистентное значение из `<pack>.txt` осталось бы живым — тот же класс, что outline в волне 1). У юзера в .txt были QUALITY=0/BIAS=0.01/OFFSET=0.02/SIZE=0.20 -> после хардкода перестают применяться, тени визуально изменятся (предупреждён).
- QUALITY читается ТОЛЬКО препроцессором (`#if ==1/==2/>=4`) — выбор между 4 телами кода, не значение. Мигрируем -> потеря развёртки blocker-цикла. Хардкод её отменяет.

### Ревью волны 3 поймало (9 подтв., 15 откл.):
1. **[baseline у меня был неверен в голове]** У юзера в .txt НЕ было QUALITY-оверрайда для пилота (`grep`: только `IRLITE_SPECULAR=false`), но БЫЛИ для других shadow-опций. Пилот видит ноль изменений QUALITY, но SIZE/BIAS/OFFSET-оверрайды перестают применяться.
2. **[minor, обратная совместимость — МОЙ баг] bit13 полярность.** Изначально bit13=«shadows ON». Committed wave-1 ядро (78c31b9) пишет bit7 (valid), но НЕ bit13 -> новый пак на нём читал бы «valid=1, shadow-bit=0» -> тени молча ВЫКЛ. Урок: valid-бит вручает «блок записан», НЕ «это поле записано». ФИКС: инвертировал -> bit13=«DISABLED», незаписанный бит=0=«не выключено»=fail-safe ON. Макрос `IRLITE_SHADOWS_LIVE = (!IRLITE_GLOBALS_OK || (vlC.w & 8192u) == 0u)`, Java `shadowFlags = enabled ? 0 : 1<<13`, дефолт поля 0.
3. **[moderate — САМОЕ ВАЖНОЕ, юзер подтвердил «гейтить бейк»] тумблер был чисто визуальный.** Гасил сэмплирование в шейдере, но НЕ запекание (13.5-17.8 мс + ~1.6 ГБ шли всегда). ФИКС: `ShadowConfig.shadowsEnabled()` (опц. `default true`, как `shadowPoseReach`) + гейт в `ShadowBaker.bakeInner` сразу после world/cam-нуль-чека, переиспользует путь освобождения ветки `count==0` (resetTileState + drain кэшей + retainOnly). Shim `IrliteShadowConfig`: `shadowsEnabled(() -> shadowsLive() || vlShadowsLive())` — ИНАЧЕ выключение surface-теней утащило бы карты у VL (лучи сэмплируют тот же атлас через bit0). Трогает ОБЩИЙ irl-core -> все 3 мода (редактор не задет: не зовёт setShadow, `shadowsEnabled()` у него = default true).
4. Мелочи: неверный коммент «QUALITY нельзя перенести» -> «можно, но потеря развёртки»; устаревший «VL defines на экране»; 5 пустых строк в lang схлопнуты.
5. Отклонено 15, в т.ч.: потеря тира «Hard» (QUALITY 0) = осознанное следствие запроса юзера, не баг; редактор на committed ядре — default true; NaN-clamp в setShadow — не критично.

### ОТКРЫТО после волны 3:
- **Тумблер выключает бейк только когда И surface, И volumetric тени выкл** (гейт = OR). Задокументировано в tooltip.
- Потеря тира «Hard» (1-тап) — если юзер захочет дешёвые жёсткие тени, вернуть отдельным битом/сентинелом (fix в находке #1 ревью).

## ЭКРАН IRIS СПЛЮЩЕН 2026-07-21 (сессия 11): финальная правка по просьбе юзера
4 подэкрана (Specular/Shadows/Toon/Volumetric — по 1-3 опции каждый) схлопнуты в один плоский `screen.IRLIGHTS`, 2-колоночная раскладка: тумблер фичи + её слайдер в строку, toon-группа вместе, `IRLITE_SHADOWS` escape-hatch внизу отдельной строкой. Удалены 4 мёртвых lang-заголовка подэкранов. Только раскладка пака — ноль GLSL/Java. Коммит `2e8ef25`.
На плоском экране осталось 10 опций = кандидаты волны 2 (`INTENSITY`, `SPECULAR_INTENSITY`, `TOON_*`) + структурные непереносимые (`VOLUMETRIC` грузит пак, `VL_RESOLUTION` аллоцирует таргет, `SHADOWS` гейтит samplerCubeArray, `DIFFUSE`/`SPECULAR` тумблеры surface-пути). После волны 2 экран ужмётся ещё.
Готча генератора: `$propsScreens = $pr[($X+1)..($O-1)]` берёт ВСЕ строки между анкерами `PIXELATED_LIGHTING_SETTINGS` и `OTHER_SETTINGS` как IRLITE-экраны — схлопывание 5 строк в 1 подхватилось само, анкеры не двигались.

## КОММИТЫ СЕССИИ 11 (2026-07-21)
Аддон (ветка `optimization/octahedral-point-shadows`): `987e1a5` P0 W2 -> `c0280f9` фикс профайлера -> `6f2815d` волна 1 outline -> `c5cac4b` волна 3 shadows trim -> `2e8ef25` плоский экран Iris. Ядро (та же ветка): `78c31b9` UBO 64->96 + setOutline() -> `ef76cdb` setShadow + bake-gate (ShadowConfig.shadowsEnabled + ShadowBaker).
Приём расщепления коммитов подтверждён снова: byte-proof-выходы (`w3b` = волна3 без flatten, `flat` = с flatten) = машина времени, откатил экран к w3b, регенерил патч, закоммитил trim, восстановил flat, закоммитил передизайн.
История разложена на 3 коммита намеренно: патч в рабочем дереве нёс P0+волну вместе, но оба byte-proof-выхода были сохранены, поэтому Modification откатывался к P0-состоянию, патч регенерился, и только потом восстанавливалась волна. Приём годится и впредь: **промежуточные выходы PatchHarness = машина времени для расщепления коммитов** по gitignored-источнику.

## ВОЛНА 1 — ЗАКРЫТА 2026-07-21 (сессия 11): outline, 10 опций в UBO

Экран Iris: 24 -> 14 опций. Все 10 `IRLITE_OUTLINE*` стали живыми ручками BBS (категория `outline`, `Icons.OUTLINE`, пятая в модуле) и применяются БЕЗ перезагрузки шейдеров. Подтверждено юзером в игре.

**UBO расширен в хвост**: `CAPACITY 64 -> 96`, `vec4 vlE` (strength / fresnelPower / back / frontStrength) + `vec4 vlF` (glowStrength / pixelSize / 2 reserved). Оффсеты 0..63 не двигались.

**Бит «globals valid» = bit7 (128u)**, ставится в `VlGlobalsBuffer.upload()` как `flags | outlineFlags | FLAG_GLOBALS_VALID` — НЕ в сеттере, чтобы его физически нельзя было забыть. Незабинденный UBO читается нулями -> бит ложен -> шейдер падает на `#define`-фоллбэки. Биты: 8 outline, 9 front, 10 glow, 11-12 target (2-битное поле).

**Ключевое решение — outline-флаги в ОТДЕЛЬНОМ слове** (`outlineFlags`), а не в `flags`. Причина: `VlSweep.overrideVlGlobals()` пересобирает `flags` только из 7 VL-тумблеров, и при общем слове каждый прогон дев-профайлера тихо гасил бы обводку. Склейка — в `upload()`. Следствие: `setOutline()` НЕ надо зеркалить в `VlSweep`, в отличие от `set()`.

`oBack`/`oFrontS`/`oStrength` вынесены ИЗ цикла по лампам (инвариантны), ветка `#ifdef IRLITE_OUTLINE_FRONT` схлопнута в `rim = oBack*(1-ndl) + oFrontS*ndl` (при front off `oFrontS==0` -> тождественно). Это частично компенсирует потерю препроцессорной вырезки.

Дефолт `outline_pixel_size` = **6** (решение юзера по виду), синхронно в 4 местах: поле ядра, геттер `IrliteConfig`, билдер `IrlightsAddon`, `#define` в GLSL.

### МИНА №2 ИЗМЕРЕНА — оказалась мелкой
Замер A/B в ОДНОЙ сессии через F3+R (Java не тронута -> bit7=0 -> картинка идентична, изолирован чистый эффект компиляции): `composite1` 0.291 -> 0.319 ms (**+9.6%**) при фоне дрейфа 1.6-4.4% на контрольных проходах -> реальный сигнал ~+6%, т.е. **сотые доли мс**, ~0.1% кадра при 60 fps.
ПОЧЕМУ ТАК МАЛО: `IRLITE_OUTLINE` был `#define`-нут ПО УМОЛЧАНИЮ, значит блок теней в composite1 компилировался и ДО миграции — вырезка почти ничего не убирала. Реальная цена пришла от неконстантной экспоненты `pow()` и неконстантного tap-оффсета `ps`, а не от исчезнувших `#ifdef`.
ЗАМЕР БЫЛ ЗАГРЯЗНЁН: GLSL объявлял 96 байт, пока Java ещё аллоцировала 64 -> привязка блока к меньшему буферу = UB, драйвер платит bounds-check. Видно по тому, что выросли ровно `composite1` и `deferred2` — единственные два прохода, включающие `irlite_lights.glsl`. При повторе мерить ПОСЛЕ бампа CAPACITY.

### ГЛАВНАЯ НАХОДКА РЕВЬЮ (была бы major-багом у юзеров)
`#define IRLITE_OUTLINE` + голый `#ifdef IRLITE_OUTLINE` = Iris РЕГИСТРИРУЕТ булеву опцию, и применяет её персистентное значение из `<pack>.txt` **независимо от того, есть ли она на каком-либо экране** (экраны рулят только GUI). Сняв опцию со всех экранов, но оставив `#ifdef`, мы получали: у юзера, который до миграции выключил обводку, лежит `IRLITE_OUTLINE=false` -> Iris комментирует `#define` -> вырезается ВЕСЬ outline вместе с новой BBS-категорией из 10 ручек, и вернуть его из UI НЕЧЕМ.
Доказательство персиста — в самом пилотном паке: `run/shaderpacks/ComplementaryReimagined_IRLights.txt:11` содержит `IRLITE_SPECULAR=false`.
ФИКС: `#define IRLITE_OUTLINE` удалён, `#ifdef IRLITE_OUTLINE` снят (внешний `#ifdef COMPOSITE1` уже был), дизъюнкт `&& defined IRLITE_OUTLINE` убран из гейта `IRLITE_COMPILE_SHADOWS`, `composite1.glsl` -> `#ifdef IRLITE_ACTIVE`. Enable теперь ТОЛЬКО bit8.
**ПРАВИЛО НА БУДУЩИЕ ВОЛНЫ: снимая булеву опцию с экрана Iris, ОБЯЗАТЕЛЬНО убирать и её голый `#ifdef`, иначе персистентное значение остаётся живым и неотзываемым.** Численные опции (`// [1 2 3]`) этим не страдают — они читаются как значения, а не гейтят код.

### Прочее из ревью (исправлено)
Мёртвые lang-строки outline убраны (25 строк) — прецедент этапа 1: он свои чистил. ГОТЧА: после удаления строк остались хвостовые пустые, а генератор их срезает (`while ($langTail[-1] -eq '')`) -> byte-proof упал на 10 пустых строках. Хвост чистить.
Два анкера генератора пришлось перевесить (оба честно споткнулись, tripwire работает): `sliders`-хвост `IRLITE_OUTLINE_GLOW_STRENGTH` -> `IRLITE_TOON_SMOOTH`; `$OStart` в composite1 -> `    #ifdef IRLITE_ACTIVE` (проверена уникальность).
Отклонено 8 находок, в т.ч. «бит valid вручается за поля, которых вызывающий не писал»: редактор `irlights` зовёт `VlGlobalsBuffer.set()`, но НЕ `setOutline()` -> берёт дефолты ядра, а они выставлены зеркально дефайнам пака (0.65/2.2/1.0/0.3/0.12/6, outline on, front/glow off, target 1), плюс забандленные патчи редактора `IRLITE_GLOBALS_OK` не содержат вовсе.

### Побочный фикс: профайлер называл проходы «unnamed»
`VlProfiler.registerPassName` имел ранний `return` на `!enabled`, а зовётся из `createProgram` — на ЗАГРУЗКЕ пака, когда профайлер по умолчанию выключен. Итог: `PASS_NAMES` пуст, все композитные проходы схлопывались в ОДИН бакет `unnamed` (ключ статистики = имя), цифра бессмысленна. Регрессия сессии 10 (рантайм-переключение `ENABLED`; при `static final` + `-Dirlite.profileVl=true` имена регистрировались). Гейт снят — это несколько `WeakHashMap.put` за загрузку пака.

## ПЛАН: перенос оставшихся шейдерных опций в UBO

Цель: экран Iris с 24 опций -> 3. Механизм тот же, что уже отработан на VL: std140 UBO binding 7 `IrliteVlGlobals`.

### Что переносится (21 из 24) — классификация проверена по коду 2026-07-21
- **Чистые скаляры (13)**, класс A, тривиально: `INTENSITY`, `SPECULAR_INTENSITY`, `SHADOW_SIZE`, `SHADOW_BIAS`, `SHADOW_NORMAL_OFFSET`, `TOON_BANDS`, `TOON_SMOOTH`, `OUTLINE_STRENGTH`, `OUTLINE_PIXEL_SIZE`, `OUTLINE_FRESNEL_POWER`, `OUTLINE_BACK`, `OUTLINE_FRONT_STRENGTH`, `OUTLINE_GLOW_STRENGTH`. Нужно +4 vec4 (`CAPACITY` 64 -> 128).
- **Тумблеры (6)** -> биты `vlC.w`, БЕСПЛАТНО по месту (занято 7 бит из 32, свободно 25): `DIFFUSE`, `SPECULAR`, `TOON`, `OUTLINE`, `OUTLINE_FRONT`, `OUTLINE_GLOW`.
- **`OUTLINE_TARGET`** — сейчас `#if` в `composite1.glsl:317,321`, становится рантайм-сравнением materialMask.
- **`SHADOW_QUALITY`** — с оговоркой, см. ниже.

### Что НЕ переносится (3) — окончательно
- `IRLITE_VOLUMETRIC` — гейтит `program.world0/deferred2.enabled` в shaders.properties (директива Iris, загрузка пака).
- `IRLITE_VL_RESOLUTION` — `size.buffer.colortex10` (аллокация рендер-таргета).
- `IRLITE_SHADOWS` — гейтит ОБЪЯВЛЕНИЕ `samplerCubeArray` (`irlite_lights.glsl:153-156`), в коде помечено как «the one construct a strict #version 130 driver could reject». Это аварийный выход по совместимости с драйвером, снимать нельзя.

### Проверенные факты (не перепроверять)
- UBO объявлен в `irlite_lights.glsl:102-114`, std140 + два `#extension` (`GL_ARB_uniform_buffer_object`, `GL_ARB_shading_language_420pack` — пак на `#version 130`). Гейтится только `IRLITE_ACTIVE`, НЕ по проходу -> виден во ВСЕХ программах: surface (`mainLighting.glsl:4-5`), composite1 (`:59`), deferred2 (`:41-42`). **В composite1 уже читается** (`:354` бит 64, `:360` `vlD.y`) -> outline-параметры кладутся в UBO без единой строки новой обвязки. Самый дешёвый кандидат.
- **PCF-цикл УЖЕ рантаймовый**: `int pcfN = (penTexS <= 3.0) ? NARROW : MID : TAPS` (`irlite_lights.glsl:657-661`, точка-близнец `:923`); `perm` тоже считается от `pcfN` в рантайме. Дефайны дают только константы. Единственная реальная потеря при переносе `SHADOW_QUALITY` — `for (int i = 0; i < IRLITE_BLOCKER_TAPS; i++)` (`:582`, точка-близнец `:848`): сейчас разворачивается, станет динамическим. 6-20 итераций blocker-search.
- `OUTLINE_PIXEL_SIZE` — НЕ граница цикла, а смещение тапа (`int ps` -> `ivec2 a = tc - ps`, 4 фиксированных сэмпла, `:983`). Класс A.
- Свободное место UBO: `vlD.z`/`vlD.w` (8 байт, пишутся 0), `vlD.y` (семантика есть — bilateral sigma, GLSL читает, Java-сеттера нет), 25 бит `vlC.w`. Потолка по vec4 нет (`GL_MAX_UNIFORM_BLOCK_SIZE` >= 16 КБ).
- Расширение UBO В ХВОСТ бинарно безопасно: std140-оффсеты 0..63 не двигаются, старый GLSL читает первые 64 байта увеличенного буфера. **Бампать `CONTRACT_VERSION` НЕ НУЖНО** — а если бампнуть, все 14 патч-файлов с `@irlite 1` немедленно перестанут применяться (`IrlPatchApplier.java:203-207`). Запрещено только переставлять существующие поля и биты 0-6.
- Заголовок SSBO `LightBuffer` расти НЕ может (std430 требует кратности 16 для `irlite_lights[]`; рост = сдвиг массива = ломает каждый выпущенный пак). Для новых опций строго UBO.
- Прямых `glUniform` из Java в проекте нет: Iris-интеграция даёт только `addDynamicSampler` (11 семплеров). `uniform.float.X` в shaders.properties вычисляется движком Iris, для конфига мода непригоден. **UBO — единственный канал.**
- Правило Iris: `#define` обязаны остаться в паке (голый `#ifdef X` = условие регистрации булевой опции). Двойных контролов не будет только потому, что мы снимаем опции из `screen.*` — этот приём отработан на этапе 1.

### Три мины (заложить в дизайн с самого начала)
1. **Сентинел-ноль не сработает.** Текущий fallback — `if (!(rt > 0.0)) rt = IRLITE_VL_INTENSITY` (`:1697-1698`), непривязанный UBO читается нулями. Но у 10 из 15 переносимых чисел ноль ЛЕГАЛЕН (`SPECULAR_INTENSITY`, `SHADOW_SIZE`, `SHADOW_BIAS`, `NORMAL_OFFSET`, `TOON_SMOOTH`, все `OUTLINE_*_STRENGTH`/`BACK`, `VL_NOISE_SPEED`) — юзер ставит 0, шейдер решает «данных нет». Решение: один свободный бит `vlC.w` = «globals valid», после чего сентинел не нужен вообще.
2. **Пропадает препроцессорная вырезка кода.** `#ifdef IRLITE_OUTLINE` сейчас физически убирает 5-тапный depth-edge детектор + Fresnel из composite1, `IRLITE_TOON` — бэндинг, `DIFFUSE`/`SPECULAR` — куски ГОРЯЧЕГО surface-пути. После переноса всё компилируется всегда и гасится рантайм-веткой. Кадр не дороже (ветка пропускается), но растёт register pressure -> может просесть occupancy. **МЕРИТЬ профайлером до/после, не предполагать.**
3. **Тихая деградация на старых паках.** Новые ползунки молча ничего не делают: ни ошибки, ни лога. Для 6 из 7 паков (все кроме CR) это состояние с первого дня — UBO там нет вовсе, объявлены `irlite_pad0/pad1`. Идиом на этот случай уже есть в L10n: «Only affects shaderpacks patched with runtime VL globals».

### Волны (пилот CR, тираж — отдельная задача)
1. **Outline (10 опций)** — UBO в composite1 уже подключён и читается. РЕВИЗИЯ 2026-07-21 (сессия 11): «самый дешёвый кусок» верно только для 8 из 10. Две опции — компат-аварийные выходы, той же природы, что уже исключённый `IRLITE_SHADOWS`:
   - **`IRLITE_OUTLINE`** входит ДИЗЪЮНКТОМ в master-гейт `samplerCubeArray`: `#if defined IRLITE_SHADOWS && (defined IRLITE_SURFACE_PASS || (defined COMPOSITE1 && defined IRLITE_OUTLINE) || ...)` -> `#define IRLITE_COMPILE_SHADOWS` (`irlite_lights.glsl:178-180` после порта W2). ВАЖНО: аварийный выход НЕ ломается — внешний конъюнкт `IRLITE_SHADOWS` остаётся `#define` (перенос запрещён), так что юзер на строгом драйвере всё так же убирает `samplerCubeArray` целиком. Реальная цена переноса уже: при shadows=ON и outline=OFF composite1 начнёт компилировать блок теней всегда -> compile time + register pressure В КОНКРЕТНОМ проходе. Это мина №2, мерить.
   - ~~**`IRLITE_OUTLINE_TARGET`**~~ — СНЯТО С УЧЁТА. Опасение было: `#if IRLITE_OUTLINE_TARGET != 0` (`composite1.glsl:317`) вырезает единственный в программе `texelFetch(colortex6, ...)` (`:319`), а `composite1.glsl:23-28` — родной страж пака под лимит 8 семплеров на macOS. Юзер 2026-07-21: macOS не поддерживаем -> [[reference-macos-out-of-scope]]. Опция переносится ПЛОСКО, обычным рантайм-сравнением, без единого `#if`. Волна 1 = полные 10 опций.
   Прочие находки скаутинга: `vlD.y` — ЛОЖНО свободный слот (Java хардкодит 0F с комментарием «no setter yet», но `composite1.glsl:360` читает его как bilateral sigma; реально свободны только `vlD.z`/`vlD.w`). `IRLITE_OUTLINE_FRONT` и `_GLOW` численно ИЗБЫТОЧНЫ как биты — при strength 0 результат идентичен, а в паке уже есть свой прецедент этого идиома (`IRLITE_OUTLINE_BACK`, комментарий «slider only (0 = off)»); можно не тратить биты. Ни одного `IrliteConfig`-поля под outline пока НЕ существует — все 10 создавать с нуля. Регистрация в UI обязательна отдельным шагом: объявить поле мало, живое доказательство — `vlBlueNoise`/`vlClusterCull`/`vlShadowHiz` объявлены, читаются, но в `IrlightsAddon.build()` не зарегистрированы.
   `VlGlobalsBuffer` живёт в irl-core (шарится 3 репозиториями) -> расширение UBO = правка ядра + `publishToMavenLocal` + пересборка; два вызова `VlGlobalsBuffer.set()` (`LightCollector.java:142`, `VlSweep.java:217`) обязаны двигаться вместе.
   **Бит «globals valid» = bit 7 (маска 128u)** в `vlC.w`: биты 0-6 заняты (0 shadows, 1 noise, 2 blueNoise, 3 ditherTemporal, 4 clusterCull, 5 hiZSkip, 6 bilateralUpsample), 7..31 доказуемо всегда нули (флаги собираются ровно в двух местах, оба — литеральный OR семи масок; дефолт до первого `set()` = `0x7F`). НЕ путать с `irlite_vlFlagsRt` в SSBO — это ретируемый пре-UBO путь, помеченный «unread here».
2. **Surface: `INTENSITY`, `SPECULAR_INTENSITY`, `TOON_*` + 6 тумблеров** — сюда же бит «globals valid».
3. **`SHADOW_QUALITY` + `SHADOW_*` скаляры** — последними: единственное место с потерей развёртки цикла, мерить отдельно.

Не смешивать с тиражом: в 13 из 14 патч-файлов UBO нет вообще, каждый несёт ПОЛНУЮ копию `irlite_lights.glsl` (1355-1715 строк, разные пути и анкеры) — механической заменой не делается. 7 DOF-комбо в `bbs-dof-addon/patches` — отдельная до-UBO линия, уже разошлась с основной.

## ОТКРЫТОЕ (мелочь)
- Секция «Дополнительно» (аккордеон) — не сделана, возможно не нужна после категорий.
- Поля `vlBlueNoise`/`vlClusterCull`/`vlShadowHiz` объявлены в `IrliteConfig`, но НЕ зарегистрированы; геттеры `field == null || field.get()` молча возвращают true. Работает верно и соответствует политике «технические фичи без кнобов» (2026-07-18), но выглядит как забытая половина фичи — дописать комментарий «намеренно», чтобы не «починили».
- `LightBuffer.setVlGlobalIntensity()`/`setVlFlags()` — мёртвые write-only пути: ни один пак не читает `vlIntensityRt`/`vlFlagsRt` (CR объявляет с комментарием «unread here», остальные как `irlite_pad0/pad1`). Можно вернуть 4 байта заголовка и два вызова на кадр.
- Ветка не коммичена: рабочее дерево содержит всю сессию 2026-07-21.

Связь: [[plan-vl-3c-bilateral]] (контракт UBO, процесс реген+byte-proof+синк), [[addon-ui-config]] (устарел в части BBSSettings-категорий — переписан этой сессией), [[shader-settings]] (правило регистрации опций Iris), [[complementary-pipeline]], [[reference-edit-routing-by-area]], [[plan-vl-profiler]].
