---
name: project-per-light-replays-version-ports
description: "09-17: Lit/Outline Replays на 1.20.1/1.20.4/1.21.1/1.21.11, сборки и автоматические проверки PASS; runtime у пользователя."
---

ЗАДАЧА 09-17: шаг 2 roadmap, порт на четыре версии по сложности; пользователь сам проверяет игру, в конце попросил runClient. DONE код/автоматика; игровая приёмка OPEN. Коммит/push не делались, шаг 3 (редакторы) не начат. DOF не менялся.

ПОРЯДОК: 1.21.11 → 1.21.1 → 1.20.1 → 1.20.4. Продукт 1.1.7, BBS 2.6, core 1.1.7; 96Б источники + 112Б профили, independent Lit/Outline, own VL/Outline, штатный BBS picker/Light tracks. Семь патчей = c154a2d (с IRP RG32F fix). Отдельные Outline GPU-тесты не добавлялись.

РАБОЧИЕ ДЕРЕВЬЯ (все под BBS/): _wt-addon-1.21.11(port/1.21.11), _wt-irl-core-1.21.11(1.21.11); _wt-addon-1.21.1(port/1.21.1), _wt-irl-core-1.21.1(1.21.1); _wt-addon-1.20.x(master), _wt-irl-core-1.20.x(main). Использованы существующие ветки, новых веток нет. Основные addon/core checkout perf/hard-shadows и их чужие dirty edits сохранены; hard-эксперимент исключён из выдачи. На master в _wt-addon-1.20.x лежит НЕЗАКОММИЧЕННАЯ копия shader-порта c154a2d плюс новые проверки — учитывать при будущем объединении, не коммитить повторно вслепую.

1.21.11: raw-GL renderer/версионные camera hooks сохранены; новый ReplayOutlineContext использует com.mojang.blaze3d.opengl.GlStateManager._enableCull/_disableCull (RenderSystem API удалён). BBS Mob/Item queue synchronous, deferred translucent queue отключена самой базой BBS; тэг оборачивает фактический draw. Старый ShadowRenderer ветки сохранён, чужой 1.20 cull-fix поверх raw renderer не переносился.
1.21.1: BBS 2.2.1 → локальный bbs-2.6-1.21.1.jar из bbs-fs-1.21.1-build/build/libs; обязательная синхронизация общей Java/UBO/core-базы. Сохранены RenderTickCounter.getTickDelta, Tessellator21/Matrix4fc и context.world positional fix. Identifier.of, EquipmentSlot, VertexConsumer(float) адаптированы. Silhouette audit whitelist НЕ расширен: 2.6-1.21.1 → UNKNOWN/full bake, до отдельного аудита.

ПРОВЕРКИ: 4 build PASS; 3 core publish PASS; по 1228 real-BBS checks (4912 суммарно), по 10 ASM API anchors (enum/категории/track/redirect/Iris uniform/MC camera) PASS. GPU core12111: upload 2305073 assertions/201 uploads, profiles 18073 PASS RTX3060. LightBuffer/LightProfile/LightProfilesBuffer/LightRegistry/VlGlobalsBuffer побайтно идентичны (без EOL) на трёх линиях. JAR: ровно один core, SHA совпал с per-MC build, Java61/65, семь embedded patches/entrypoint/mixin classes PASS. Шейдерная логика не изменялась после прошлого shader-port GPU/round-trip, проверена идентичность канону.

