import { ButtonView, Command, Plugin } from "ckeditor5";
import { IconPencil, IconPlus } from "ckeditor5";
import type { Editor } from "ckeditor5";
import { toWidget, Widget } from "ckeditor5";
import { alertDialog, promptDialog } from "./dialogs";

/**
 * Widget "Lựa chọn trong câu" — 1 ô CHỌN nằm ngay giữa dòng chữ, mỗi ô mang bộ lựa chọn RIÊNG (VD
 * "The school has an excellent (environment / timetable / hours) with many computers"). Dùng cho
 * dạng đọc điền từ (cloze) và "chọn từ trong câu". Khi tạo, MỖI ô thành 1 câu trắc nghiệm riêng,
 * các câu chung 1 nhóm + chung đoạn văn (xem CkeditorGroupParser.java) — đúng dữ liệu
 * ClozeQuestionBuilder đang tạo, màn Học viên không đổi gì.
 */
export const INLINE_CHOICE_SEPARATOR = "|";

let inlineChoiceIdCounter = 0;

function generateInlineChoiceId(): string {
  inlineChoiceIdCounter += 1;
  return `ic${Date.now().toString(36)}${inlineChoiceIdCounter}`;
}

/** Hỏi danh sách lựa chọn + lựa chọn đúng; trả null nếu GV huỷ hoặc nhập không hợp lệ. */
async function promptOptions(currentOptions: string[], currentCorrect: string | null): Promise<{ options: string[]; correctIndex: number } | null> {
  const raw = await promptDialog({
    title: "Lựa chọn trong câu",
    label: "Nhập các lựa chọn, cách nhau bởi dấu phẩy (VD: go, goes, going):",
    defaultValue: currentOptions.join(", ")
  });
  if (!raw) return null;
  const options = raw.split(",").map((o) => o.trim()).filter(Boolean);
  if (options.length < 2) {
    await alertDialog("Cần ít nhất 2 lựa chọn.");
    return null;
  }
  const correct = await promptDialog({
    title: "Lựa chọn trong câu",
    label: `Lựa chọn nào ĐÚNG? Gõ đúng 1 trong:\n${options.join(", ")}`,
    defaultValue: currentCorrect ?? options[0]
  });
  if (correct === null) return null;
  const correctIndex = options.indexOf(correct.trim());
  if (correctIndex === -1) {
    await alertDialog(`"${correct}" không khớp lựa chọn nào trong danh sách — huỷ thao tác, thử lại.`);
    return null;
  }
  return { options, correctIndex };
}

class InsertInlineChoiceCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const result = await promptOptions([], null);
    if (!result) return;
    editor.model.change((writer) => {
      const widget = writer.createElement("inlineChoice", {
        choiceId: generateInlineChoiceId(),
        options: result.options.join(INLINE_CHOICE_SEPARATOR),
        correctIndex: result.correctIndex
      });
      editor.model.insertObject(widget, null, null, { setSelection: "after" });
    });
  }

  override refresh() {
    const model = this.editor.model;
    this.isEnabled = model.schema.checkChild(model.document.selection.focus!.parent as any, "inlineChoice");
  }
}

class EditInlineChoiceCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const element = this.getSelected();
    if (!element) return;
    const currentOptions = ((element.getAttribute("options") as string) || "").split(INLINE_CHOICE_SEPARATOR);
    const result = await promptOptions(currentOptions, currentOptions[element.getAttribute("correctIndex") as number] ?? null);
    if (!result) return;
    editor.model.change((writer) => {
      writer.setAttribute("options", result.options.join(INLINE_CHOICE_SEPARATOR), element);
      writer.setAttribute("correctIndex", result.correctIndex, element);
    });
  }

  override refresh() {
    this.isEnabled = !!this.getSelected();
  }

  private getSelected() {
    const element = this.editor.model.document.selection.getSelectedElement();
    return element && element.name === "inlineChoice" ? element : null;
  }
}

export default class InlineChoiceWidgetPlugin extends Plugin {
  static get requires() {
    return [Widget];
  }

  init() {
    const editor: Editor = this.editor;
    this._defineSchema();
    this._defineConverters();
    editor.commands.add("insertInlineChoice", new InsertInlineChoiceCommand(editor));
    editor.commands.add("editInlineChoice", new EditInlineChoiceCommand(editor));

    const addButton = (name: string, label: string, icon: string) => {
      editor.ui.componentFactory.add(name, (locale) => {
        const button = new ButtonView(locale);
        button.set({ label, withText: true, tooltip: true, icon });
        button.on("execute", () => editor.execute(name));
        button.bind("isEnabled").to(editor.commands.get(name)!, "isEnabled");
        return button;
      });
    };
    addButton("insertInlineChoice", "Chèn lựa chọn trong câu", IconPlus);
    addButton("editInlineChoice", "Sửa lựa chọn trong câu", IconPencil);
  }

  private _defineSchema() {
    this.editor.model.schema.register("inlineChoice", {
      inheritAllFrom: "$inlineObject",
      allowAttributes: ["choiceId", "options", "correctIndex"]
    });
  }

  private _defineConverters() {
    const conversion = this.editor.conversion;

    conversion.for("upcast").elementToElement({
      view: { name: "span", classes: "q-inline-choice" },
      model: (viewElement, { writer }) =>
        writer.createElement("inlineChoice", {
          choiceId: viewElement.getAttribute("data-choice-id") || generateInlineChoiceId(),
          options: viewElement.getAttribute("data-options") || "",
          correctIndex: Number(viewElement.getAttribute("data-correct-index") || 0)
        })
    });

    conversion.for("editingDowncast").elementToElement({
      model: "inlineChoice",
      view: (modelElement, { writer }) => {
        const options = ((modelElement.getAttribute("options") as string) || "").split(INLINE_CHOICE_SEPARATOR);
        const correctIndex = modelElement.getAttribute("correctIndex") as number;
        const span = writer.createContainerElement("span", {
          class: "q-inline-choice",
          "data-choice-id": modelElement.getAttribute("choiceId")
        });
        const label = options.map((o, i) => (i === correctIndex ? `✓${o}` : o)).join(" / ");
        writer.insert(writer.createPositionAt(span, 0), writer.createText(`( ${label} )`));
        return toWidget(span, writer, { label: "Lựa chọn trong câu" });
      }
    });

    conversion.for("dataDowncast").elementToElement({
      model: "inlineChoice",
      view: (modelElement, { writer }) =>
        writer.createContainerElement("span", {
          class: "q-inline-choice",
          "data-choice-id": modelElement.getAttribute("choiceId"),
          "data-options": modelElement.getAttribute("options"),
          "data-correct-index": String(modelElement.getAttribute("correctIndex"))
        })
    });

    // Nhãn trong widget là nội dung dựng lúc tạo view — đổi lựa chọn phải tự vẽ lại (xem giải thích
    // ở BlankWidgetPlugin: gom theo change:data, mỗi widget 1 lần).
    this.editor.model.document.on("change:data", () => {
      const toReconvert = new Set<any>();
      for (const change of this.editor.model.document.differ.getChanges()) {
        if (change.type !== "attribute") continue;
        if (change.attributeKey !== "options" && change.attributeKey !== "correctIndex") continue;
        const item = (change as any).range?.start?.nodeAfter;
        if (item && item.is && item.is("element", "inlineChoice")) toReconvert.add(item);
      }
      for (const item of toReconvert) this.editor.editing.reconvertItem(item);
    });
  }
}
