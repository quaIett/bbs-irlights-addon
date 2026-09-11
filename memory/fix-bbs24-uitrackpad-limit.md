---
name: fix-bbs24-uitrackpad-limit
description: BBS 2.4-1.20.1 сделал UITrackpad extends UINumericInput<T> -> NoSuchMethodError limit(DD); фикс = рефлексивный limit в IrliteTrackpads + байткодный чекер дрейфа
metadata:
  node_type: memory
  type: project
  originSessionId: 684e3e04-73fd-4fb0-bc27-c35e74dba103
  modified: 2026-08-10T00:13:09.663Z
---

BBS 2.4-1.20.1 (jar от 2026-08-03) провёл рефактор: `UITrackpad extends UINumericInput<UITrackpad>`, все `limit()` уехали в generic-родителя (erased return = `UINumericInput`). В 2.3.x и в 2.4-**1.20.4** — старая форма (`extends UIBaseTextbox`, limit прямо в UITrackpad). Bridge-методов нет.

Следствие: скомпилированный call site `.limit(double,double)` жёстко привязан к той BBS, против которой собран, и падает `NoSuchMethodError` на другой (краш 2026-08-04 при открытии панели точечного света).

**Why:** ось BBS 2.3.1 (сборка, `libs/bbs-2.3.1-1.20.1.jar`) != ось BBS в рантайме у пользователей; аддон декларирует `"bbs": "*"`.

**How to apply:** BBS-методы с меняющимся объявляющим классом/erased-возвратом звать рефлексивно — `IrliteTrackpads.create(callback, min, max)` (кэшированный `UITrackpad.class.getMethod("limit", double, double)`, fallback = без клампа). Прямой `.limit(...)` в панелях запрещён. Тот же приём, что и [[project-spotlight-gobo-cookie-plan]]-независимые пробы в `IrliteBbsCompat`.

Статус: фикс висел в рабочем дереве незакоммиченным до 2026-08-10, теперь `master 5cc4e29` (3 файла: `IrliteTrackpads` + обе панели; `compileClientJava -Pmc=1.20.4` зелёный). Не запушен.

Инструмент проверки дрейфа (ASM, single-file Java, scratchpad `CheckRefs.java`): индексирует BBS-jar (+nested), сканирует mod-jar (+nested core), печатает нерезолвящиеся mchorse-ссылки. Готчи: цепочку наследования обрывать на `java/lang/Object` (иначе всё «резолвится»); неизвестные не-mchorse супертипы = «не проверить» -> ok. Прогон 1.1.5-fix: 0 нерезолвленных против 2.4-1.20.1 / 2.3.1-1.20.1 / 2.4-1.20.4; против 2.3-1.20.1 только `UISection` (штатно гейтится `IrliteBbsCompat.SECTIONS`). Сборка = `JAVA_HOME=jdk-21 ./gradlew build -Pmc=1.20.1` (loom 1.15.5 требует JVM 21, хотя таргет 17).
