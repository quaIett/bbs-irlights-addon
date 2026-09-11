# Этап 1а: версии силуэта и повторное использование overlay

Реализация от 2026-09-11, MC 1.20.4, core 1.1.6. Изменения этапа 0 сохранены.

## Поддержанный путь BBS

Первый путь: известное состояние выдаётся для ModelBlockEntity с точным ModelForm,
ModelFormRenderer и cubic Model из BBS `2.3.1-1.20.4`. Проверяются CPU-геометрия
кубов/мешей и загруженные VAO. Geometry-сигнатура общая для экземпляров модели
в пределах одного кадра, затем вычисляется заново.

Перед оценкой проверяется отсутствие активных animator actions, procedural
animator, state players, IK, physics/wind, constraints, геометрических body parts, equipment
и look-at. После этого выполняется настоящий `Model.resetPose/applyPose` с
`renderer.getPose()`, включая pose overlays, rotate2 и quaternion orientation.
Общее изменяемое состояние модели сохраняется и восстанавливается в `finally`.
Авторская поза, включая меняемые извне bone tracks, проверяется каждый кадр.
Недостаточно сравнить сериализованную форму или положение model block.

Подписи разделены на transform, evaluated pose, morph/scale, geometry,
material/cutout и resources. Текстуры должны быть загруженными обычными BBS
Texture; animated textures и неизвестные реализации не кэшируются. Mixin
версии учитывают загрузку/изменение пикселей, параметры текстур, создание и
обновление VAO. Перезагрузка ресурсов и замена Iris pipeline меняют resource
generation. Разрешение текстур материалов соответствует байткоду **2.3.1**:
при числе материалов <= 1 используется default texture, иначе применяются
runtime override, выбранная texture и fallback модели.

Второй путь: стандартный `MobForm(minecraft:villager)` без пользовательского
NBT, texture override, имени, экипировки и вложенного morph. Именно такие 26
model blocks находятся в `New World`. Их `EntityRenderDispatcher` выполняется
с CPU-only VertexConsumer: собирается сигнатура реально испущенных вершин,
UV, alpha и render layers **после** vanilla-анимации и BBS pose mixin. Включён
полный dispatcher, в том числе его ground-shadow geometry; вызов одного лишь
VillagerEntityRenderer пропустил бы её. ModelPart, временные BBS-позы и hurtTime
восстанавливаются после пробы, включая аварийный путь. На GPU проба не рисует.
Это позволяет сравнивать текущую анимацию, а не считать жителя статичным по классу.

В обоих путях разрешены body parts, состоящие только из конечных IRLite
PointLightForm/SpotlightForm без state players: их собственный render3D прямо
возвращает управление при ShadowBakeState.isBaking(), геометрии у них нет.

Для BOBJ, world entities/morphs, replay stubs, геометрических attachments,
активных runtime-анимаций/IK/physics cubic-моделей, остальных MobForm и других
версий BBS возвращается UNKNOWN. Эти пути продолжают
обновляться каждый кадр. Расширение поддержки требует собственного полного
контракта оценки; неподвижность на вид его не заменяет. Прямые изменения GPU
ресурсов сторонними модами в обход BBS API также не являются поддержанным путём.

## Обновления и очистка

Общие spot/point проверки находятся в core. Совпасть должны точное множество
caster, все шесть компонентов, bounds/face mask, лампа, tile, статическая основа
и настройки partial tile. При совпадении пропускаются copy, draw, pyramid и
moments вместе. Исчезновение caster, tile handoff, reload или неизвестное
состояние не дают пропуска. Фильтрация point при изменении остаётся полной;
частичных зависимых швов этот этап не вводит.

Сбой caster/flush не фиксируется как готовая тень. Прерванный проход фильтров
сбрасывает записи, отсутствие ресурсов сохраняет повторные попытки. World,
quality, shaders-off, cache-off, удаление фильтров и вытеснение tile очищают
состояние. BBS-объекты не переносятся в static layer.

## Проверка

Core `build publishToMavenLocal`, addon `build -Pmc=1.20.4`, 34 проверки overlay,
15 проверок профайлера и CSV-регрессия выполнены. В клиенте дополнительно прошли
10 проверок повторной оценки, translation, rotate2, scale, итоговых вершин головы,
геометрии тела, неизвестной текстуры, восстановления состояния, reload и очистки
BBS pose cache. Включаются через `-Dirlite.checkCasterRevisions=true`; тестовые
изменения восстанавливаются синхронно до настоящего draw/save. Для этого ПК Gradle запускается
с `--no-daemon`: daemon, созданный в ограниченной песочнице, получал AccessDenied
при чтении Minecraft JAR даже после повторного запуска клиента Gradle.

Команды сборки — в `performance-redesign.md`; новый локальный core — 1.1.6.
После повторной публикации той же версии нужно точечно обновить её Loom remap.
`tools/verify-overlays.ps1` находится в core; `tools/verify-profiler.ps1` — в addon.
`tools/verify-core-bundle.ps1 -CaptureMetadata <capture.csv.json>` проверяет ровно
один вложенный core, побайтовое совпадение со свежим core JAR, Java 17 и runtime
class hash. Пройдено; отчёт `build/performance/stage1a-build-verification.json`.

## Игровой smoke-check на New World

Финальный запуск: `build/overlay-final-smoke.log`, штатный выход с сохранением
мира, Gradle exit=0. Маркер `caster-revision-checks: PASS 10` получен.
CSV: `run/logs/irlite-perf/capture-13240775783976152988.csv`, SHA-256
`998D870FF6D973ADBAB56A3B3725325B71E877F8E77D040A2E93717585333F34`.
Окно [241, 841): 600/600 полных GPU-кадров, пропуски 0; анализ сохранён в
`build/performance/stage1a-smoke.json`. На каждом кадре `caster.known=26`,
`caster.unknown=1`, `sp.reuse=26`; copy/draw/pyramid/moments для спотов не
выполнялись. Суммарный GPU bake около 0,01 мс; CPU pipeline в среднем 1,44 мс.

Это доказательство работы пропуска в неподвижной сцене, **не сопоставимый A/B**
со старым замером 8,15 мс: dev-клиент стартовал в 854×480, с новым dev-player и
другой камерой. VSync, VL и камера не приводились к условиям контрольного A/B.
Быстрые движения и визуальные швы этим smoke-check не проверены. Cubic-путь
собран и сверен с BBS API, но эта конкретная сцена использует MobForm.

Для сравнения выполнить одинаковые запуски с подробными таймерами и CSV, во
втором добавить `-Dirlite.noOverlayReuse=true`. Новые счётчики: `caster.known`,
`caster.unknown`, `sp.reuse`, `pt.reuse`. Метаданные новых CSV записывают режим
reuse. Сравнивать полные окна после прогрева, учитывая CPU-стоимость оценки поз.

Полный визуальный набор из плана и контрольный A/B ещё требуются. Целевые
14→5–9 мс и VL ×2–4 этим изменением не объявляются достигнутыми. Этапы 1б–4
этой реализацией не закрываются.
