import { ButtonView, Command, Image, Plugin } from "ckeditor5";
import { IconCheck, IconPlus } from "ckeditor5";
import type { Editor, ModelElement, ModelPosition } from "ckeditor5";
import { toWidget, toWidgetEditable, Widget } from "ckeditor5";

/**
 * Chuyển từ PoC (feature/ckeditor5-poc) sang khung soạn câu hỏi THẬT — widget "Nhóm lựa chọn" (Trắc
 * nghiệm): dùng chung cho cả MULTIPLE_CHOICE (đúng 1 đáp án) lẫn MULTIPLE_ANSWER (nhiều đáp án) —
 * KHÔNG phân biệt bằng dropdown "loại câu hỏi", mà suy ra tự động từ SỐ LƯỢNG lựa chọn GV đánh dấu
 * đúng (1 → MULTIPLE_CHOICE; ≥2 → MULTIPLE_ANSWER) — xem CkeditorChoiceParser.java backend.
 *
 * Mỗi lựa chọn (`choiceOption`) là 1 vùng gõ chữ tự do (toWidgetEditable) nằm trong widget cha
 * `choiceGroup` (toWidget) — giống cách CKEditor5 dựng Table (table = object, ô bảng = editable
 * con), không phải kỹ thuật mới.
 *
 * Khác hẳn Ô trống — widget này KHÔNG giữ nguyên trong {@code content} khi lưu: backend bóc ra
 * thành {@code choices[]} chuẩn (đã xác nhận với người dùng — tránh hiện lựa chọn LẶP 2 LẦN phía
 * học sinh, vì MULTIPLE_CHOICE/MULTIPLE_ANSWER luôn render lựa chọn từ {@code choices[]} sẵn có).
 */
let groupIdCounter = 0;
let optionIdCounter = 0;

function generateGroupId(): string {
  groupIdCounter += 1;
  return `g${Date.now().toString(36)}${groupIdCounter}`;
}

function generateOptionId(): string {
  optionIdCounter += 1;
  return `o${Date.now().toString(36)}${optionIdCounter}`;
}

function findAncestorElement(position: ModelPosition, elementName: string): ModelElement | null {
  let node: any = position.parent;
  while (node) {
    if (node.is && node.is("element", elementName)) return node;
    node = node.parent;
  }
  return null;
}

class InsertChoiceGroupCommand extends Command {
  override execute() {
    const editor = this.editor;
    editor.model.change((writer) => {
      const group = writer.createElement("choiceGroup", { groupId: generateGroupId() });
      ["Option A", "Option B"].forEach((text) => {
        const option = writer.createElement("choiceOption", { optionId: generateOptionId(), isCorrect: false });
        writer.insertText(text, option);
        writer.append(option, group);
      });
      editor.model.insertObject(group, null, null, { setSelection: "after" });
    });
  }

  override refresh() {
    // "choiceGroup" là $blockObject — hợp lệ khi chèn làm ANH EM của đoạn văn hiện tại, KHÔNG PHẢI
    // làm con trực tiếp của đoạn văn (đoạn văn chỉ chứa chữ) — mirror WordBankWidgetPlugin.
    const model = this.editor.model;
    const selection = model.document.selection;
    const parent = selection.focus!.parent as any;
    const checkAt = parent.is("element", "$root") ? parent : parent.parent;
    this.isEnabled = !!checkAt && model.schema.checkChild(checkAt, "choiceGroup");
  }
}

/** Bổ sung 2026-11-15, đã xác nhận với người dùng — nút riêng cho câu Đúng/Sai (QuestionType.TRUE_FALSE
 * thật, khác MULTIPLE_CHOICE 2 lựa chọn thường) — chèn sẵn ĐÚNG 2 lựa chọn cố định "Đúng"/"Sai" và
 * đánh dấu `trueFalse: true` lên nhóm để CkeditorChoiceParser.java map đúng type (xem Javadoc đó). */
