---
name: project-workstation-resync-2026-09-10
description: "Ресинк 2026-09-10: рабочая копия ...\BBS\* оказалась восстановлена из старого бэкапа (addon master отставал на 97 коммитов), источник правды = C:\Users\qualet\Desktop\IRLIghts_new (снимок 2026-08-30). Все 5 репо + Shadres + боевые паки + склад памяти подтянуты локально; GitHub НЕ трогали (origin отстаёт: core +3, addon +6, editor +2 на ветку)."
metadata:
  node_type: memory
  type: project
  originSessionId: 2d5790fc-cc20-414c-9ef4-6ffe694f76a0
---

СИТУАЦИЯ. Рабочие дира `C:\Users\Qualet\Documents\Project\Minecraft\BBS\*` были восстановлены из СТАРОГО бэкапа (у всех mtime 2026-08-30 19:4x, содержимое — состояние начала июля). Git это подтвердил: addon `master` = 3c3ef3f, строгий предок `origin/master` (2e1b7b3) на 97 коммитов позади снимка; editor-ветки отставали на 43..95; core `main` на 35. Расхождений (local-only коммитов) НЕ было НИ В ОДНОМ репо — только чистая перемотка вперёд.

ИСТОЧНИК ПРАВДЫ = `C:\Users\qualet\Desktop\IRLIghts_new` (снимок 2026-08-30 18:29, см. его `README-ПЕРЕНОС.md`): `code/` (5 репо со всеми ветками/стэшами/рефлогами), `Shadres/` (dev-цикл шейдеров, вне git), `claude-memory/` (реальное содержимое junction'а), `tools/` (build-скрипты + CHANGELOG'и + VERSION).

ЧТО СДЕЛАНО ЛОКАЛЬНО (удалённый репо НЕ трогали).
- Git: в каждом репо снимок подключён временным remote'ом, все ветки перемотаны `--ff-only` / `branch -f`; upstream'ы возвращены на origin. Перед этим все локальные ветки и stash'и забэкаплены в `refs/presync-20260910-200826/*` (живут в репо, снести можно `git for-each-ref --format='%(refname)' refs/presync-20260910-200826 | xargs -n1 git update-ref -d`).
- Заведены отсутствовавшие ветки: core `1.21.1`/`1.21.4`/`1.21.11`, addon `feature/entity-reveal-spotlight-cr`.
- `IRLights-flashlight` локально ОТСУТСТВОВАЛ — склонирован из снимка в `...\BBS\IRLights-flashlight`, origin переставлен на `https://github.com/quaIett/irl-flashlight.git`.
- `bbs-dof-addon` локально был БЕЗ remote'а — заведён origin + `refs/remotes/origin/master`; вернули потерянный stash@{0} («wip on stale port/1.21.1») через `git stash store`. Ветка `port/1.21.1` (b49c176) есть только локально — в снимке её нет, оставлена как есть.
- Файлы вне git: `bbs-irlights-addon/Shadres` зеркалирован из снимка (`robocopy /MIR`, старый в scratchpad `presync-backup/addon-Shadres`); `run/shaderpacks` — ДОБАВЛЕНЫ боевые паки снимка (`photon_v1.3b_IRLights`, `ComplementaryReimagined_r5.8.1_IRLights` + zip'ы, правки от 08-11) РЯДОМ со старым локальным набором из 7 паков (июльский, имена другие — коллизий нет); `TEMP/`, `bin/`; для DOF — `Memory/`, `libs/bbs-2.4-*.jar`, DOF-паки. Корневой `Shadres` (`...\Minecraft\Shadres`) уже совпадал байт-в-байт.
- В корень `...\BBS\` положены АКТУАЛЬНЫЕ build-скрипты из `tools/` снимка: `build-trilogy.ps1` (30 КБ вместо 14 КБ — worktree-сборка `.build-wt`, per-MC core), `build-trilogy-parallel.ps1`, `build-bbs-pack.ps1`, `build-line.ps1`, `VERSION` (=1.1.5, скрипт читает его из BbsRoot), CHANGELOG'и 1.1.3→1.1.5. См. [[tool-build-trilogy-script]].
- Память: см. инцидент в [[reference-memory-junctions]] (склад 109→153, оба junction'а пересозданы).

СОСТОЯНИЕ ПОСЛЕ (= снимок): core `main`=50671ef (origin +3), addon `master`=701336f (origin +6), `port/1.21.1`=504ea02 (origin +2), editor все 5 веток `+2` к origin, dof `master`=e27a808 (=origin), flashlight `main`=d7af972 (=origin). Рабочие деревья чистые. GitHub отстаёт ровно на эти хвосты — пушить по отдельному решению.

РАСХОЖДЕНИЕ ЧЕКАУТА: в снимке редактор (`irlights`) стоял на ветке `port/1.21.11`, локально оставлен на `main` — если нужно продолжить ту работу, `git switch port/1.21.11`.

ОКРУЖЕНИЕ ОБНОВИЛОСЬ: `~/.m2` не существовало вовсе (трилогия на этой копии никогда не собиралась) — core пере-опубликован в mavenLocal. Пути JDK на машине СМЕНИЛИСЬ относительно записанных в [[reference-edit-routing-by-area]]: теперь `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` и `jdk-21.0.12.101-hotspot` (есть ещё `.8`-сборки обеих). Системный `JAVA_HOME` по-прежнему JDK 8 — `JAVA_HOME` задавать явно на каждую команду.
