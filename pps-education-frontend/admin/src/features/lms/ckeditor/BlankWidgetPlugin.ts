import { ButtonView, Command, Plugin, UpcastWriter } from "ckeditor5";
import type { Editor, ViewDocumentFragment, ViewElement, ViewNode } from "ckeditor5";
import { toWidget, Widget } from "ckeditor5";
import { IconCheck, IconPlus } from "ckeditor5";
import { findFirstWordBankWords } from "./WordBankWidgetPlugin";
import { alertDialog, promptDialog } from "./dialogs";

/**
 * Chuyển từ PoC (feature/ckeditor5-poc) sang khung soạn câu hỏi THẬT — widget "Ô trống" cho tab
 * CKEditor5 ("Ô trống"/"Hộp từ vựng") ở CreateAndAssignExerciseModal.tsx. GV gõ/dán sẵn `___` (3
 * dấu gạch dưới liên tiếp trở lên) y hệt thói quen soạn đề trong Word — hệ thống TỰ ĐỘNG nhận
 * diện lúc dán và chuyển thành widget Ô trống thật.
 *
 * 3 trạng thái 1 ô trống (bổ sung ngoài PoC, đã xác nhận với người dùng khi review mockup — "bạn
 * có tính trường hợp ô trống là rỗng chưa"): `answered=false` (CHƯA cấu hình — chặn Lưu),
 * `answered=true, answer=""` (để trống CÓ CHỦ ĐÍCH — hợp lệ, VD mạo từ a/an/the không bắt buộc),
 * `answered=true, answer="..."` (có đáp án). `SetBlankAnswerCommand` luôn set `answered=true` sau
 * khi GV xác nhận prompt (dù để trống), chỉ `answered=false` khi widget chưa từng được gán.
 */
const UNDERSCORE_RUN_PATTERN = /_{3,}/g;

let blankIdCounter = 0;

function generateBlankId(): string {
  blankIdCounter += 1;
  return `b${Date.now().toString(36)}${blankIdCounter}`;
}

class InsertBlankCommand extends Command {
  override execute() {
    const editor = this.editor;
    const model = editor.model;
    model.change((writer) => {
      const blank = writer.createElement("blank", {
        blankId: generateBlankId(),
        answer: "",
        answered: false
      });
      model.insertObject(blank, null, null, { setSelection: "after" });
    });
  }

  override refresh() {
    const model = this.editor.model;
    const selection = model.document.selection;
    this.isEnabled = model.schema.checkChild(selection.focus!.parent as any, "blank");
  }
}

class SetBlankAnswerCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const element = this.getSelectedBlank();
    if (!element) return;
    const current = (element.getAttribute("answer") as string) || "";
    const bankWords = findFirstWordBankWords(editor);

    // Có "Hộp từ vựng" trong tài liệu — bắt GV chọn ĐÚNG 1 từ trong hộp, không cho gõ tự do lệch hộp.
    if (bankWords && bankWords.length > 0) {
      let next: string | null = null;
      while (true) {
        next = await promptDialog({
          title: "Gán đáp án ô trống",
          label: `Chọn từ trong hộp (nhiều đáp án đúng thì cách nhau bằng dấu "/"; để trống nếu chỗ trống này cố ý không có đáp án):\n${bankWords.join(", ")}`,
          defaultValue: current
        });
        if (next === null) return; // GV bấm Huỷ — giữ nguyên trạng thái cũ
        next = next.trim();
        const variants = next.split("/").map((v) => v.trim()).filter(Boolean);
        const outside = variants.filter((v) => !bankWords.includes(v));
        if (outside.length === 0) break;
        await alertDialog(`"${outside.join('", "')}" không có trong hộp từ. Vui lòng chỉ dùng từ trong danh sách (hoặc để trống).`);
      }
      editor.model.change((writer) => {
        writer.setAttribute("answer", next, element);
        writer.setAttribute("answered", true, element);
      });
      return;
    }

    // Không có hộp từ — Điền từ tự do.
    const next = await promptDialog({
      title: "Gán đáp án ô trống",
      label: 'Đáp án đúng cho ô trống này. Nhiều đáp án đúng thì cách nhau bằng dấu "/" (VD: 2 dollars/two dollars). Để trống nếu cố ý không yêu cầu đáp án:',
      defaultValue: current
    });
    if (next === null) return;
    editor.model.change((writer) => {
      writer.setAttribute("answer", next.trim(), element);
      writer.setAttribute("answered", true, element);
    });
  }

  override refresh() {
    this.isEnabled = !!this.getSelectedBlank();
  }

  private getSelectedBlank() {
    const selection = this.editor.model.document.selection;
    const element = selection.getSelectedElement();
    return element && element.name === "blank" ? element : null;
  }
}