class InsertTrueFalseCommand extends Command {
  override execute() {
    const editor = this.editor;
    editor.model.change((writer) => {
      const group = writer.createElement("choiceGroup", { groupId: generateGroupId(), trueFalse: true });
      ["Đúng", "Sai"].forEach((text) => {
        const option = writer.createElement("choiceOption", { optionId: generateOptionId(), isCorrect: false });
        writer.insertText(text, option);
        writer.append(option, group);
      });
      editor.model.insertObject(group, null, null, { setSelection: "after" });
    });
  }

  override refresh() {
    const model = this.editor.model;
    const selection = model.document.selection;
    const parent = selection.focus!.parent as any;
    const checkAt = parent.is("element", "$root") ? parent : parent.parent;
    this.isEnabled = !!checkAt && model.schema.checkChild(checkAt, "choiceGroup");
  }
}

class AddChoiceOptionCommand extends Command {
  override execute() {
    const editor = this.editor;
    const selection = editor.model.document.selection;
    const group = findAncestorElement(selection.getFirstPosition()!, "choiceGroup");
    if (!group) return;
    editor.model.change((writer) => {
      const option = writer.createElement("choiceOption", { optionId: generateOptionId(), isCorrect: false });
      writer.insertText("New option", option);
      writer.append(option, group);
      writer.setSelection(option, 0);
    });
  }

  override refresh() {
    const selection = this.editor.model.document.selection;
    const group = findAncestorElement(selection.getFirstPosition()!, "choiceGroup");
    // Khoá thêm lựa chọn cho nhóm Đúng/Sai — backend đòi hỏi ĐÚNG 2 lựa chọn cố định (xem InsertTrueFalseCommand).
    this.isEnabled = !!group && !group.getAttribute("trueFalse");
  }
}

class ToggleChoiceCorrectCommand extends Command {
  override execute() {
    const editor = this.editor;
    const selection = editor.model.document.selection;
    const option = findAncestorElement(selection.getFirstPosition()!, "choiceOption");
    if (!option) return;
    editor.model.change((writer) => {
      writer.setAttribute("isCorrect", !option.getAttribute("isCorrect"), option);
    });
  }

  override refresh() {
    const selection = this.editor.model.document.selection;
    this.isEnabled = !!findAncestorElement(selection.getFirstPosition()!, "choiceOption");
  }
}

export default class ChoiceWidgetPlugin extends Plugin {
  static get requires() {
    // Yêu cầu Image để chắc chắn "imageInline" đã đăng ký trước khi _defineSchema() mở rộng cho
    // phép nó nằm trong "choiceOption" — phục vụ dạng "đáp án là ảnh".
    return [Widget, Image];
  }

  init() {
    const editor: Editor = this.editor;
    this._defineSchema();
    this._defineConverters();
    editor.commands.add("insertChoiceGroup", new InsertChoiceGroupCommand(editor));
    editor.commands.add("insertTrueFalse", new InsertTrueFalseCommand(editor));
    editor.commands.add("addChoiceOption", new AddChoiceOptionCommand(editor));
    editor.commands.add("toggleChoiceCorrect", new ToggleChoiceCorrectCommand(editor));
    this._defineUI();
    // KHÔNG đăng ký phím tắt — xem giải thích ở JustifyCleanupPlugin.ts (Ctrl+Alt/Ctrl+Shift đều
    // có nguy cơ trùng AltGr bàn phím VN hoặc phím tắt có sẵn của Chrome). Dùng nút trên toolbar.
  }

  private _defineUI() {
    const editor = this.editor;

    const addButton = (name: string, label: string, command: string, icon: string) => {
      editor.ui.componentFactory.add(name, (locale) => {
        const button = new ButtonView(locale);
        button.set({ label, withText: true, tooltip: true, icon });
        button.on("execute", () => editor.execute(command));
        const cmd = editor.commands.get(command)!;
        button.bind("isEnabled").to(cmd, "isEnabled");
        return button;
      });
    };

    addButton("insertChoiceGroup", "Chèn nhóm lựa chọn", "insertChoiceGroup", IconPlus);
    addButton("insertTrueFalse", "Chèn Đúng/Sai", "insertTrueFalse", IconCheck);
    addButton("addChoiceOption", "Thêm lựa chọn", "addChoiceOption", IconPlus);
    addButton("toggleChoiceCorrect", "Đánh dấu đúng/sai", "toggleChoiceCorrect", IconCheck);
  }

