---
name: project-light-form-item-icon
description: "Иконка light-формы (point/spot) для инвентарного айтема model-блока: DONE+коммит 3a1ad5e; хвост — cherry-pick в master."
metadata:
  node_type: memory
  type: project
  originSessionId: deecd7e7-bbd0-494b-a31e-2930a3a82923
  modified: 2026-07-19T21:06:06.318Z
---

Фича: BBS model-блок с light-формой (Point/Spotlight) как основная/inventory-форма раньше рендерился в инвентаре ПРОЗРАЧНЫМ айтемом (light-форма не имеет геометрии). Теперь в слоте рисуется иконка морфа (Icons.LIGHT / Icons.FRUSTUM), тонированная цветом света.

СТАТУС 2026-07-20: DONE, визуал-гейт юзера PASS (иконка по центру, не перевёрнута, цвет=цвету света; при сдвиге позиции света внутри блока иконка НЕ едет). ЗАКОММИЧЕНО addon 3a1ad5e, 1 файл.

Реализация (единственный файл: src/client/java/qualet/irlite/client/forms/AbstractLightFormRenderer.java):
- ветка в render3D на `context.type == FormRenderType.ITEM_INVENTORY && !isPicking()` → renderItemIcon → return (только GUI/хотбар; в руке/на земле НЕ делали — так решил юзер);
- квад рисуется BBS-пайплайном: `BBSModClient.getTextures().bindTexture(icon.texture)` (icons.png = сырой GL-текстура, не ванильный Identifier) + ванильный `GameRenderer::getPositionTexColorProgram` + POSITION_TEXTURE_COLOR + disableCull; alpha форсим 1F (полупрозрачный свет ≠ бледная иконка);
- КЛЮЧЕВОЕ: перед отрисовкой снимаем СОБСТВЕННЫЙ трансформ формы (позиция света внутри блока), иначе иконка едет вместе с ним. FormRenderer.render() правым умножением накладывает ровно `createTransform().createMatrix()` (порядок T→Rz→Ry→Rx→S у MatrixStackUtils.applyTransform и Transform.setupMatrix совпадает), поэтому `posMatrix.mul(formMatrix.invert())` отменяет его точно. `createTransform()` — protected метод суперкласса, доступен; `createMatrix()` отдаёт общий Matrices.TEMP_4F → копировать в new Matrix4f.
- модель айтема = `builtin/entity` без display-трансформа → GUI-вид фронтальный, ориентация выведена корректно с первого раза.

ГОТЧА: BBS FormUtilsClient.render() ГЛОТАЕТ исключения рендера молча (try/catch пустой) → сбой в renderItemIcon не даст лога, только пустой айтем. Проверка = визуальная.

ХВОСТ: коммит лёг на ветку `optimization/octahedral-point-shadows` (рядом с экспериментальным VRAM/shadow-треком за красной линией), НЕ на master. Это master-достойная фича аддона → cherry-pick 3a1ad5e в master по команде. См. [[project-red-line-2026-07-19]], [[addon-forms]], [[reference-edit-routing-by-area]].