/** Duyệt TOÀN BỘ tài liệu (kể cả trong bảng) theo đúng thứ tự xuất hiện — khớp thứ tự sẽ map với
 * `structuredContent.blanks[]` ở backend (xem CkeditorWordBankParser.java). */
export function collectBlanksInDocumentOrder(editor: Editor) {
  const root = editor.model.document.getRoot()!;
  const range = editor.model.createRangeIn(root);
  const result: { element: any; blankId: string; answer: string; answered: boolean }[] = [];
  for (const value of range) {
    if (value.type === "elementStart" && value.item.is("element", "blank")) {
      result.push({
        element: value.item,
        blankId: (value.item.getAttribute("blankId") as string) || "",
        answer: (value.item.getAttribute("answer") as string) || "",
        answered: !!value.item.getAttribute("answered")
      });
    }
  }
  return result;
}

export default class BlankWidgetPlugin extends Plugin {
  static get requires() {
    return [Widget];
  }

  init() {
    const editor: Editor = this.editor;
    this._defineSchema();
    this._defineConverters();
    editor.commands.add("insertBlank", new InsertBlankCommand(editor));
    editor.commands.add("setBlankAnswer", new SetBlankAnswerCommand(editor));
    this._defineUI();
    this._enableAutoDetectBlanksOnPaste();

    // KHÔNG đăng ký phím tắt — "Ctrl+Alt+*" trùng AltGr bàn phím tiếng Việt, "Ctrl+Shift+B" trùng
    // phím tắt toggle thanh bookmark của Chrome (xem giải thích đầy đủ ở JustifyCleanupPlugin.ts).
    // Dùng nút bấm trên toolbar.
  }

  private _enableAutoDetectBlanksOnPaste() {
    const editor = this.editor;
    const clipboardPipeline = editor.plugins.get("ClipboardPipeline") as any;
    const upcastWriter = new UpcastWriter(editor.editing.view.document);

    clipboardPipeline.on("inputTransformation", (_evt: unknown, data: { content: ViewDocumentFragment }) => {
      this._replaceUnderscoreRunsWithBlanks(upcastWriter, data.content);
    });
  }

  private _replaceUnderscoreRunsWithBlanks(writer: UpcastWriter, parent: ViewElement | ViewDocumentFragment) {
    const children: ViewNode[] = Array.from(parent.getChildren());

    for (const child of children) {
      if (child.is("$text") || child.is("$textProxy")) {
        const text = (child as any).data as string;
        UNDERSCORE_RUN_PATTERN.lastIndex = 0;
        if (!UNDERSCORE_RUN_PATTERN.test(text)) continue;

        const replacement: Array<ReturnType<UpcastWriter["createText"]>> = [];
        let lastIndex = 0;
        let match: RegExpExecArray | null;
        UNDERSCORE_RUN_PATTERN.lastIndex = 0;
        while ((match = UNDERSCORE_RUN_PATTERN.exec(text))) {
          if (match.index > lastIndex) replacement.push(writer.createText(text.slice(lastIndex, match.index)) as any);
          replacement.push(
            writer.createElement("span", { class: "q-blank", "data-blank-id": generateBlankId() }) as any
          );
          lastIndex = match.index + match[0].length;
        }
        if (lastIndex < text.length) replacement.push(writer.createText(text.slice(lastIndex)) as any);

        const index = child.index;
        if (index === null) continue;
        writer.remove(child);
        writer.insertChild(index, replacement, parent);
      } else if (child.is("element")) {
        this._replaceUnderscoreRunsWithBlanks(writer, child as ViewElement);
      }
    }
  }

  private _defineUI() {
    const editor = this.editor;

    editor.ui.componentFactory.add("insertBlank", (locale) => {
      const button = new ButtonView(locale);
      button.set({ label: "Chèn ô trống", withText: true, tooltip: true, icon: IconPlus });
      button.on("execute", () => editor.execute("insertBlank"));
      const command = editor.commands.get("insertBlank")!;
      button.bind("isEnabled").to(command, "isEnabled");
      return button;
    });

    editor.ui.componentFactory.add("setBlankAnswer", (locale) => {
      const button = new ButtonView(locale);
      button.set({ label: "Gán đáp án ô trống", withText: true, tooltip: true, icon: IconCheck });
      button.on("execute", () => editor.execute("setBlankAnswer"));
      const command = editor.commands.get("setBlankAnswer")!;
      button.bind("isEnabled").to(command, "isEnabled");
      return button;
    });
  }

