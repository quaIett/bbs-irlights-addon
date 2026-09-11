---
name: project-perf-followup-2026-09-11
description: "Оптимизации после overlay reuse: compact cluster, CPU reuse, host-cell fix; версия 1.1.6, перенос и проверки."
metadata:
  node_type: memory
  type: project
  originSessionId: 2026-09-11-performance-followup
---

Состояние: изменения в рабочих деревьях code/irl-core (main) и code/bbs-irlights-addon (master), без commit/push. Рабочая линия Minecraft 1.20.4. На другие MC-линии и редактор эти изменения не переносились.

Версия: пользователь явно попросил оставить 1.1.6. Core, обе зависимости аддона, mod_version IRLite и tools/VERSION = 1.1.6. Промежуточная версия core 1.1.7 отменена; не использовать её JAR из старых build/cache. Итоговый файл irlite-1.1.6+mc1.20.4.jar включает core 1.1.6.

Основа: этап 0 (профайлер/CSV) и этап 1а (CasterRevision + ShadowOverlayCache + BBS cubic/Villager evaluation) уже находились в рабочем дереве. Они сохранены и включены в пакет изменений. Пользователь подтвердил успех предыдущей оптимизации. Подробности: code/bbs-irlights-addon/docs/performance-stage-1a.md; старый игровой smoke подтверждал 26 reuse/кадр, не полный сопоставимый A/B.

Реализовано в новом проходе:
- ClusterGridBuffer: activeWords=max(1,ceil(packedLightCount/32)), compact upload; legacy uvec2[576] по offset 16 и wide offset 4624 неизменны. 0–32 лампы: 6928 вместо 152080 байт/кадр; 33–64: 9232; 2048: 152080. Максимальная GPU capacity не снижалась. Flood-биты объединяются по слову. Snapshot freshness сбрасывается begin/disable/empty/delete.
- IrlSamplers/Bind: массив array-backed entries обновляется при register, live suppliers вызываются при bind, ранний выход без временного boolean[]/lambda/iterator. GL id нельзя кэшировать между пересозданиями текстур.
- ShadowRenderer: положительная/отрицательная классификация opaque/cutout хранится в существующих VBO/list maps по identity списка. Ошибка сборки сохраняет retry; null entry очищается через прежний eviction.
- ShadowBaker/ShadowOverlayCache: membership не выделяется при reuse-off/cache-off/UNKNOWN; shortlist, face masks и UNKNOWN-контракт сохранены.
- ShadowVramBudget: полный физический point pool выходит до NVX query; политика неполного пула прежняя.
- BlockShadowCache: hostX/Y/Z входят в проверку cache hit. Исправлен переход 0.9 -> 1.1 с одинаковым round, но разным floor/emitter-host. Section index переиспользуется при совпадении шести section bounds; empty sections продолжают инвалидироваться.
- LightCollector: ThreadLocal depth scratch с matrix/vector reuse, свежие mutable values, finally rewind, без хранения Form/World/Pose. Float/double порядок прежний.
- TimingStats: сортировка своего массива один раз до следующего add, без копий для median/p95/HUD.

Проверки: core build+publish и addon build PASS; overlay 44, block-cache 31, shadow-budget 23, profiler 23 + CSV PASS; LightCollector 370491 численных утверждений/256 деревьев; pipeline 3998684 утверждения/245 reference кадров + 21 shader variant PASS. После возврата версии 1.1.6 повторные core/addon builds PASS, локальный remap 1.1.6 обновлён, metadata аддона = 1.1.6+mc1.20.4, единственный nested core = 1.1.6 и совпадает с новым локальным JAR, bytecode major 61. Свежие логи и build-verification.json входят в комплект. Численные проверки относятся к CPU и upload bytes, не к реальному GL driver/BBS callbacks.

Сборка (PowerShell, из корня проекта; пути JDK/кэша адаптировать):
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user'
$mavenOption = '-Dmaven.repo.local=' + (Join-Path $PWD '.maven-local').Replace('\', '/')
Push-Location code/irl-core
try { .\gradlew.bat build publishToMavenLocal $mavenOption --no-daemon --console=plain } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'Core build failed' }
Push-Location code/bbs-irlights-addon
try { .\gradlew.bat build '-Pmc=1.20.4' $mavenOption --no-daemon --console=plain } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'Addon build failed' }
```

На исходном ПК использован --offline; на новой машине без кэша этот флаг не добавлять. При повторной публикации 1.1.6 старый Loom remap может скрыть изменения: проверять только соответствующую директорию code/bbs-irlights-addon/.gradle/loom-cache/remapped_mods/remapped/org/qualet/irl-core-*/1.1.6 и обновлять её, не весь кэш. Проверка tools/verify-core-bundle.ps1 обязательна после сборки; runtime hash проверяется только новым capture, не старым CSV. На исходном ПК Java в песочнице ловила AccessDenied при закрытии cached JAR; сборки/финальные проверки вне песочницы прошли.

Таблица приоритетов и полный отчёт: code/bbs-irlights-addon/docs/performance-next-priorities.md. OPEN: игровой визуальный A/B и FPS этого пакета; BbsMob/BbsModel pooling snapshot только после проверки mutable BBS state/restore; этапы 1б–4 performance-redesign не реализованы. Не объявлять цель 14 -> 5–9 мс достигнутой по объёму SSBO или CPU harness.

Перенос: папка IRLights-changes-1.1.6-2026-09-11 на рабочем столе содержит source/ с актуальными изменёнными и новыми файлами, полное актуальное claude-memory, JAR, отчёт, сведения о Git-базе и manifest SHA-256. Это дополнение к имеющейся копии проекта, не автономный полный исходный проект. В переносе сохранены незакоммиченные файлы этапов 0/1а, чтобы продолжение не потеряло их зависимости.

**Ин-гейм проверка на этом ПК (2026-09-11, билд irlite-1.1.6+mc1.20.4, Complementary_IRLights):** РЕГРЕССИЙ НЕ ОБНАРУЖЕНО (визуально, юзер). Лог чистый — 0 ошибок [irlite]/mixin-failure/исключений, caster-revision-checks PASS 10 на старте. Проверялись сценарии скоупа (границы блоков, 32/64, opaque/cutout, смена shaderpack/quality, движ/неподвиж лампы). Тяжёлая сцена point+spot: gpu bake avg 1.37 мс, CPU frame 25.3 мс / pipeline 1.15 мс. OPEN остаётся только количественный FPS-A/B нового скоупа против 1.1.5-билда (единого тумблера нет — сравнивать двумя jar на одной сцене). Патчер: 3 копии complementaryreimagined.irlights идентичны (sha a813ed…), shaders в 1.1.6 не менялись, compact cluster-upload шейдеро-совместим (legacy uvec2[576] на прежнем offset, stride из header.w).
