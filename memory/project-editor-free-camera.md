---
name: project-editor-free-camera
description: "Редактор (irlights, main): свободная камера (free-fly) ВНУТРИ открытого ImGui-редактора. DONE + commit 9120755 (2026-07-22). Default-on при открытии редактора; F toggle raw; hold-ЛКМ обзор с cursor-grab; WASD/Space/Shift raw; колесо=скорость; 'Speed x.x' правый-низ. Порт на порт-ветки редактора — open."
metadata:
  node_type: memory
  type: project
  originSessionId: 7b96d60c-6f09-4656-93fa-4c71714ff8ac
  modified: 2026-07-22T19:28:58.416Z
---

Свободная камера редактора DONE + commit **9120755** на ветке **main** редактора (2026-07-22, юзер принял рантайм, «коммитим»). Канон редактора = [[project-irlite-base-ported]]; активная UI-фаза = [[project-editor-redesign]]; гайды = [[project-editor-guide-overlay]].

**Модель (ключевое): freecam живёт ВНУТРИ открытого ImGui-редактора, НЕ в мире.**
- **Default-on**: включается сама в момент открытия редактора (edge open в `onEndClientTick`, `fcEditorWasOpen`). Закрытие редактора → `disable()` (off + сброс сохранённой позиции). Клавиша открытия редактора = **J**.
- **Toggle = F**, читается RAW (`glfwGetKey` по bound key `free_camera`, дефолт F) когда `LightEditorScreen.isOverlayActive() && !ImGui wantsKeyboard`. Ванильный keybind queue мёртв за Screen — поэтому **нет конфликта** с ванильным swap-offhand (F): при открытом редакторе ванильные бинды не тикают. keybind зарегистрирован (перебиндируемость), но toggle через raw-edge.
- **Move**: WASD по полному взгляду (вкл pitch), Space/Shift = мировая вертикаль. RAW (`glfwGetKey` bound forward/back/left/right/jump/sneakKey), gate `!ImGui wantsKeyboard`. Работает за экраном (keybind.isPressed там false). Per-frame интеграция (real-time dt из nanoTime, `speed*dt*20` бл/тик).
- **Look = hold-ЛКМ** над вьюпортом: старт при `lmb && !ImGui wantsMouse && !GuideOverlay.isHandleActive()` → `glfwSetInputMode CURSOR_DISABLED` (grab) + сохранить cursor pos; delta → `player.changeLookDirection(dx*g, dy*g)` (g = vanilla sens factor `(sens*.6+.2)^3*8`); отпустил → `CURSOR_NORMAL` + восстановить pos. Над панелью ЛКМ = обычный клик UI (wantsMouse true).
- **Speed = колесо** (`FreeCamScrollMixin` на `Mouse.onMouseScroll` HEAD cancellable, gate `isActive && !wantsMouse`), шаг 0.2, диапазон 0.2..10.0, дефолт 1.0. HUD **«Speed x.x»** правый нижний угол (`HudRenderCallback`), без заголовка.

**Файлы (все `irlights/src/client/java/org/qualet/irlredactor/`):**
- **НОВЫЙ** `client/FreeCamera.java` — всё состояние+логика (static): pos/speed/freeze/look. `posValid` = позиция сохраняется при F off→on в рамках ОДНОГО открытия редактора, сброс в `disable()` (закрытие). `isKeyDown(handle, KeyBinding)` = raw poll bound key.
- **НОВЫЙ** `mixin/client/CameraFreeMixin` — `@Inject Camera.update(...)ZZF TAIL`, при active: `advance()` + `@Shadow setPos(...)` (переопределяет ТОЛЬКО позицию; **rotation ванильная** = player look).
- **НОВЫЙ** `mixin/client/FreeCamInputMixin` — `@Inject KeyboardInput.tick(ZF) TAIL`, гасит `Input.movementForward/Sideways/pressing*/jumping/sneaking` при active (нужно для overlay/replay-пути где screen==null).
- **НОВЫЙ** `mixin/client/FreeCamScrollMixin` — колесо→speed.
- `client/IRLRedactorClient` — регистр keybind F, HudRenderCallback, auto-enable-on-open + toggle + `tickFreeze` в `onEndClientTick`, disable в DISCONNECT.
- `editor/GuideOverlay` — +static `handleActive` (hot-хват ИЛИ drag), getter `isHandleActive()`; выставляется в `frame()`. **Приоритет драга гайда спота над look**: тянешь за кольцо/диск → look не стартует.

**Заморозка игрока (client-side)**: `tickFreeze()` в END_CLIENT_TICK — `setVelocity(0)` + `setPosition(freezeXYZ)` + `fallDistance=0`; + input-suppress миксин. На сервер уходит только «стоящий» игрок.

**Билд/рантайм**: republish core **main** (Loom 1.9.2, JDK17) в mavenLocal ПЕРЕД compile (гоча из [[project-editor-redesign]] — mavenLocal мог быть загрязнён core-1.21.1/Loom1.15.5); `gradlew build` PASS; runClient чистый (0 mixin/exception). Маппинги 1.20.4 подтверждены: `Camera.setPos(DDD)` protected, `KeyboardInput.tick(ZF)`, `GameOptions.forwardKey/backKey/leftKey/rightKey/jumpKey/sneakKey`, `Entity.changeLookDirection(DD)`.

**Открытый хвост**: порт на порт-ветки редактора (1.20.1/1.21.1/1.21.4/1.21.11) — по команде юзера (директива: порт редактора в самом конце). Гоча портов: input-обёртки разные (1.20.4 raw events vs 1.21.11 record-обёртки MouseInput/KeyInput) — см. [[reference-edit-routing-by-area]]; `Camera.update`/`KeyboardInput.tick` дескрипторы могут отличаться на 1.21.x.
