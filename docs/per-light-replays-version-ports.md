# Lit Replays / Outline Replays: версии Minecraft

Дата: 2026-09-17. Шаг 2 дорожной карты: код, автоматические проверки и игровые приёмки всех четырёх версий завершены; пользователь подтвердил успех. Коммитов и push в этой задаче нет.

Порядок: 1.21.11 → 1.21.1 → проверка 1.20.1 → проверка 1.20.4. Все сборки: IRLite 1.1.7, BBS 2.6, вложенный core 1.1.7. Независимые Lit/Outline списки, персональные VL/Outline, пикер BBS и ключи Light перенесены без изменения контракта: источник 96 Б, профиль 112 Б. Семь патчей совпадают с c154a2d, включая исправление IterationRP.

## Размещение

- `_wt-addon-1.21.11` / `port/1.21.11`, `_wt-irl-core-1.21.11` / `1.21.11`: сохранён raw-GL backend. ReplayOutlineContext использует GlStateManager._enableCull/_disableCull вместо удалённых RenderSystem API. Существующий ShadowRenderer 1.21.11 сохранён. Проверены синхронные BBS Mob/Item draws и addDynamicUniforms Iris 1.10.7.
- `_wt-addon-1.21.1` / `port/1.21.1`, `_wt-irl-core-1.21.1` / `1.21.1`: старая BBS 2.2.1-база обновлена до BBS 2.6; синхронизированы необходимые общие изменения core/UI/рендера. Сохранены RenderTickCounter, Tessellator 1.21, Matrix4fc Iris 1.8.8 и world-stack позиционирование. Адаптированы Identifier.of, EquipmentSlot и VertexConsumer(float). Консервативный silhouette audit не расширен: на BBS 2.6-1.21.1 ревизии UNKNOWN, корректный полный bake вместо неподтверждённого reuse.
- `_wt-addon-1.20.x` / `master`, `_wt-irl-core-1.20.x` / `main`: существующая реализация плюс порт семи патчей и проверки матрицы. Основные грязные checkout `perf/hard-shadows` addon/core сохранены; эксперимент hard-shadows в эти сборки не включён. Редактор и DOF не менялись.

Пути указаны относительно `C:/Users/qualet/Documents/Project/Minecraft/BBS`. Существующие ветки используются в отдельных worktree, новых веток под сессию нет.

## Проверки

- `build`: PASS на 1.20.1, 1.20.4, 1.21.1, 1.21.11; core publish PASS на всех трёх линиях.
- `verify-profiles-bbs.ps1 -MinecraftVersion <mc>`: 1228 BBS-проверок на каждой версии (сохранение/copy, legacy формы, ключи/списки, playback); всего 4912.
- `ReplayPortApiCheck`: по 10 bytecode-проверок на версию — enum/категория Light, TrackCatalog, redirect FormRenderer, Iris uniform, GameRenderer и точка upload. Это статическая проверка настоящих JAR, не запуск игры.
- `verify-upload-gl.ps1` на core 1.21.11: 2305073 assertions / 201 uploads; профили registry/GPU 18073 PASS на RTX 3060. Исходники LightBuffer/LightProfile/LightProfilesBuffer/LightRegistry/VlGlobalsBuffer идентичны на всех трёх линиях.
- `verify-replay-port.py`: единственный вложенный core побайтно совпадает с соответствующей сборкой; Java 17/21, семь embedded patches, entrypoint и наличие всех mixin-классов PASS.
- Отчёты: `bbs-irlights-addon/build/replay-version-port/*.json`; копии рядом с выдачей.
- Выдача: `deliverables/lit-outline-replays-1.1.7/<mc>/irlite-1.1.7+mc<mc>.jar`, SHA256 сверены после копирования.

## Продолжение

Пользователь проверяет игру самостоятельно; computer-use не использовался. После визуальной приёмки — коммит по подтверждению пользователя, затем шаг 3 (редакторы). Пункты оптимизации/релиз 2.0 не начинались.

Для запуска проверенной 1.20.4: из `_wt-addon-1.20.x` выполнить `gradlew.bat runClient -Pmc=1.20.4 -PrunDir=../bbs-irlights-addon/run --console=plain` на JDK21. Относительный runDir обязателен: Loom некорректно объединяет абсолютный Windows-путь с корнем проекта. Лог — `bbs-irlights-addon/run/runclient-console.log`; без quickplay. MavenLocal в конце содержит обычный core 1.1.7 линии main; 1.1.7-hard не перезаписывался.

