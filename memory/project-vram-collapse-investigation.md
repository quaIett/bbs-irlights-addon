---
name: project-vram-collapse-investigation
description: "VRAM-коллапс: АТРИБУЦИЯ ЗАВЕРШЕНА 2026-07-19 — виновник = point-shadow стек irl-core на ULTRA, 9594 MiB алловится ОДНОЙ секундой на джойне (2×24576² депт-атласа + полные tier-массивы pyr/EVSM под 30 ламп при 1 лампе в сцене); клампа F=4096→2048 НЕ существует (24576<GL_MAX 32768). Деградация у лампы = residency-трэш при ~0.4 GB free, НЕ утечка. NEXT: дизайн фикса (VRAM-бюджетный кламп + demand-sized тиры)."
metadata: 
  node_type: memory
  type: project
  originSessionId: fc514b3c-22b5-4879-b601-d21f5bbad7a6
  modified: 2026-07-20T11:41:28.583Z
---

# VRAM-коллапс (15 FPS): АТРИБУЦИЯ ЗАВЕРШЕНА — point-shadow стек, 9.6 GB на джойне

## ВЕРДИКТ (2026-07-19, репро с alloc-телеметрией, аддон + мир с 1 PointLightForm)
Сумма alloc-строк shadow-стека = **9594 MiB**, вся выделяется одной секундой на джойне мира (18:57:57-58). Совпадения: дельта java dedicated в редакторском репро = 9638 MB (расход ±44 MiB); GPU-семплер этого прогона: java 217 → **10315 MB** — скачок целиком в клиенте. Виновник назван поимённо, sodium/iris/ванила оправданы.

Разбивка (`[irl-core] quality: ULTRA (point F=4096 -> effective 4096, spot tile=4096 -> effective 4096)`):
| аллокация | размер | MiB |
|---|---|---|
| point static depth | 24576×24576 D32F | 2304 |
| point live depth | 24576×24576 D32F | 2304 |
| point-pyr t0/t1/t2 | 2048²×12 / 1024²×72 / 512²×96 rg32f | 511+767+255 |
| point-evsm t0/t1/t2 | те же слои rgba32f+мипы | 1023+1535+511 |
| point-evsm temp | 2048²×6 rgba32f | 384 |
| **итого** | | **9594** |

Двухступенчатость редакторского репро (+7.3/+2.3 GB) = группы коммита драйвером: static+live+pyr+evsm-t0 ≈ 7164, evsm-t1/t2+temp ≈ 2430.

## КЛЮЧЕВЫЕ ФАКТЫ (не переоткрывать)
1. **Клампа НЕТ**: старая оценка «F=4096 клампится до 2048 по GL_MAX_TEXTURE_SIZE» НЕВЕРНА — атлас 6F=24576 < GL_MAX 32768 (RTX 3060), effective остаётся 4096. Отсюда 2×2304 MiB депт-атласов.
2. **Тир-массивы алловятся ЦЕЛИКОМ под максимум ламп** (t0 ×12 слоёв=2 лампы, t1 ×72=12, t2 ×96=16, по 6 граней) — при ОДНОЙ лампе в сцене. Спрос не учитывается.
3. **«Ленивый» static-атлас срабатывает мгновенно**: игрок сам = динамический кастер → первый overlay-бейк на джойне → static+live алловятся сразу. Лень не спасает.
4. **Деградация у лампы ≠ утечка**: после алловов free ≈ 0.35-0.5 GB → любые пер-кадровые аплоады заставляют драйвер эвиктить (счётчик NVX кумулятивный по драйверу, 74.7 GB на старте прогона включал прошлые сессии; в этом прогоне дельты малы). Прогрессивный рост кадра 35→236 ms из стресс-500-репро = residency-трэш при нулевом хедруме.
5. F11-аномалия (mode change → сброс) объясняется: эвикт мусора при пересоздании свапчейна временно возвращает хедрум. Родитель: [[plan-shadow-bake-track]].
6. Эвикт-строки `[irlite] vram:` при free<1 GB идут КАЖДУЮ секунду — вотчер на них флудит, фильтровать только alloc/quality/crash.

## Артефакты
- bbs-irlights-addon/run/runclient-console-alloc-repro-2026-07-19.log — полный лог репро (quality+alloc+vram-строки).
- bbs-irlights-addon/run/gpu-mem-alloc-repro-2026-07-19.csv — per-process GPU-семплер 1 Гц (java 217→10315 MB).
- Прошлое репро деградации: bbs-irlights-addon/run/runclient-console-vram-repro-2026-07-19.log.