  private _defineSchema() {
    const schema = this.editor.model.schema;
    schema.register("choiceGroup", {
      inheritAllFrom: "$blockObject",
      allowAttributes: ["groupId", "trueFalse"]
    });
    schema.register("choiceOption", {
      allowIn: "choiceGroup",
      allowAttributes: ["optionId", "isCorrect"],
      isLimit: true
    });
    // Dạng "đáp án là ảnh" — cho phép chèn ảnh (imageInline) NGAY TRONG lựa chọn, cùng lúc với chữ.
    schema.extend("imageInline", { allowIn: "choiceOption" });
    schema.extend("$text", { allowIn: "choiceOption" });
  }

  private _defineConverters() {
    const conversion = this.editor.conversion;

    // ----- choiceGroup -----
    conversion.for("upcast").elementToElement({
      view: { name: "div", classes: "q-choice-group" },
      model: (viewElement, { writer }) =>
        writer.createElement("choiceGroup", {
          groupId: viewElement.getAttribute("data-group-id") || generateGroupId(),
          trueFalse: viewElement.getAttribute("data-true-false") === "true"
        })
    });

    conversion.for("editingDowncast").elementToElement({
      model: "choiceGroup",
      view: (modelElement, { writer }) => {
        const isTrueFalse = !!modelElement.getAttribute("trueFalse");
        const div = writer.createContainerElement("div", {
          class: "q-choice-group" + (isTrueFalse ? " q-choice-group-true-false" : ""),
          "data-group-id": modelElement.getAttribute("groupId"),
          ...(isTrueFalse ? { "data-true-false": "true" } : {})
        });
        return toWidget(div, writer, { label: isTrueFalse ? "Đúng/Sai" : "Nhóm lựa chọn" });
      }
    });

    conversion.for("dataDowncast").elementToElement({
      model: "choiceGroup",
      view: (modelElement, { writer }) => {
        const isTrueFalse = !!modelElement.getAttribute("trueFalse");
        return writer.createContainerElement("div", {
          class: "q-choice-group" + (isTrueFalse ? " q-choice-group-true-false" : ""),
          "data-group-id": modelElement.getAttribute("groupId"),
          ...(isTrueFalse ? { "data-true-false": "true" } : {})
        });
      }
    });

    // ----- choiceOption -----
    conversion.for("upcast").elementToElement({
      view: { name: "label", classes: "q-choice-option" },
      model: (viewElement, { writer }) =>
        writer.createElement("choiceOption", {
          optionId: viewElement.getAttribute("data-option-id") || generateOptionId(),
          isCorrect: viewElement.getAttribute("data-correct") === "true"
        })
    });

    conversion.for("editingDowncast").elementToElement({
      model: "choiceOption",
      view: (modelElement, { writer }) => {
        const label = writer.createEditableElement("div", {
          class: "q-choice-option" + (modelElement.getAttribute("isCorrect") ? " q-choice-correct" : ""),
          "data-option-id": modelElement.getAttribute("optionId")
        });
        return toWidgetEditable(label, writer);
      }
    });

    conversion.for("dataDowncast").elementToElement({
      model: "choiceOption",
      view: (modelElement, { writer }) =>
        writer.createContainerElement("label", {
          class: "q-choice-option" + (modelElement.getAttribute("isCorrect") ? " q-choice-correct" : ""),
          "data-option-id": modelElement.getAttribute("optionId"),
          "data-correct": modelElement.getAttribute("isCorrect") ? "true" : "false"
        })
    });

    // Phản ứng ngay trong lúc soạn khi bấm "Đánh dấu đúng/sai" — đổi class để CSS vẽ dấu tick xanh
    // (CKEditor5 coi "class" là attribute đa giá trị, attributeToAttribute THÊM/BỚT chứ không ghi
    // đè toàn bộ — không mất class "q-choice-option" gốc).
    conversion.for("editingDowncast").attributeToAttribute({
      model: "isCorrect",
      view: (value) => ({ key: "class", value: value ? "q-choice-correct" : "" })
    });
  }
}
