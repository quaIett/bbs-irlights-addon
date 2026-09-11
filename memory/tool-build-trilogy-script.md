---
name: tool-build-trilogy-script
description: "BBS/build-trilogy.ps1 (ПЕРЕПИСАН 2026-07-08, вне репов): сборка трилогии per-MC линиями - detached temp-worktree, publishToMavenLocal ядра перед потребителями, jar irl-redactor-1.1+mc<ver>/irlite-1.1+mc<ver> в Desktop/IRLights/{Redactor,BBS Addon}. Боевой прогон 13/13 OK."
metadata:
  node_type: memory
  type: project
  originSessionId: b380c6ee-dcda-4c14-bee0-bc277499a549
  modified: 2026-07-24T11:35:08.921Z
---

build-trilogy.ps1 лежит в C:\Users\Qualet\Documents\Project\Minecraft\BBS\build-trilogy.ps1 (родительская папка BBS, вне git). ПОЛНОСТЬЮ ПЕРЕПИСАН 2026-07-08 (старый чекаут-скрипт от 2026-06-20 умер вместе с до-Ф2 реальностью; бэкап в scratchpad сессии a4cbd228). Боевой прогон 2026-07-08: 13/13 таргетов OK за 2063s (полный, холодные worktree). Повторный прогон 2026-07-24 (`-Version 1.1.5`): 13/13 OK за 1372s (тёплые worktree/кэш); все 8 продукт-jar 1.1.5 в Desktop/IRLights, JiJ = ровно 1 core-1.1.5. Ключ: `-Version 1.1.5` (или BBS/VERSION=1.1.5) — скрипт сам штампует во все worktree, git-бамп веток для сборки НЕ нужен ([[reference-core-versioning]]).

Запуск (PowerShell): & 'C:\Users\Qualet\Documents\Project\Minecraft\BBS\build-trilogy.ps1' [-Lines 1.21.11] [-Dest ...] [-JavaHome ...] [-KeepWorktrees]. Дефолты: все 5 линий; Dest=C:/Users/Qualet/Desktop/IRLights; JavaHome=C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot. Одна линия ~5-8 мин, все пять ~35-45 мин.

Механика: линии СТРОГО последовательно (mavenLocal один на машину). Каждая линия: (1) detached temp-worktree ядра (BBS/.build-wt/irl-core-<line>) от нужной ветки -> gradlew publishToMavenLocal; (2) на каждого потребителя: detached worktree от КОММИЧЕНОГО tip ветки + Copy-LocalLibs (gitignored libs/*.jar из основного чекаута аддона - без этого Loom падает NoSuchFileException на bbs-jar) -> gradlew build -x test --refresh-dependencies [-Pmc=...] -> Assert имени jar + JiJ META-INF/jars/irl-core-1.1.jar -> копия в Dest; (3) teardown worktree (gradlew --stop -> Remove-Item -> prune). Основные чекауты юзера НЕ трогаются (ветки не переключаются, грязное дерево не мешает).

Матрица линий: 1.20.4 = core main -> редактор main + аддон master -Pmc=1.20.4; 1.21.1 = core 1.21.1 -> редактор port/1.21.1 + аддон port/1.21.1; 1.21.4 = core 1.21.4 -> редактор port/1.21.4; 1.21.11 = core 1.21.11 -> редактор port/1.21.11; 1.20.1 = core MAIN (отдельной ветки core НЕТ и не нужно - поверхность совместима, доказано сборкой и прецедентом универсального jar) -> редактор port/1.20.1 + аддон master -Pmc=1.20.1.

Раскладка Dest: Redactor/<mc>/irl-redactor-1.1+mc<mc>.jar (5 папок) + "BBS Addon"/1.20.x/ (тут СОСУЩЕСТВУЮТ +mc1.20.4 и +mc1.20.1) + "BBS Addon"/1.21.1/. Jar ядра юзеру НЕ копируются (JiJ внутри модов). Зачистка перед копией затирает только same-MC jar и старые имена без +mc.

Логи: .build-wt/logs/<line>-<comp>.log (переживают teardown); при падении скрипт сам печатает хвост лога (Show-LogTail).

ГОТЧИ (актуальные):
- PS 5.1 stderr-ловушка: git/gradle пишут информационные строки в stderr; под $ErrorActionPreference='Stop' + редирект потоков это терминирующий NativeCommandError. В скрипте ВСЕ нативные вызовы обёрнуты (EAP='Continue' + 2>&1 stringify, гейт по $LASTEXITCODE) - НЕ ломать при правках.
- Teardown worktree на Windows может не удалить каталог (локи демона на loom-cache jar) - скрипт варнит и идёт дальше; хвосты в .build-wt/ прибирать вручную: gradlew --stop где-нибудь -> rm -rf -> git worktree prune во всех трёх репах.
- После прогона mavenLocal = core ПОСЛЕДНЕЙ линии (сейчас порядок заканчивается 1.20.1 -> core main). Перед ручной сборкой другой линии - publish её ветки core.
- Гонка git index при частых checkout+gradle (2026-06-21, редко): fatal index corrupt -> rm -f .git/index && git reset --hard HEAD (объекты целы).

Контекст: [[project-trilogy-unify-11]] (создание скрипта + коммиты), [[reference-edit-routing-by-area]], [[project-github-repos]].
