---
name: plan-per-light-replays-to-2.0-roadmap
description: "Дорожная карта 2026-09-17 от текущего состояния (per-light Lit/Outline Replays только на мастере+Complementary) до релиза 2.0: 7 шагов, зафиксировано пользователем."
metadata:
  node_type: memory
  type: project
  originSessionId: cbab0940-57ca-4cd8-a34a-922f50e24c8b
  modified: 2026-09-17T11:58:00.943Z
---

РЕШЕНО 2026-09-17. Пользователь зафиксировал порядок работ от текущей точки (per-light Lit Replays/Outline Replays реализованы на мастере, но шейдерная часть — только Complementary; см. [[plan-port-perlight-v2]], [[project-per-light-profiles]]) до релиза 2.0:

1. Порт фичи (per-light replay targeting) на остальные шейдеры, БЕЗ DOF-аддона (Solas, BSL, RethinkingVoxels, Bliss, IterationRP, Photon — контракт профилей v2: источник 96Б + отдельный профиль 112Б, marker 0x49524C50; НЕ struct 160Б).
2. Порт на остальные версии/ветки аддона (не только 1.20.4/1.20.1 master).
3. Актуализация редакторов (irlights repo currently pinned на core 1.1.6/контракт v1 — рассинхрон с v2, см. хвост в [[project-per-light-profiles]]).
4. Релиз 1.1.7.
5. Создание новой ветки оптимизации — переименование/продолжение текущей perf/hard-shadows ([[project-hard-shadows-branch-ab]]).
6. Исследование и оптимизация по 6 пунктам из docs/architecture-audit-cpu-findings-2026-09-16.md (раздел 7 «Исправленный порядок работ» — независимая проверка/коррекция плана [[plan-caster-cpu-pipeline]]): (1) повторный замер сцен на новой сборке с фиксацией условий, (2) решить нужны ли мягкие тени вообще (иначе EVSM/MSM-план не нужен), (3) дешёвые настройки VL (Full/Half, 64/32, stride) прежде froxel, (4) изолировать цену outline/surface (кластерный отсев outline-ламп, cost selected-replay), (5) bake-оптимизация (overlay/revision reuse, block-cache misses, MobForm CPU-путь), (6) froxel/history как последний этап если всё ещё bottleneck.
7. Релиз 2.0.

Why: пользователь хочет сначала довести per-light replay-фичу до полного паритета по всем пакам/версиям и закрыть версию 1.1.7, и только потом открывать отдельный performance-трек к 2.0 — не смешивать feature-порт с оптимизацией.
How to apply: шаг 1 закрыт пользователем 09-17; следующий шаг — пункт 2 (остальные версии аддона), отдельной задачей. Не начинать пункт 5/6 (ветка оптимизации) до релиза 1.1.7. DOF-аддон намеренно исключён из шага 1 — его синхронизация не в этом плане (отдельная задача, см. [[project-per-light-profiles]] хвост про CONTRACT_MISMATCH).


СТАТУС 2026-09-17: шаг 1, код/патчи/сборка DONE; визуальная приёмка OPEN у пользователя. Перенесены Solas → BSL → RethinkingVoxels → Bliss → IterationRP → Photon: независимые Lit/Outline списки, own Outline при global OFF, own VL. DOF/editor/другие версии не менялись. Выполнено поверх существующего грязного perf/hard-shadows, его изменения сохранены; soft-shadow-функции остальных паков не портировались на hard. Коммит/push отсутствуют.

Транспорт: Solas colortex9.rg; BSL 11.rg; RV 6.gb с без потерь упакованным native RGB8 в r и декодированием native-потребителей (включая bilinear и rgba32f image); Bliss 12.rg с сохранением native depth в a через полноэкранное копирование deferred1 и composite; IterationRP 13.rg; Photon 15.gb с сохранением native LoD-depth в r. Токен+глубина 32F, blend metadata off; forward uniform/deferred depth validation. В совместных циклах Photon/IterationRP фильтры раздельны.

Проверки: PatchHarness round-trip 2594 файла PASS; GPU syntax (JCPP → GLSL430 compatibility) 614 fragment + 12 compute + 9 Photon DH PASS; ещё 10 native-программ падают и на исходной базе (Bliss clamp float/int, Photon disabled DOF/TAAU), новых ошибок нет. Iris runtime/linking не проверен, делает пользователь. Build 1.20.4 PASS, nested core 1.1.7-hard SHA совпал; 6 bundled patches совпали с patches и run/irlite/patches. Отчёты build/replay-port/{roundtrip,compilation,bundle,delivery}.json, docs/per-light-replays-shader-ports.md. Новый генератор tools/gen-replay-patches.py + прежние 6 PS entrypoint-обёрток. run/shaderpacks/<Pack>_IRLights_Replays — все 6 новых готовых папок; прежние паки/настройки не заменены, активный пак не переключён. Runtime запускается runClient -Pmc=1.20.4 без quickplay. Следующий этап после визуальной приёмки — шаг 2.

runClient 1.20.4 запущен 2026-09-17: BBS 2.6 entrypoint и OpenGL на RTX 3060 подтверждены логом run/runclient-console.log (процесс Java 11608 на момент запуска). Без quickplay, мир/интерфейс не управлялись; визуальная проверка остаётся у пользователя.