  private _defineSchema() {
    const schema = this.editor.model.schema;
    schema.register("blank", {
      inheritAllFrom: "$inlineObject",
      allowAttributes: ["blankId", "answer", "answered"]
    });
  }

  private _renderBlankLabel(answer: string, answered: boolean): string {
    if (!answered) return "⚠ Chưa gán đáp án";
    if (!answer) return "( để trống — có chủ đích )";
    return answer;
  }

  private _blankClass(answered: boolean, hasAnswer: boolean): string {
    if (!answered) return "q-blank q-blank-unconfigured";
    if (!hasAnswer) return "q-blank q-blank-intentional-empty";
    return "q-blank q-blank-answered";
  }

  private _defineConverters() {
    const conversion = this.editor.conversion;

    conversion.for("upcast").elementToElement({
      view: { name: "span", classes: "q-blank" },
      model: (viewElement, { writer }) => {
        const hasAnswerAttr = viewElement.hasAttribute("data-answer");
        return writer.createElement("blank", {
          blankId: viewElement.getAttribute("data-blank-id") || generateBlankId(),
          answer: viewElement.getAttribute("data-answer") || "",
          answered: hasAnswerAttr
        });
      }
    });

    conversion.for("editingDowncast").elementToElement({
      model: "blank",
      view: (modelElement, { writer }) => {
        const answer = (modelElement.getAttribute("answer") as string) || "";
        const answered = !!modelElement.getAttribute("answered");
        const span = writer.createContainerElement("span", {
          class: this._blankClass(answered, !!answer),
          "data-blank-id": modelElement.getAttribute("blankId")
        });
        writer.insert(writer.createPositionAt(span, 0), writer.createText(this._renderBlankLabel(answer, answered)));
        return toWidget(span, writer, { label: "Ô trống" });
      }
    });

    conversion.for("dataDowncast").elementToElement({
      model: "blank",
      view: (modelElement, { writer }) => {
        const answered = !!modelElement.getAttribute("answered");
        const attrs: Record<string, string> = {
          class: "q-blank",
          "data-blank-id": modelElement.getAttribute("blankId") as string
        };
        // Chỉ xuất data-answer khi widget ĐÃ được cấu hình (answered=true) — thiếu attribute này ở
        // dataDowncast là tín hiệu "chưa cấu hình" mà upcast đọc lại đúng ngược (xem nhánh upcast
        // trên) khi mở lại 1 câu đã lưu để sửa.
        if (answered) attrs["data-answer"] = (modelElement.getAttribute("answer") as string) || "";
        return writer.createContainerElement("span", attrs);
      }
    });

    // Đổi text hiển thị trong widget (đáp án/placeholder) là đổi NỘI DUNG, không phải 1 attribute
    // HTML đơn — attributeToAttribute (xem bản PoC cũ, dùng cho "title") không re-render được text
    // con. Phải tự reconvert cả widget mỗi khi "answer"/"answered" đổi.
    //
    // Fix bug thật (người dùng báo: "Gán nhanh tất cả đáp án" áp 10 ô cùng lúc, chỉ vài ô ĐẦU hiện
    // đáp án ngay, phải chuyển tab/reload mới thấy hết) — bản đầu gọi reconvertItem() NGAY TRONG LÚC
    // dispatcher đang xử lý sự kiện đổi attribute (2 lần/ô × 10 ô = 20 lượt reconvert LỒNG NHAU
    // trong CÙNG 1 lần model.change()), làm hỏng các lượt reconvert sau khi đổi NHIỀU ô cùng lúc —
    // chỉ đúng với sửa 1 ô/lần (SetBlankAnswerCommand) nên không lộ ra trước đó. Sửa đúng theo cách
    // CKEditor5 khuyến nghị: gom danh sách element đổi qua `model.document.differ` rồi reconvert
    // SAU KHI cả batch `model.change()` đã xong hẳn (sự kiện `change:data`), mỗi element CHỈ 1 LẦN
    // dù đổi cả 2 attribute cùng lúc.
    this.editor.model.document.on("change:data", () => {
      const toReconvert = new Set<any>();
      for (const change of this.editor.model.document.differ.getChanges()) {
        if (change.type !== "attribute") continue;
        if (change.attributeKey !== "answer" && change.attributeKey !== "answered") continue;
        const item = (change as any).range?.start?.nodeAfter;
        if (item && item.is && item.is("element", "blank")) toReconvert.add(item);
      }
      for (const item of toReconvert) this.editor.editing.reconvertItem(item);
    });
  }
}