## ФИКС 1+2 РЕАЛИЗОВАН + ЗАКОММИЧЕН (2026-07-19, core **8189aca**, ветка optimization/octahedral-point-shadows)
Гейт юзера ПРОЙДЕН (два прогона + команда «коммить»). Второй прогон (preset-cycling, лог run/runclient-console-fix-e2e2-preset-cycling-2026-07-19.log): 249 vram-окон, free 6566–9641 MiB, эвикций НОЛЬ; циклы LOW↔MEDIUM↔HIGH↔ULTRA чистые, кламп стабильно только на ULTRA point. W2-правки (ClusterGridBuffer/LightRegistry) НЕ входят в коммит — остаются красной линией. Редактор (irl-redactor) заберёт фикс при пересборке на core 1.2 из mavenLocal — при этом ЧИСТИТЬ его loom-кэш (см. готчу ниже). Юзер дал добро на связку 1+2. Реализовано в irl-core (9 файлов, ShadowVramBudget НОВЫЙ; дифф отделим от W2 — базис в scratchpad core-w2-baseline.diff):
- **Бюджетный кламп**: PointDepthAtlas/SpotlightDepthAtlas.setTileSize — power-of-two ladder до (free NVX + resident своей цепочки − резерв 2560 MiB)/2 на цепочку; фолбэк без NVX = 3 GiB/цепочку; спот получил и отсутствовавший GL_MAX-кламп. -Dirlite.shadowVramReserveMb / -Dirlite.shadowVramBudgetMb (override free). apply(): evsmShift теперь ДО setTileSize (спот-оценка считает с правильным shift); IRLShadowQuality.current стартует null (кламп+quality-строка срабатывают и на дефолтном пресете).
- **Demand-тиры point**: pyr/EVSM массивы per-tier лениво, ёмкость в блоках, чанки {2,3,4}, рост = новый glTexStorage3D + glCopyImageSubData слоёв + ребилд cube-вьюхи; ShadowBaker пролог: ShadowVramBudget.updatePointCaps() (одобрение роста ДО раздачи) → POINT_TIER_CAP; acquire-пути cap-aware (ownership выше капа чтится); acquireSpareTile: worst-tier-first, но lowest-index-in-tier (было backward = сразу верхний блок). Rank→tier маппинг НЕ тронут (GLSL ABI).
- Ревью 20 агентов (5 линз + опровержение): 12 подтверждённых → 6 дефектов, ВСЕ исправлены: (A) OOM-чек glTexStorage3D через GL_TEXTURE_IMMUTABLE_FORMAT + retry-cooldown 600 флашей; (B) бюджет считался до освобождения старой цепочки → resident add-back; (C) temp-цена на первый одобренный чанк любого тира; (D) floor капа = max owned блок (ShadowBaker.ownedPointBlocks), committed-учёт в headroom; (E) inert()-фильтр (провал компиляции compute) не ограничивает пул; (F) current=null.
- **E2E PASS (телеметрия, 2026-07-19 20:06)**: кламп сработал («face size 4096 needs 6527 MiB > chain budget 3903 MiB; clamped to 2048»), депт 2×576, только t0-фильтры x12/12 (127+255+96), спот-цепочка при появлении спота — 4096 без клампа (влезла в бюджет, live+static+pyr+evsm ≈ 3.1 GB). ИТОГ СЕССИИ (58 vram-окон): free min/max = **5302/8740 MiB** (до фикса: 258-490), **эвикций НОЛЬ за всю сессию** (до: +54/+65 каждую секунду), GPU-пассы суб-мс. Лог: run/runclient-console-fix-e2e-2026-07-19.log.
- ГОТЧА ПОВТОРИЛАСЬ: publish той же версии 1.2 в mavenLocal → loom-кэш аддона подсунул СТАРЫЙ ремапнутый jar (первый e2e-заход юзера 20:04 тестировал старое ядро, 9.6 GB повторились). Лечение: rm -rf .gradle/loom-cache/remapped_mods/remapped/org/qualet/irl-core-*/1.2 + перезапуск. ПОМНИТЬ при каждом re-publish без бампа версии.
- core 1.2 в mavenLocal теперь = W2 + этот фикс. Открытый гейт юзера: субъективный FPS у лампы + визуал теней (тир-0 теперь 2048 вместо 4096 на ULTRA/12GB — вблизи может быть чуть заметно) + смена пресетов туда-обратно.
**ВИЗУАЛ-ГЕЙТ ЗАКРЫТ 2026-07-20 (PASS).** Юзер прогнал ULTRA вживую: кламп сработал (`face size 4096 needs 6527 MiB > chain budget 3574 MiB; clamped to 2048`, спот остался 4096 — влез в бюджет), free 7.1/12.3 GB, эвикции +0, тир-0 2048 вблизи лампы принят на глаз. Трек VRAM закрыт целиком. ВНИМАНИЕ: core теперь **1.1.3**, не 1.2 (артефакт 1.2 УДАЛЁН из mavenLocal как осиротевший) → [[reference-core-versioning]].

Отложено (не рычаг сейчас): static-слой (кандидат 3), полурез EVSM point (кандидат 4).

## Инструментарий (готов, ЗАКОММИЧЕН)
- Alloc-телеметрия core db731ac: `[irl-core] alloc:/quality:` безусловно (System.out), видна и в аддоне, и в редакторе.
- Редактор-профайлер irlights 4d5b93c: runtime-тумблер ImGui «perf», hold-bake, `[irl-redactor] gpu:/bake:/vram:`.
- GPU-семплер: scratchpad gpu-sampler.ps1 (Get-Counter Dedicated Usage 1 Гц, >150 MB).
- Запуск аддона: JAVA_HOME=JDK21 (систем. JAVA_HOME смотрит на JDK 8!), `./gradlew runClient -Pmc=1.20.4 -PclientJvmArgs="-Dirlite.profileVl=true"`. Готча: застрявший gradle-даймон держит run/.fabric/processedMods/*.jar → «файл занят» на ремапе; лечение: убить даймон, удалить jar.

Связь: [[plan-shadow-bake-track]] (F11-аномалия закрыта этим вердиктом), [[plan-partial-tile-filter]], [[addon-shadows]], [[project-red-line-2026-07-19]] (mavenLocal core 1.2 всё ещё с W2-правками).
