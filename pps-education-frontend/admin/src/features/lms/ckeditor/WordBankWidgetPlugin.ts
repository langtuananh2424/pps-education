import { ButtonView, Command, Plugin } from "ckeditor5";
import { IconPencil, IconPlus } from "ckeditor5";
import type { Editor, ModelElement } from "ckeditor5";
import { toWidget, Widget } from "ckeditor5";
import { alertDialog, promptDialog } from "./dialogs";

/**
 * Chuyển từ PoC (feature/ckeditor5-poc) sang khung soạn câu hỏi THẬT — widget "Hộp từ vựng" (Word
 * Bank): 1 hộp liệt kê sẵn các từ/cụm từ, bên dưới là đoạn văn có nhiều ô trống mà đáp án PHẢI là
 * 1 trong các từ của hộp (không phải gõ tự do như widget "blank" thường) — xem phối hợp validate
 * ở BlankWidgetPlugin.ts#SetBlankAnswerCommand.
 */
export const WORD_SEPARATOR = "|";

const DEFAULT_COLUMNS = 3;
const MIN_COLUMNS = 1;
const MAX_COLUMNS = 8;

/** Số hàng tự wrap theo CSS grid dựa trên (số từ, số cột) — không có ô riêng, luôn = ceil(words/columns). */
async function promptColumns(current: number): Promise<number | null> {
  const raw = await promptDialog({
    title: "Số cột hiển thị",
    label: "Số cột hiển thị trong hộp (số hàng tự xuống dòng theo số từ):",
    defaultValue: String(current)
  });
  if (raw === null) return null;
  const parsed = Number(raw.trim());
  if (!Number.isInteger(parsed) || parsed < MIN_COLUMNS || parsed > MAX_COLUMNS) {
    await alertDialog(`Số cột phải là số nguyên từ ${MIN_COLUMNS} đến ${MAX_COLUMNS}.`);
    return promptColumns(current);
  }
  return parsed;
}

class InsertWordBankCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const raw = await promptDialog({
      title: "Chèn Hộp từ vựng",
      label: "Nhập danh sách từ trong hộp, cách nhau bởi dấu phẩy:",
      defaultValue: "capital, architecture, suburb, famous for, residential area"
    });
    if (raw === null) return;
    const words = raw
      .split(",")
      .map((w) => w.trim())
      .filter(Boolean);
    const columns = await promptColumns(DEFAULT_COLUMNS);
    if (columns === null) return;
    editor.model.change((writer) => {
      const box = writer.createElement("wordBank", { words: words.join(WORD_SEPARATOR), columns });
      editor.model.insertObject(box, null, null, { setSelection: "after" });
    });
  }

  override refresh() {
    // "wordBank" là $blockObject — phải kiểm tra ở cấp CHA CỦA đoạn văn (nơi nó sẽ tách ra làm
    // anh em), không phải cấp đoạn văn (nơi $blockObject không bao giờ được phép chèn làm con).
    const model = this.editor.model;
    const selection = model.document.selection;
    const parent = selection.focus!.parent as any;
    const checkAt = parent.is("element", "$root") ? parent : parent.parent;
    this.isEnabled = !!checkAt && model.schema.checkChild(checkAt, "wordBank");
  }
}

class EditWordBankCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const element = this.getSelectedWordBank();
    if (!element) return;
    const current = ((element.getAttribute("words") as string) || "").split(WORD_SEPARATOR).join(", ");
    const raw = await promptDialog({ title: "Sửa Hộp từ vựng", label: "Sửa danh sách từ (cách nhau bởi dấu phẩy):", defaultValue: current });
    if (raw === null) return;
    const words = raw
      .split(",")
      .map((w) => w.trim())
      .filter(Boolean);
    const currentColumns = (element.getAttribute("columns") as number) || DEFAULT_COLUMNS;
    const columns = await promptColumns(currentColumns);
    if (columns === null) return;
    editor.model.change((writer) => {
      writer.setAttribute("words", words.join(WORD_SEPARATOR), element);
      writer.setAttribute("columns", columns, element);
    });
  }

  override refresh() {
    this.isEnabled = !!this.getSelectedWordBank();
  }

  private getSelectedWordBank() {
    const selection = this.editor.model.document.selection;
    const element = selection.getSelectedElement();
    return element && element.name === "wordBank" ? element : null;
  }
}

/** Dùng từ BlankWidgetPlugin để validate đáp án theo hộp từ gần nhất trong tài liệu (lấy hộp ĐẦU
 * TIÊN tìm thấy — đơn giản hoá, 1 câu WORD_BANK thực tế chỉ có đúng 1 hộp). */
export function findFirstWordBankWords(editor: Editor): string[] | null {
  const root = editor.model.document.getRoot();
  if (!root) return null;
  for (const node of (root as ModelElement).getChildren()) {
    if ((node as ModelElement).is?.("element", "wordBank")) {
      const raw = ((node as ModelElement).getAttribute("words") as string) || "";
      return raw.split(WORD_SEPARATOR).filter(Boolean);
    }
  }
  return null;
}

export default class WordBankWidgetPlugin extends Plugin {
  static get requires() {
    return [Widget];
  }

