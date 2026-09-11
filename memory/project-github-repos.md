---
name: project-github-repos
description: "GitHub-дом проектов под owner quaIett (заглавная I): трилогия irl-core / bbs-irlights-addon / irl-editor (public) + bbs-dof-addon (private) + irl-flashlight (public, создан 2026-08-10, первый РЕЛИЗ в экосистеме v1.0.0). Где origin у каждого, какие ветки запушены, gh CLI. Запрет на публикацию патча IterationRP снят 2026-07-26 — но исходники платного пака по-прежнему не в git."
metadata:
  node_type: memory
  type: project
  originSessionId: 16e57e57-6e6f-4bc0-b9b7-f7fddfcb7982
  modified: 2026-07-26T17:21:01.084Z
---

GitHub-репозитории трилогии (созданы 2026-06-20). Трилогия (3 локальных репа из [[reference-edit-routing-by-area]]) опубликована на GitHub как ПРИВАТНЫЕ репозитории под аккаунтом quaIett (внимание: третий символ — ЗАГЛАВНАЯ I, не строчная l; git config user.name = qualet, это другое).

| Локальная папка | origin | default branch | запушенные ветки |
|---|---|---|---|
| irl-core | https://github.com/quaIett/irl-core.git | main | main |
| bbs-irlights-addon | https://github.com/quaIett/bbs-irlights-addon.git | master | master, port/1.21.1 |
| irlights (редактор) | https://github.com/quaIett/irl-editor.git | main | main, port/1.21.1, port/1.21.4, port/1.21.11 |

Все три — private. Тегов на момент создания не было.

**irl-flashlight = 5-й репо (2026-08-10, [[project-flashlight-addon]]): https://github.com/quaIett/irl-flashlight, PUBLIC, default main, одна ветка main, один коммит (проект НЕ был git — история начата с нуля, ничего чистить не пришлось).** Имя выбрал юзер: `irl-flashlight` (не `irlights-flashlight`, хотя папка и archives_base_name = irlights-flashlight → jar по-прежнему `irlights-flashlight-*.jar`, как у редактора с irl-redactor-*.jar). LICENSE переведён ARR → MIT © 2026 qualet (по решению юзера, единообразно с трилогией; LICENSE вкладывается в jar через `jar{from("LICENSE")}` → после смены лицензии ВСЕ jar пересобраны). README по шаблону irl-editor (центрированный заголовок, shields-бейджи, таблицы фич/версий/сурс-рутов, разделы Requirements/Downloads/Installation/Usage/Universal build/Building/The suite). Ассеты (модель предмета + текстура) публикуются — юзер подтвердил, что текущая модель ЕГО; сторонний .bbmodel в проекте отсутствовал (папки Desktop/model и new_model уже удалены), удалять было нечего.

**ПЕРВЫЙ РЕЛИЗ во всей экосистеме: irl-flashlight v1.0.0** (у irl-core / irl-editor / bbs-irlights-addon тегов и релизов НЕТ вообще) — 5 ассетов: `irlights-flashlight-1.0.0+mc{1.20.1,1.20.4,1.21.1,1.21.11,1.20.1-1.21.1}.jar`, в notes честно помечено, что 1.21.x ин-гейм не тестировались. Ассеты доливались через `gh release upload v1.0.0 <jar> --clobber` (плюс `gh release edit --notes-file -` для правки таблицы) — тег не переносился, mod_version остался 1.0.0. Гоча идентичности: ГЛОБАЛЬНЫЙ git user = `skebob <skebob@example.com>`, а во всех репах экосистемы коммиты от `qualet <qualetprod@gmail.com>` — в новых репах ОБЯЗАТЕЛЬНО ставить локальный user.name/user.email, иначе автор коммита выпадет из ряда.

bbs-dof-addon = 4-й репо. Был НАМЕРЕННО LOCAL-ONLY (2026-07-04) из-за бандла combo-патчей под платные шейдеры (IterationRP Tahnass) — тот же контент, что вычищался из трилогии ДЛЯ ПУБЛИКАЦИИ (см. блок «IterationRP убран»). **2026-07-26 юзер дал прямую команду — репо СОЗДАН: https://github.com/quaIett/bbs-dof-addon, PRIVATE, default master, ветка master.** **Ограничение снято 2026-07-26 (юзер): патч IterationRP МОЖНО выкладывать** — флип этого репо в public патчем не блокируется. Из репо исключены: `libs/*.jar`, `Shaders/`, `Memory/` (untracked 2026-07-26, стухшие заметки эпохи IRLEngine). Пуш трилогии 2026-07-04: irl-core main->6668f22, bbs-irlights-addon master->283256b, irl-editor main->57a7dfd (все fast-forward, origin у каждого).

