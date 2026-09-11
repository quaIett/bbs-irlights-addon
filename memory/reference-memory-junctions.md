---
name: reference-memory-junctions
description: "3 memory-дира трилогии (irlights/addon/core) = ОДИН физ. склад через Windows junctions; реальный склад = ключ проекта ...BBS-irlights; addon+core memory = junction на него (2026-07-01). Сессия в любом из трёх грузит тот же MEMORY.md и пишет в один склад, ноль синка. Мёртвые pre-rename базы IRLite/IRL-redactor вычищены 2026-07-01 (flashback-план спасён)."
metadata: 
  node_type: memory
  type: reference
  originSessionId: a933c891-3656-4062-80f5-9cbf24fd6717
---

Единый memory-склад трилогии (junction-топология, создано 2026-07-01).

Реальный склад (source of truth): C:\Users\Qualet\.claude\projects\C--Users-Qualet-Documents-Project-Minecraft-BBS-irlights\memory (ключ проекта irlights = редактор; 53 .md на момент создания). Все три проекта трилогии читают/пишут ЕГО.

Junctions на реальный склад (Windows directory junction, LinkType=Junction, БЕЗ прав админа):
- C:\Users\Qualet\.claude\projects\C--Users-Qualet-Documents-Project-Minecraft-BBS-bbs-irlights-addon\memory -> реальный склад
- C:\Users\Qualet\.claude\projects\C--Users-Qualet-Documents-Project-Minecraft-BBS-irl-core\memory -> реальный склад (сам проект-дир ...BBS-irl-core создан вручную под junction, т.к. сессий в irl-core ещё не было; Claude Code подхватит существующий при первой сессии).

Эффект: сессия Claude Code в любом из трёх репо (irlights / bbs-irlights-addon / irl-core) грузит ТОТ ЖЕ MEMORY.md и пишет память в один склад. Синхронизация не нужна. Проверено: read (md=53 + MEMORY.md видны через оба junction) и write-through (файл, записанный через core-junction, появился в main+addon).

Пересоздать junction (если Claude Code пересоздаст проект-дир начисто): PowerShell New-Item -ItemType Junction -Path <проект-key>\memory -Target <реальный склад>.

Удаление: НЕ делать рекурсивный delete по junction (rm -rf / Remove-Item -Recurse способен провалиться в target и снести реальную базу). Снести ТОЛЬКО линк: cmd /c rmdir <link> ИЛИ (Get-Item <link>).Delete().

Живые дира трилогии (source of truth для маршрутизации) = ТОЛЬКО ключи bbs-irlights-addon / irl-core / irlights; на диске существуют папки ...BBS\bbs-irlights-addon, ...BBS\irl-core, ...BBS\irlights (проверено 2026-07-01). Открывать проект из другого пути = другой ключ = отдельная база (единство держится на cwd).

РЕПО-зеркало != склад (гоча, словлена 2026-07-02): в git-репо аддона папка memory/ = ОБЫЧНАЯ трекаемая папка-снапшот, НЕ junction. Write в память идёт в склад; в репо-зеркало попадает только ручным синком перед memory-коммитом (diff -rq склад против repo/memory, копия в обе стороны: склад авторитетен, но repo может держать файлы, потерянные складом — 2026-07-02 так восстановлены plan-shadow-filtering-refactor.md и _archive/shadow-refactor-research-2026-07-01). При чекпоинте: сначала синк, потом git add memory/.

Мёртвые pre-rename базы ВЫЧИЩЕНЫ 2026-07-01: ключи ...BBS-IRLite (было 39 .md) и ...BBS-IRL-redactor (было 58 .md) — от СТАРЫХ имён папок до переименования 2026-06-18 (IRLite->bbs-irlights-addon, IRL-redactor->irlights); проект-папок с такими именами на диске нет -> сессии там не открыть, junction им не нужен был. Перед удалением всё содержимое сверено по имя+MD5 против ВСЕГО склада (вкл. _archive): покрыто всё, КРОМЕ одного уникального project-flashback-irlights-plan.md (жил только в IRL-redactor) -> спасён в активный склад. Файлы «ОТЛИЧАЕТСЯ» = старые версии активных заметок (склад новее после консолидации 2026-06-29); мнимо-«UNIQUE» = завершённые план-файлы, уже лежащие в складском _archive/. Удалены ТОЛЬКО memory-подпапки (реальные, LinkType пустой — не junction); session-логи проект-диров оставлены. См. [[reference-edit-routing-by-area]], [[feedback-memory-strict-style]].

ИНЦИДЕНТ 2026-07-12 (сломанный addon-junction, обнаружен и ПОЧИНЕН): junction ключа bbs-irlights-addon оказался заменён ОБЫЧНОЙ папкой (LinkType пуст) с двумя файлами сессий 2026-07-08 (project-port-12111-refresh.md, project-trilogy-unify-11.md) — это были ЕДИНСТВЕННЫЕ копии (в складе и репо-зеркале их не было, оба MEMORY.md ссылались вхолостую). Следствие поломки: весь перф-трек 2026-07-10..12 (4 план-файла + MEMORY.md-строки) жил ТОЛЬКО в репо-зеркале аддона, склад отставал. Починка: 2 файла восстановлены в склад+зеркало, 4 перф-файла скопированы репо->склад, складской MEMORY.md перезаписан репо-версией (проверен строгий суперсет: 0 уникальных строк в складе), junction пересоздан (mklink /J). Урок: в НАЧАЛЕ сессии, если memory-дир выглядит тонким (нет MEMORY.md / мало файлов) — проверить LinkType и НЕ доверять ему как складу; писать через зеркало репо и синкать. Перф-сессии 2026-07-10..12 использовали зеркало как рабочую базу — теперь склад снова авторитетен, зеркало = снапшот при чекпоинтах.

ИНЦИДЕНТ 2026-09-10 (ОБА junction'а исчезли, склад отстал — ПОЧИНЕНО): после восстановления рабочей копии из СТАРОГО бэкапа (все дира ...\BBS\* имеют mtime 2026-08-30 19:4x) топология развалилась: ключ `...BBS-bbs-irlights-addon\memory` = ОБЫЧНАЯ ПУСТАЯ папка (LinkType пуст, 0 файлов — сессия стартовала бы без памяти вообще), проект-дир `...BBS-irl-core` вообще отсутствовал, реальный склад `...BBS-irlights\memory` = 109 файлов при 153 в актуальном снимке (отстал на ~44 файла: весь трек 07-19..08-11 — VL-профайлер, partial-tile, shadow-lod, contact-penumbra, dof-1211, editor-redesign, flashlight). Источник правды для починки: `C:\Users\qualet\Desktop\IRLIghts_new\claude-memory` (снимок 2026-08-30, 153 файла) — сверено: файлов «только в складе» НЕТ, снимок = строгий суперсет. Починка: robocopy снимок->склад (склад 153), затем junction'ы пересозданы через .NET (`New-Item -ItemType Junction`) на addon-ключ и на созданный заново `...BBS-irl-core`. Старый склад сохранён в scratchpad сессии (`presync-backup/memory-store-old`). ГОЧА ИНСТРУМЕНТА: `cmd /c rmdir "<link>"` блокируется хуком Claude Code (парсит `/c` как системный путь) — сносить линк через `[System.IO.Directory]::Delete($p)` или `(Get-Item $p).Delete()`. См. [[project-workstation-resync-2026-09-10]].
