---
name: reference-debug-ui-flags
description: "Дебаг-UI/логи скрыты по умолчанию в аддоне и редакторе; dev-флаги возврата -Dirlite.debug / -Dirlredactor.debug. Коммиты addon 6893208, editor 1713afe."
metadata:
  node_type: memory
  mod_scope: addon+editor
  type: reference
  originSessionId: 8775a629-8b86-4962-ae4c-5a32fe7c7afa
  modified: 2026-07-23T00:30:24.781Z
---

Весь дебаг ОТКЛЮЧЁН по умолчанию (директива юзера 2026-07-23). Код НЕ удалён — только скрыт/приглушён, вернуть флагом. Связано: [[plan-vl-profiler]], [[addon-ui-config]], [[addon-architecture]].

ФЛАГИ ВОЗВРАТА (Boolean.getBoolean, boot-time):
- `-Dirlite.debug=true` — АДДОН: показать секцию «Debug» (кнопка Show performance overlay) в настройках IRLights (UIDebugSection). Гейт в самом `UIDebugSection.append` (ранний return).
- `-Dirlredactor.debug=true` — РЕДАКТОР: показать подсекцию «perf» в LightEditorPanel (toggle perfProfiler + holdBake/holdBakeOnJoin + кнопка Bake now). Гейт = `LightEditorPanel.DEBUG_UI`.
- Сам профайлер БЕЗ изменений: `-Dirlite.profileVl` / `-Dirlredactor.profileVl` (VlProfiler collecting) — как раньше. System.out профайлера уже был под гейтом enabled -> по умолчанию молчит.

ПОВЕДЕНЧЕСКАЯ СМЕНА (редактор): `LightConfig.holdBakeOnJoin` дефолт true->false. Раньше вход в мир ЗАМОРАЖИВАЛ бейк теней (дев-режим замеров, ждал Bake now); теперь нормальный бейк на join (рантайм-подтверждено: point depth atlas аллоцируется при входе). `holdBake` дефолт остался false.

INFO-ЛОГИ -> DEBUG (не печатаются на info-уровне): аддон CookieArray (Cookie loaded/evicted); редактор CookieArray (Cookie loaded), LightStore (Saved/Loaded N lights), IRLRedactorMod (stub loaded). warn/error ОСТАВЛЕНЫ. Патчер-логи (UIPatcherSection аддон, PatcherPanel редактор) НЕ трогали — операционный вывод, не дебаг. Core-логи `[irl-core] alloc:` НЕ трогали (ядро вне скоупа).

Коммиты: аддон master **6893208**, редактор main **1713afe** (оба chore(debug), НЕ запушены). Компиляция обоих OK (аддон JDK21, редактор JDK17), рантайм редактора PASS. Порт-ветки НЕ тронуты (тираж по команде).
