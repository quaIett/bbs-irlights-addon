---
name: project-replay-selection-ux
description: "09-17: новый UX Lit/Outline Replays, только1.20.4; keyframe+выбор без тоглов, build/1306 checks PASS; runtime у пользователя."
---

ЗАДАЧА09-17: пользователь перед релизом запросил UX Lit/Outline: создать ключ → выбрать реплеи. Предложение принято с поправкой: порядок треков/групп НЕ менять. Только1.20.4; после пользовательской приёмки порт UX на остальные версии. Самостоятельный irlights editor/DOF/оптимизации не входят. Runtime делает пользователь; в конце запуск runClient, без UI automation.

РЕАЛИЗАЦИЯ: существующий _wt-addon-1.20.x/master (общий source1.20.x, построена только1.20.4), основной bbs-irlights-addon/perf/hard-shadows Java НЕ менялся. UIReplaySelectionKeyframeFactory: Choose replays, Nobody, Use light settings; тоглы убраны из ключа. Родные graph.setValue/undo/auto-key. Порядок LightTrackLayout не изменён. JSON прежних строковых треков расширен mode=selected/inherit; отсутствие mode=LEGACY, старые списки/сцены не мигрируются при открытии. Form picker по-прежнему пишет legacy default.

РЕНДЕР: LightEffectsRegistration считывает mode вместе со списком live-формы. SELECTED включает фильтр; для Outline также customOutline+outline, target=all (список является целью, включая BlockForms), числовой вид/Front/Glow из локальных свойств. Lit/Outline независимы. INHERIT использует getOriginalValue списка и прежние тоглы. Пустой SELECTED=никто. Persistent switches не меняются. Причина необходимости захода в форму: старый UI менял authoring-form booleans, а BaseFilmController.createEntities держит FormUtils.copy; новые ключи применяются на копию обычным playback, без синхронизации тоглов.

ВРЕМЯ: новый ReplaySelectionTrackMixin→PropertyTrack.apply вызывает ReplaySelectionPlayback.resetBeforeFirst. Для первого нового ключа до его tick runtime списка сбрасывается; до него действуют исходные настройки. Legacy backward extrapolation сохранена. UI auto-key preview использует то же условие. Между ключами обычные ступенчатые строки BBS.

ПРОВЕРКИ: build -Pmc=1.20.4 PASS; ProfilesBbsTest1306 (было1228), оба типа света, live-copy без пересоздания, save/load, наследование default list, независимые режимы, пустой список, назад по времени, восстановление undo snapshot,16комбинаций старых тоглов, global/local Outline OFF, target conflict, missing/foreign actors. Headless PropertyTrack wrapper вызывает production hook, сам Mixin/UI не эмулирует. 11 ASM anchors PASS. Bundle/core1.1.7 main SHA/7patches/mixin classes PASS; build/replay-ux/bundle.json, build/profiles-bbs-test. docs/replay-selection-ux.md. Шейдеры/core не менялись, новых GPU тестов нет. Коммит/push НЕ выполнялись.

ПРОДОЛЖЕНИЕ: пользовательская проверка1.20.4. Особенно новый источник без захода в форму, два независимых списка, immediate preview, Nobody/inherit, auto-key/undo. До приёмки не портировать. Запуск Git Bash runClient -Pmc=1.20.4 -PrunDir=../bbs-irlights-addon/run из _wt-addon-1.20.x, лог в основном addon/run/runclient-console.log; мир/активный пак не переключались.

ЗАПУСК09-17 17:29: runClient1.20.4 успешно стартовал, Java PID10448; IRLite/core1.1.7, OpenGL RTX3060 и Sound engine started подтверждены, ERROR/FATAL/InvalidInjection в логе отсутствуют на17:30. Клиент оставлен пользователю; визуальный PASS не заявлен.

ПОРТ09-17: пользователь после1.20.4 ответил «Отлично, портируем для1.20.1/1.21.1/1.21.11». Итерация1.20.4 принята для тиража. Новый UX перенесён в существующие _wt-addon-1.21.1 и _wt-addon-1.21.11; 1.20.1 использует общий _wt-addon-1.20.x. Перед копированием четыре изменяемых файла сверены с прежней базой1.20.x: совпали; перенесены3изменённых+2новых Java UX, ProfilesBbsTest и ReplayPortApiCheck, в mixin config только точечная регистрация нового хука. Версионные рендереры/LightTrackLayout/шейдеры/core не менялись.

ПРОВЕРКИ ПОРТА: 3build PASS; по1306BBS checks и11ASM anchors на1.20.1/1.21.1/1.21.11 (3918+33). По каждой bundle/core SHA/7patches/mixins PASS. Общие UX-файлы и layout идентичны всем линиям. Выдача BBS/deliverables/replay-ux-1.1.7/<mc>/irlite-1.1.7+mc<mc>.jar + report.json/api-report.json/bundle.json; добавлена принятая1.20.4, все4SHA проверены. Docs/replay-selection-ux.md в3worktrees и README выдачи. Коммит/push отсутствуют. Другие существовавшие dirty/staged edits сохранены.

ПРОДОЛЖЕНИЕ ПОСЛЕ ПОРТА: пользовательские runtime-приёмки нового UX на1.21.11/1.21.1/1.20.1 OPEN. В конце запускается1.21.11 из _wt-addon-1.21.11 с -PrunDir=runs/1.21.11, лог build/replay-ux-port/runclient-1.21.11.log, без quickplay/UI automation. MavenLocal core1.1.7 теперь линия1.21.11; перед другими MC republish нужной core. Самостоятельный irlights editor (шаг3 roadmap) пока НЕ обновлялся.

ЗАПУСК ПОРТА09-17 17:38: runClient1.21.11 успешно стартовал, Java PID7232; IRLite1.1.7+mc1.21.11/core1.1.7, OpenGL RTX3060, Sound engine started подтверждены. Клиент оставлен пользователю. Старое нефатальное отсутствие BBS categories.json и dev-Realms auth присутствуют; Mixin/compile/fatal ошибок нет. Runtime UX ещё ждёт пользователя;1.20.1/1.21.1 на этом шаге не запускались.


ПРИЁМКА09-17: после запуска runClient1.21.11 пользователь сообщил «проверил, всё норм». Runtime нового UX на1.21.11 PASS по контексту запущенного клиента.1.20.4 ранее принята для тиража. Проверки новых UX-портов1.20.1/1.21.1 пользователем отдельно не подтверждены; не считать весь runtime rollout закрытым. Сборки/автоматика всех версий PASS. Коммит/push не поручены и не выполнены.

ЗАКРЫТИЕ09-17: пользователь явно подтвердил оставшиеся1.20.1/1.21.1 («коммитим, там тоже всё норм») и разрешил коммит. Новый UX принят на всех4MC:1.20.4/1.20.1/1.21.1/1.21.11; build/автоматика/runtime PASS. Чекпоинт включает новый UX и необходимую ранее незакоммиченную основу replay-портов в addon master/port1.21.1/port1.21.11 и core1.21.1/1.21.11. Экспериментальные perf/hard-shadows checkout не включаются, push не запрошен. Следующий пункт дорожной карты — актуализация standalone irlights editor, ещё НЕ начат.