ВЫДАЧА: BBS/deliverables/lit-outline-replays-1.1.7/<mc>/irlite-1.1.7+mc<mc>.jar. Все 4 SHA после копирования сверены. docs/per-light-replays-version-ports.md, build/replay-version-port/*.json в основном addon; отчёты продублированы в deliverables. tools/verify-profiles-bbs.ps1 расширен на 4 версии + ReplayPortApiCheck.java; tools/verify-replay-port.py проверяет bundle per-MC.

ЗАПУСК: обычный runClient1.20.4 из _wt-addon-1.20.x, -PrunDir=../bbs-irlights-addon/run, JDK21, без quickplay. Общая папка игры/сцены сохранены, активный IterationRP_IRLights_Replays не переключался. Лог bbs-irlights-addon/run/runclient-console.log. Абсолютный Windows runDir ломает Loom downloadAssets (дублирование корня), использовать относительный. MavenLocal в конце = core1.1.7 main; слот1.1.7-hard не менялся. Перед запуском121x заново publish соответствующий core (координата общая).

ПРОДОЛЖЕНИЕ: пользовательская игровая проверка, затем коммит только по подтверждению. Следующий пункт плана — редакторы; не начинать автоматически.

СТАРТ 09-17 16:40: runClient1.20.4 успешно запущен, Java PID9732; лог подтверждает irlite1.1.7+mc1.20.4/core1.1.7, OpenGL RTX3060 и Sound engine started. Автомат не входил в мир и не управлял UI.


ДОПОЛНЕНИЕ 09-17 16:49: пользователь подчеркнул необходимость проверки остальных MC. Выполнен последовательный стартовый runClient smoke 1.21.11 → 1.21.1 → 1.20.1, затем повторно открыт 1.21.11 для пользователя. Все три загрузили IRLite/core1.1.7, Iris и ComplementaryReimagined_IRLights_Replays, Sound engine started; фатальных Mixin/шейдерных ошибок старта нет. Миры/UI не управлялись; визуальная приёмка Lit/Outline на каждой версии всё ещё OPEN, не считать startup функциональным тестом. Тестовые клиенты закрывались через CloseMainWindow, не kill.
Папки: _wt-addon-1.21.11/runs/1.21.11; _wt-addon-1.21.1/run; bbs-irlights-addon/runs/1.20.1. В каждой семь новых *_IRLights_Replays, свежий Complementary получен PatchHarness из Original + канонический patch (50 ops). Прежний iris.properties сохранён как iris.properties.before-replay-port, выбран свежий CR. Общий run1.20.4 и его миры не тронуты.
Нефатальные сообщения: отсутствие categories.json в тестовом BBS-профиле; на1.20.1/Iris1.7.2 штатные CR uniforms BIOME_PALE_GARDEN/endFlashIntensity не разрешаются (эти строки есть в Original), фатального shader compile/pipeline failure нет. Не выдавать эти предупреждения за дефекты replay-порта или за визуальный PASS.
Отчёты: build/replay-version-port/startup-other-versions.json и startup-<mc>.log; копии в deliverables/lit-outline-replays-1.1.7. Последний MavenLocal1.1.7 теперь линия1.21.11 (заменяет прежнюю запись main); перед запуском1.20.x/1.21.1 republish нужной core-ветки. Сейчас открыт runClient1.21.11, без quickplay.

ПРИЁМКА 09-17: пользователь явно подтвердил «1.21.11 успешно». Игровая проверка Lit/Outline Replays на1.21.11 PASS. Следующая ручная проверка1.21.1; её core1.1.7 republish и runClient запущен. 1.20.1/1.21.1 ещё без пользовательской приёмки; не закрывать весь шаг2. Последний MavenLocal = линия1.21.1.

ПРИЁМКА 09-17: пользователь явно подтвердил «1.21.1 успешно». Игровые проверки Lit/Outline на1.21.11 и1.21.1 PASS. Следующий ручной тест1.20.1: core main1.1.7 republish, runClient1.20.1 в отдельной runs/1.20.1. Пользовательского подтверждения1.20.1 ещё нет;1.20.4 ранее запускался, явного сообщения о приёмке в этой задаче нет. Последний MavenLocal = main1.20.x. Коммитов нет.

ПРИЁМКА 09-17: пользователь явно подтвердил «1.20.1 успешно». Игровые проверки Lit/Outline на1.21.11,1.21.1,1.20.1 PASS. Для финального подтверждения1.20.4 повторно запущен её runClient из _wt-addon-1.20.x с общей bbs-irlights-addon/run. Только приёмка1.20.4 ещё без явного подтверждения в этой задаче. MavenLocal остаётся main1.20.x/core1.1.7. Коммитов нет.

ЗАКРЫТИЕ ШАГА2, 09-17: пользователь явно подтвердил «1.20.4 успешно». Все четыре игровые приёмки Lit/Outline Replays (1.21.11,1.21.1,1.20.1,1.20.4) PASS по сообщениям пользователя. Шаг2 дорожной карты ЗАВЕРШЁН: код, сборки, автоматика и пользовательский runtime. Следующий этап — шаг3, актуализация редакторов; пока НЕ начат. Коммит/push в этой задаче не выполнялись, изменения остаются в описанных worktree и требуют отдельного подтверждения на коммит. Последний запущенный клиент1.20.4 не закрывался агентом после приёмки. MavenLocal = main1.20.x/core1.1.7.
