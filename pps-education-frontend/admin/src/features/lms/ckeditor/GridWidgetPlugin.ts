import { ButtonView, Command, Plugin } from "ckeditor5";
import { IconCancel, IconCheck, IconPencil, IconPlus, IconTable } from "ckeditor5";
import type { Editor, ModelElement } from "ckeditor5";
import { toWidget, toWidgetEditable, Widget } from "ckeditor5";
import { alertDialog, promptDialog } from "./dialogs";

/**
 * Widget "Bảng lưới" — dùng cho đọc hiểu dạng lưới và nối đáp án: mỗi DÒNG là 1 câu hỏi, các CỘT là
 * bộ đáp án DÙNG CHUNG cho mọi dòng (VD cột Tom / Max / Anna; dòng "Who loves a subject because of
 * visiting museums?"). Khi tạo, mỗi dòng thành 1 câu trắc nghiệm riêng, các câu chung 1 nhóm +
 * chung đoạn văn (xem CkeditorGroupParser.java) — đúng dữ liệu GridQuestionBuilder đang tạo, màn
 * Học viên tự gộp thành bảng.
 *
 * Bộ đáp án lưu ở attribute `options` của bảng; đáp án đúng của từng dòng lưu ở `correctIndex` của
 * dòng đó (chưa set = chưa chọn, chặn Tạo câu hỏi).
 */
export const GRID_OPTION_SEPARATOR = "|";

const letter = (index: number) => String.fromCharCode(65 + index);

async function parseOptions(raw: string | null): Promise<string[] | null> {
  if (!raw) return null;
  const options = raw.split(",").map((o) => o.trim()).filter(Boolean);
  if (options.length < 2) {
    await alertDialog("Cần ít nhất 2 đáp án.");
    return null;
  }
  if (options.length > 10) {
    await alertDialog("Tối đa 10 đáp án (A–J).");
    return null;
  }
  return options;
}

function optionsOf(grid: ModelElement): string[] {
  return ((grid.getAttribute("options") as string) || "").split(GRID_OPTION_SEPARATOR).filter(Boolean);
}

function findAncestor(editor: Editor, name: string): ModelElement | null {
  const selection = editor.model.document.selection;
  const selected = selection.getSelectedElement();
  if (selected && selected.name === name) return selected;
  let node: any = selection.getFirstPosition()!.parent;
  while (node) {
    if (node.is && node.is("element", name)) return node;
    node = node.parent;
  }
  return null;
}

class InsertChoiceGridCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const raw = await promptDialog({
      title: "Chèn Bảng lưới",
      label: "Nhập các đáp án dùng chung cho mọi dòng, cách nhau bởi dấu phẩy:",
      defaultValue: "Tom, Max, Anna"
    });
    const options = await parseOptions(raw);
    if (!options) return;
    editor.model.change((writer) => {
      const grid = writer.createElement("choiceGrid", { options: options.join(GRID_OPTION_SEPARATOR) });
      ["Câu hỏi 1", "Câu hỏi 2"].forEach((text) => {
        const row = writer.createElement("gridRow");
        writer.insertText(text, row);
        writer.append(row, grid);
      });
      editor.model.insertObject(grid, null, null, { setSelection: "after" });
    });
  }

  override refresh() {
    const model = this.editor.model;
    const parent = model.document.selection.focus!.parent as any;
    const checkAt = parent.is("element", "$root") ? parent : parent.parent;
    this.isEnabled = !!checkAt && model.schema.checkChild(checkAt, "choiceGrid");
  }
}

class AddGridRowCommand extends Command {
  override execute() {
    const editor = this.editor;
    const grid = findAncestor(editor, "choiceGrid");
    if (!grid) return;
    editor.model.change((writer) => {
      const row = writer.createElement("gridRow");
      writer.insertText("Câu hỏi mới", row);
      writer.append(row, grid);
      writer.setSelection(row, "in");
    });
  }

  override refresh() {
    this.isEnabled = !!findAncestor(this.editor, "choiceGrid");
  }
}

class RemoveGridRowCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const row = findAncestor(editor, "gridRow");
    const grid = findAncestor(editor, "choiceGrid");
    if (!row || !grid) return;
    if (grid.childCount <= 1) {
      await alertDialog("Bảng lưới cần ít nhất 1 dòng — muốn bỏ cả bảng thì chọn bảng rồi bấm Delete.");
      return;
    }
    editor.model.change((writer) => {
      writer.setSelection(grid, "on");
      writer.remove(row);
    });
  }

  override refresh() {
    this.isEnabled = !!findAncestor(this.editor, "gridRow");
  }
}

class SetGridRowCorrectCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const row = findAncestor(editor, "gridRow");
    const grid = findAncestor(editor, "choiceGrid");
    if (!row || !grid) return;
    const options = optionsOf(grid);
    const current = row.getAttribute("correctIndex");
    const raw = await promptDialog({
      title: "Chọn đáp án dòng",
      label: `Đáp án đúng cho dòng này — gõ chữ cái:\n${options.map((o, i) => `${letter(i)}. ${o}`).join("\n")}`,
      defaultValue: typeof current === "number" ? letter(current) : "A"
    });
    if (raw === null) return;
    const index = raw.trim().toUpperCase().charCodeAt(0) - 65;
    if (raw.trim().length !== 1 || index < 0 || index >= options.length) {
      await alertDialog(`Chỉ gõ 1 chữ cái từ A đến ${letter(options.length - 1)}.`);
      return;
    }
    editor.model.change((writer) => writer.setAttribute("correctIndex", index, row));
  }

  override refresh() {
    this.isEnabled = !!findAncestor(this.editor, "gridRow");
  }
}

class EditGridOptionsCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const grid = findAncestor(editor, "choiceGrid");
    if (!grid) return;
    const raw = await promptDialog({
      title: "Sửa đáp án lưới",
      label: "Sửa các đáp án dùng chung (cách nhau bởi dấu phẩy):",
      defaultValue: optionsOf(grid).join(", ")
    });
    const options = await parseOptions(raw);
    if (!options) return;
    editor.model.change((writer) => {
      writer.setAttribute("options", options.join(GRID_OPTION_SEPARATOR), grid);
      // Dòng nào đang trỏ tới đáp án không còn tồn tại thì đưa về "chưa chọn".
      for (const row of grid.getChildren()) {
        const index = (row as ModelElement).getAttribute("correctIndex");
        if (typeof index === "number" && index >= options.length) writer.removeAttribute("correctIndex", row as ModelElement);
      }
    });
  }

  override refresh() {
    this.isEnabled = !!findAncestor(this.editor, "choiceGrid");
  }
}

export default class GridWidgetPlugin extends Plugin {
  static get requires() {
    return [Widget];
  }

  init() {
    const editor: Editor = this.editor;
    this._defineSchema();
    this._defineConverters();
    editor.commands.add("insertChoiceGrid", new InsertChoiceGridCommand(editor));
    editor.commands.add("addGridRow", new AddGridRowCommand(editor));
    editor.commands.add("removeGridRow", new RemoveGridRowCommand(editor));
    editor.commands.add("setGridRowCorrect", new SetGridRowCorrectCommand(editor));
    editor.commands.add("editGridOptions", new EditGridOptionsCommand(editor));

    const addButton = (name: string, label: string, icon: string) => {
      editor.ui.componentFactory.add(name, (locale) => {
        const button = new ButtonView(locale);
        button.set({ label, withText: true, tooltip: true, icon });
        button.on("execute", () => editor.execute(name));
        button.bind("isEnabled").to(editor.commands.get(name)!, "isEnabled");
        return button;
      });
    };
    addButton("insertChoiceGrid", "Chèn bảng lưới", IconTable);
    addButton("addGridRow", "Thêm dòng lưới", IconPlus);
    addButton("removeGridRow", "Xoá dòng lưới", IconCancel);
    addButton("setGridRowCorrect", "Chọn đáp án dòng", IconCheck);
    addButton("editGridOptions", "Sửa đáp án lưới", IconPencil);
  }

  private _defineSchema() {
    const schema = this.editor.model.schema;
    schema.register("choiceGrid", { inheritAllFrom: "$blockObject", allowAttributes: ["options"] });
    schema.register("gridRow", { allowIn: "choiceGrid", allowAttributes: ["correctIndex"], isLimit: true });
    schema.extend("$text", { allowIn: "gridRow" });
  }

  private _defineConverters() {
    const conversion = this.editor.conversion;

    conversion.for("upcast").elementToElement({
      view: { name: "div", classes: "q-grid" },
      model: (viewElement, { writer }) => writer.createElement("choiceGrid", { options: viewElement.getAttribute("data-options") || "" })
    });
    conversion.for("upcast").elementToElement({
      view: { name: "div", classes: "q-grid-row" },
      model: (viewElement, { writer }) => {
        const raw = viewElement.getAttribute("data-correct-index");
        return writer.createElement("gridRow", raw != null && raw !== "" ? { correctIndex: Number(raw) } : {});
      }
    });

    // Bản soạn: nhãn bộ đáp án (đầu bảng) và nhãn đáp án đúng (cuối mỗi dòng) vẽ bằng CSS từ 2
    // attribute data-*-label bên dưới — xem ckeditor.css.
    conversion.for("editingDowncast").elementToElement({
      model: "choiceGrid",
      view: (_modelElement, { writer }) => toWidget(writer.createContainerElement("div", { class: "q-grid" }), writer, { label: "Bảng lưới" })
    });
    conversion.for("editingDowncast").elementToElement({
      model: "gridRow",
      view: (_modelElement, { writer }) => toWidgetEditable(writer.createEditableElement("div", { class: "q-grid-row" }), writer)
    });
    conversion.for("editingDowncast").attributeToAttribute({
      model: { name: "choiceGrid", key: "options" },
      view: (value) => ({
        key: "data-options-label",
        value: String(value || "").split(GRID_OPTION_SEPARATOR).filter(Boolean).map((o, i) => `${letter(i)}. ${o}`).join("   ·   ")
      })
    });
    conversion.for("editingDowncast").attributeToAttribute({
      model: { name: "gridRow", key: "correctIndex" },
      view: (value) => ({ key: "data-correct-label", value: `Đáp án: ${letter(Number(value))}` })
    });

    conversion.for("dataDowncast").elementToElement({
      model: "choiceGrid",
      view: (modelElement, { writer }) =>
        writer.createContainerElement("div", { class: "q-grid", "data-options": modelElement.getAttribute("options") })
    });
    conversion.for("dataDowncast").elementToElement({
      model: "gridRow",
      view: (modelElement, { writer }) => {
        const index = modelElement.getAttribute("correctIndex");
        return writer.createContainerElement("div", {
          class: "q-grid-row",
          ...(typeof index === "number" ? { "data-correct-index": String(index) } : {})
        });
      }
    });
  }
}