## 09-17 IterationRP replay fix
Пользователь: остальные пять паков работают, IterationRP требует отдельного исправления. В runtime-логе IterationRP загрузился без compile/link errors.
Корень: `/* const int colortex13Format = RG32F; */` на одной строке игнорируется Iris ConstDirectiveParser (требует начало строки const); буфер оставался default normalized RGBA, ID clamp 0..1, глубина квантуется и не проходит epsilon 0.000002. Директива перенесена на отдельную строку внутри многострочного комментария. Replay depth validation и outline depth приведены к native depthtexS (Composite_Copy выбирает solid/translucent material).
Регрессия воспроизведена настоящим Iris 1.7.2 ConstDirectiveParser + GPU readback: до фикса FAIL depth 0.91372555 вместо 0.91337; после PASS шесть ID 0/1/2/255/256/16777215 и глубина. Harness: tools/irislex/ReplayAttachmentCheck.java, компилировать с ConstDirectiveParser.java из локального Iris + LWJGL classpath. Все 146 IRP fragment-программ compile PASS; Java PatchHarness 60 ops, 593 файла round-trip PASS; build 1.20.4 PASS, встроенный patch совпадает. Обновлены только iterationrp.irlights и IterationRP_IRLights_Replays; остальные паки/DOF не тронуты. Отчёт build/replay-port/iteration-fix-verification.json. Повторный визуальный тест IRP OPEN у пользователя.


Закрытие 2026-09-17: пользователь после исправления IterationRP поручил закоммитить и закрыть задачу. Шаг 1 дорожной карты закрыт; следующий — порт на остальные версии аддона, отдельной задачей. Отдельного отчёта о повторном визуальном тесте IterationRP пользователь не предоставлял. DOF исключён. В коммит входят только порт шести паков, исправление IterationRP, генераторы, проверки и относящиеся к задаче заметки; ранее существовавшие изменения hard-shadows остаются незакоммиченными.


СТАТУС 2026-09-17, шаг 2: код и сборки всех четырёх MC (1.20.1, 1.20.4, 1.21.1, 1.21.11) DONE; 4912 BBS checks, 40 bytecode API anchors, GPU SSBO/profile, per-MC bundle PASS. Игровая приёмка OPEN у пользователя; коммитов нет. Worktree/выдача/ограничения и продолжение — [[project-per-light-replays-version-ports]]. Следующий этап после приёмки — шаг 3; редакторы/DOF не менялись.

ПРИЁМКА 09-17: пользователь явно подтвердил «1.21.11 успешно». Игровая проверка Lit/Outline Replays на1.21.11 PASS. Следующая ручная проверка1.21.1; её core1.1.7 republish и runClient запущен. 1.20.1/1.21.1 ещё без пользовательской приёмки; не закрывать весь шаг2. Последний MavenLocal = линия1.21.1.

ПРИЁМКА 09-17: пользователь явно подтвердил «1.21.1 успешно». Игровые проверки Lit/Outline на1.21.11 и1.21.1 PASS. Следующий ручной тест1.20.1: core main1.1.7 republish, runClient1.20.1 в отдельной runs/1.20.1. Пользовательского подтверждения1.20.1 ещё нет;1.20.4 ранее запускался, явного сообщения о приёмке в этой задаче нет. Последний MavenLocal = main1.20.x. Коммитов нет.

ПРИЁМКА 09-17: пользователь явно подтвердил «1.20.1 успешно». Игровые проверки Lit/Outline на1.21.11,1.21.1,1.20.1 PASS. Для финального подтверждения1.20.4 повторно запущен её runClient из _wt-addon-1.20.x с общей bbs-irlights-addon/run. Только приёмка1.20.4 ещё без явного подтверждения в этой задаче. MavenLocal остаётся main1.20.x/core1.1.7. Коммитов нет.

ЗАКРЫТИЕ ШАГА2, 09-17: пользователь явно подтвердил «1.20.4 успешно». Все четыре игровые приёмки Lit/Outline Replays (1.21.11,1.21.1,1.20.1,1.20.4) PASS по сообщениям пользователя. Шаг2 дорожной карты ЗАВЕРШЁН: код, сборки, автоматика и пользовательский runtime. Следующий этап — шаг3, актуализация редакторов; пока НЕ начат. Коммит/push в этой задаче не выполнялись, изменения остаются в описанных worktree и требуют отдельного подтверждения на коммит. Последний запущенный клиент1.20.4 не закрывался агентом после приёмки. MavenLocal = main1.20.x/core1.1.7.

ПОРТ UX09-17: по запросу пользователя новый сценарий ключ+выбор перенесён с1.20.4 на1.20.1/1.21.1/1.21.11. Сборки и автоматика PASS, ручная приёмка новых3портов OPEN. Подробнее [[project-replay-selection-ux]]. Шаг3 standalone editor этим не закрывается.

ЗАКРЫТИЕ UX09-17: все4MC приняты пользователем; новый UX и его тираж завершены. Пользователь разрешил коммит всего проверенного replay-чекпоинта. Следующий шаг — пункт3, standalone irlights editor; не начат. [[project-replay-selection-ux]].
