---
name: reference-chatgpt-memory-location
description: "Только ChatGPT: читать и обновлять общую память трёх проектов в .claude/projects; repo/memory — копия."
metadata:
  node_type: memory
  type: reference
  originSessionId: 2026-09-11-java-optimization-iter1
---

Инструкция исключительно для ChatGPT. Claude её не применяет: эта база уже расположена в его штатном каталоге памяти.

Пользователь 2026-09-11 явно указал хранить и обновлять проектную память здесь:
- `C:\Users\qualet\.claude\projects\C--Users-qualet-Documents-Project-Minecraft-BBS-bbs-irlights-addon`
- `C:\Users\qualet\.claude\projects\C--Users-Qualet-Documents-Project-Minecraft-BBS-irl-core`
- `C:\Users\qualet\.claude\projects\C--Users-Qualet-Documents-Project-Minecraft-BBS-irlights`

В каждом каталоге память находится в подпапке `memory`. Проверено 2026-09-11: addon/memory и core/memory — directory junction на `C:\Users\qualet\.claude\projects\C--Users-Qualet-Documents-Project-Minecraft-BBS-irlights\memory`. Это один склад; не создавать три расходящиеся копии и не менять junctions.

ChatGPT в начале работы читает MEMORY.md общего склада и релевантные тематические файлы, затем обновляет факты и состояние задачи там же. `bbs-irlights-addon/memory` внутри репозитория — обычная копия: обновлённые темы и индекс синхронизировать из общего склада при подготовке результата. Инструкции ChatGPT об этой маршрутизации всегда маркировать как применимые только к ChatGPT.

Связь: [[reference-memory-junctions]], [[feedback-memory-strict-style]].