Репо редактора ПЕРЕИМЕНОВАН 2026-06-20: irlights -> irl-editor (юзер: «redactor» — рунглиш-ошибка, по-английски правильно «editor»). Поменяны: имя GitHub-репо (старый URL редиректит) + локальный origin + брендинг в README всех 3 репов (IRL-redactor->IRL-editor, ссылки quaIett/irlights->quaIett/irl-editor). НЕ менялись (по просьбе «в коде не трогать»): локальная папка осталась irlights, пакет org.qualet.irlredactor, mod-id irl-redactor, archives_base_name=irl-redactor (-> jar всё ещё irl-redactor-*.jar, в README намеренно оставлено как фактическое имя).

Тулинг (важно для будущих push):
- gh CLI v2.95.0 установлен через choco -> C:\Program Files\GitHub CLI\gh.exe (PATH в новых шеллах подхватит; в старой сессии — полный путь).
- Авторизация: device-flow под quaIett, scopes repo, read:org, gist, токен в keyring.
- gh auth setup-git выполнен -> глобальный credential.helper = !gh auth git-credential -> git push по https работает без промптов.

README + лицензия (2026-06-20):
- У всех трёх репов есть README.md (стиль: центрированный заголовок, shields.io-бейджи, таблицы фич/версий) + LICENSE = MIT, на ВСЕХ ветках. Шаблон стиля = README аддона.
- Аддон перелицензирован: был CC BY-ND 4.0 (LICENSE.txt, NoDerivatives, © wemppy+qualet) -> теперь MIT (LICENSE.txt удалён, добавлен LICENSE).
- Копирайт во всех MIT-файлах = qualet (по решению юзера wemppy убран, хотя старая лицензия аддона его упоминала — учесть, если всплывёт вопрос соавторства).

⚠️ УСТАРЕЛО в части ПАТЧА — см. апдейт 2026-07-26 в конце блока. Историческая запись: IterationRP убран + ИСТОРИЯ ВЫЧИЩЕНА для публикации (2026-06-20): IterationRP — приватный/платный шейдерпак (Tahnass). По решению юзера интеграция убрана до разрешения автора; файлы оставлены локально (gitignored, НЕ удалены).
- Делистнут из всех 6 README; untracked + в .gitignore (маркер # IterationRP integration): аддон patches/iterationrp.irlights + tools/gen-iterationrp-patch.ps1; редактор src/client/resources/assets/irl-redactor/patches/iterationrp.irlights.
- НАХОДКА: в ИСТОРИИ аддона лежал весь Shadres/ (полные исходники IterationRP платный + Photon + Original + Modification) — когда-то закоммичен, потом gitignored (в HEAD не было, только в истории). Это блокировало публикацию.
- История ПЕРЕПИСАНА git filter-repo (вырезано из ВСЕХ коммитов/веток): аддон = Shadres/ + regex (?i)iterationrp (поймал и старое имя patches/iterationrp.irlpatch до ребрендинга .irlpatch->.irlights); редактор = regex (?i)iterationrp + \.log$ (5 dev-логов). irl-core НЕ трогали (история чиста).
- Репо bbs-irlights-addon и irlights УДАЛЕНЫ и ПЕРЕСОЗДАНЫ на GitHub (нужен был скоуп delete_repo — добавлен), залита чистая история; проверено свежим mirror-клоном: 0 совпадений shadres/iteration/.log. Дефолт-ветки восстановлены (master/main).
- SHA ВСЕХ коммитов аддона/редактора СМЕНИЛИСЬ (история переписана) -> ссылки на старые SHA в памяти/доках устарели (код тот же).
- Бэкап ДО переписи: BBS/_history-backup-20260620/{irl-core,bbs-irlights-addon,irlights}.bundle (addon.bundle 30M = со старым Shadres). Откат: git clone <bundle>.
- patches.zip (аддон) — локальный, не в git.
- Видимость на 2026-06-20 всё ещё private у всех трёх; юзер планирует сделать публичными (теперь безопасно). Фактически на 2026-07-26 трилогия УЖЕ PUBLIC (gh repo list), private только bbs-fbs-npc и bbs-dof-addon.

**АПДЕЙТ 2026-07-26 — запрет на IterationRP СНЯТ (прямые слова юзера: «патч IterationRP можно выкладывать»).** Держать разделение:
- **`.irlights`-ПАТЧ под IterationRP — МОЖНО публиковать.** Это наш код (набор якорей+вставок), не пак. Делистинг из README и gitignore трилогии больше не обязателен; если захочется вернуть в трилогию — делать по команде, само собой это не откатится.
- **ИСХОДНИКИ пака (`Shadres/`, полное дерево платного IterationRP) — по-прежнему НЕ в git.** Причина другая и в силе: это перераспространение чужого платного продукта. Именно это блокировало публикацию в 06-20 и ради этого гоняли filter-repo. Не путать с патчем.

Гочи:
- Старый remote аддона указывал на quaIett/IRLights.git — этот репо УДАЛЁН (404 даже после авторизации). origin аддона переуказан на новый bbs-irlights-addon.
- Owner легко опечатать: quaIett != qualett != qualet.