  init() {
    const editor: Editor = this.editor;
    this._defineSchema();
    this._defineConverters();
    editor.commands.add("insertWordBank", new InsertWordBankCommand(editor));
    editor.commands.add("editWordBank", new EditWordBankCommand(editor));
    this._defineUI();

    // KHÔNG đăng ký phím tắt — xem giải thích ở BlankWidgetPlugin.ts. Dùng nút bấm trên toolbar.
  }

  private _defineUI() {
    const editor = this.editor;

    editor.ui.componentFactory.add("insertWordBank", (locale) => {
      const button = new ButtonView(locale);
      button.set({ label: "Chèn Hộp từ vựng", withText: true, tooltip: true, icon: IconPlus });
      button.on("execute", () => editor.execute("insertWordBank"));
      const command = editor.commands.get("insertWordBank")!;
      button.bind("isEnabled").to(command, "isEnabled");
      return button;
    });

    editor.ui.componentFactory.add("editWordBank", (locale) => {
      const button = new ButtonView(locale);
      button.set({ label: "Sửa danh sách từ", withText: true, tooltip: true, icon: IconPencil });
      button.on("execute", () => editor.execute("editWordBank"));
      const command = editor.commands.get("editWordBank")!;
      button.bind("isEnabled").to(command, "isEnabled");
      return button;
    });
  }

  private _defineSchema() {
    const schema = this.editor.model.schema;
    schema.register("wordBank", {
      inheritAllFrom: "$blockObject",
      allowAttributes: ["words", "columns"]
    });
  }

  private _defineConverters() {
    const conversion = this.editor.conversion;

    // Số cột đi kèm theo HTML lưu lại (style inline — backend sanitizer đã cho phép attribute
    // "style" ở mọi thẻ, xem CkeditorWordBankParser.java) để giữ đúng layout khi mở lại câu để sửa
    // hoặc hiển thị nơi khác sau này (Portal học sinh) — không cần cột riêng ở DB, thuần trình bày.
    const renderBox = (words: string[], columns: number, writer: any, editable: boolean) => {
      const box = writer.createContainerElement("div", {
        class: "q-word-bank",
        style: `grid-template-columns: repeat(${columns}, 1fr);`
      });
      words.forEach((w) => {
        const chip = writer.createContainerElement("span", { class: "q-word-bank-item" });
        writer.insert(writer.createPositionAt(chip, 0), writer.createText(w));
        writer.insert(writer.createPositionAt(box, "end"), chip);
      });
      return editable ? toWidget(box, writer, { label: "Hộp từ vựng" }) : box;
    };

    conversion.for("upcast").elementToElement({
      view: { name: "div", classes: "q-word-bank" },
      model: (viewElement, { writer }) => {
        const words = Array.from(viewElement.getChildren())
          .map((child: any) => (child.is?.("element") ? child.getChild(0)?.data || "" : ""))
          .filter(Boolean);
        const styleMatch = /grid-template-columns:\s*repeat\((\d+)/.exec(viewElement.getAttribute("style") || "");
        const columns = styleMatch ? Number(styleMatch[1]) : DEFAULT_COLUMNS;
        return writer.createElement("wordBank", { words: words.join(WORD_SEPARATOR), columns });
      }
    });

    conversion.for("editingDowncast").elementToElement({
      model: "wordBank",
      view: (modelElement, { writer }) => {
        const words = ((modelElement.getAttribute("words") as string) || "").split(WORD_SEPARATOR).filter(Boolean);
        const columns = (modelElement.getAttribute("columns") as number) || DEFAULT_COLUMNS;
        return renderBox(words, columns, writer, true);
      }
    });

    conversion.for("dataDowncast").elementToElement({
      model: "wordBank",
      view: (modelElement, { writer }) => {
        const words = ((modelElement.getAttribute("words") as string) || "").split(WORD_SEPARATOR).filter(Boolean);
        const columns = (modelElement.getAttribute("columns") as number) || DEFAULT_COLUMNS;
        return renderBox(words, columns, writer, false);
      }
    });

    // Mirror BlankWidgetPlugin (xem Javadoc fix bug "reconvert lồng nhau" ở đó) — danh sách chip/số
    // cột là NỘI DUNG dựng lúc tạo view, đổi "words"/"columns" phải tự reconvert cả widget mới thấy
    // ngay trong lúc soạn. Gom theo `change:data` (SAU KHI cả batch model.change() xong) thay vì
    // reconvert NGAY TRONG LÚC dispatcher đang xử lý — an toàn kể cả khi đổi nhiều wordBank cùng lúc.
    this.editor.model.document.on("change:data", () => {
      const toReconvert = new Set<any>();
      for (const change of this.editor.model.document.differ.getChanges()) {
        if (change.type !== "attribute") continue;
        if (change.attributeKey !== "words" && change.attributeKey !== "columns") continue;
        const item = (change as any).range?.start?.nodeAfter;
        if (item && item.is && item.is("element", "wordBank")) toReconvert.add(item);
      }
      for (const item of toReconvert) this.editor.editing.reconvertItem(item);
    });
  }
}