Запуск подтверждён 09-17 в 16:40: PID 9732, irlite 1.1.7+mc1.20.4 / core 1.1.7, OpenGL RTX3060 и Sound engine started. Игровая проверка не проводилась.


ДОПОЛНЕНИЕ 09-17 16:49: пользователь подчеркнул необходимость проверки остальных MC. Выполнен последовательный стартовый runClient smoke 1.21.11 → 1.21.1 → 1.20.1, затем повторно открыт 1.21.11 для пользователя. Все три загрузили IRLite/core1.1.7, Iris и ComplementaryReimagined_IRLights_Replays, Sound engine started; фатальных Mixin/шейдерных ошибок старта нет. Миры/UI не управлялись; визуальная приёмка Lit/Outline на каждой версии всё ещё OPEN, не считать startup функциональным тестом. Тестовые клиенты закрывались через CloseMainWindow, не kill.
Папки: _wt-addon-1.21.11/runs/1.21.11; _wt-addon-1.21.1/run; bbs-irlights-addon/runs/1.20.1. В каждой семь новых *_IRLights_Replays, свежий Complementary получен PatchHarness из Original + канонический patch (50 ops). Прежний iris.properties сохранён как iris.properties.before-replay-port, выбран свежий CR. Общий run1.20.4 и его миры не тронуты.
Нефатальные сообщения: отсутствие categories.json в тестовом BBS-профиле; на1.20.1/Iris1.7.2 штатные CR uniforms BIOME_PALE_GARDEN/endFlashIntensity не разрешаются (эти строки есть в Original), фатального shader compile/pipeline failure нет. Не выдавать эти предупреждения за дефекты replay-порта или за визуальный PASS.
Отчёты: build/replay-version-port/startup-other-versions.json и startup-<mc>.log; копии в deliverables/lit-outline-replays-1.1.7. Последний MavenLocal1.1.7 теперь линия1.21.11 (заменяет прежнюю запись main); перед запуском1.20.x/1.21.1 republish нужной core-ветки. Сейчас открыт runClient1.21.11, без quickplay.

ПРИЁМКА 09-17: пользователь явно подтвердил «1.21.11 успешно». Игровая проверка Lit/Outline Replays на1.21.11 PASS. Следующая ручная проверка1.21.1; её core1.1.7 republish и runClient запущен. 1.20.1/1.21.1 ещё без пользовательской приёмки; не закрывать весь шаг2. Последний MavenLocal = линия1.21.1.

ПРИЁМКА 09-17: пользователь явно подтвердил «1.21.1 успешно». Игровые проверки Lit/Outline на1.21.11 и1.21.1 PASS. Следующий ручной тест1.20.1: core main1.1.7 republish, runClient1.20.1 в отдельной runs/1.20.1. Пользовательского подтверждения1.20.1 ещё нет;1.20.4 ранее запускался, явного сообщения о приёмке в этой задаче нет. Последний MavenLocal = main1.20.x. Коммитов нет.

ПРИЁМКА 09-17: пользователь явно подтвердил «1.20.1 успешно». Игровые проверки Lit/Outline на1.21.11,1.21.1,1.20.1 PASS. Для финального подтверждения1.20.4 повторно запущен её runClient из _wt-addon-1.20.x с общей bbs-irlights-addon/run. Только приёмка1.20.4 ещё без явного подтверждения в этой задаче. MavenLocal остаётся main1.20.x/core1.1.7. Коммитов нет.

ЗАКРЫТИЕ ШАГА2, 09-17: пользователь явно подтвердил «1.20.4 успешно». Все четыре игровые приёмки Lit/Outline Replays (1.21.11,1.21.1,1.20.1,1.20.4) PASS по сообщениям пользователя. Шаг2 дорожной карты ЗАВЕРШЁН: код, сборки, автоматика и пользовательский runtime. Следующий этап — шаг3, актуализация редакторов; пока НЕ начат. Коммит/push в этой задаче не выполнялись, изменения остаются в описанных worktree и требуют отдельного подтверждения на коммит. Последний запущенный клиент1.20.4 не закрывался агентом после приёмки. MavenLocal = main1.20.x/core1.1.7.
