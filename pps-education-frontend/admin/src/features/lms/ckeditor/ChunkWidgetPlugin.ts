import { ButtonView, Command, Plugin } from "ckeditor5";
import { IconMarker } from "ckeditor5";
import type { Editor } from "ckeditor5";
import { toWidget, Widget } from "ckeditor5";
import { alertDialog } from "./dialogs";

/**
 * Chuyển từ PoC (feature/ckeditor5-poc) sang khung soạn câu hỏi THẬT — widget "Khối sắp xếp" (map
 * QuestionType.SENTENCE_BUILDING). Khác Ô trống/Nhóm lựa chọn ở chỗ: không CHÈN nội dung mới, mà
 * BIẾN nội dung đã gõ sẵn (GV bôi đen 1 từ/cụm từ) thành khối.
 *
 * Thứ tự HIỂN THỊ cho học sinh LUÔN giữ NGUYÊN y hệt đề bài GV gõ (không random) — "thứ tự đúng" là
 * 1 cấu hình TÁCH RIÊNG qua attribute `order` (gán qua panel "Gán thứ tự đúng" ở
 * CkeditorWordBankComposer.tsx + lệnh `applyChunkOrder`). Mặc định nếu GV không đụng tới, `order`
 * để trống và backend coi thứ tự hiển thị luôn là thứ tự đúng (an toàn ngược — không bắt buộc phải
 * cấu hình thêm nếu 2 thứ tự giống nhau, xem CkeditorChunkParser.java).
 *
 * Bỏ auto-detect dán "/" (có ở PoC, đã rút kinh nghiệm dễ bấm nhầm/hỏi rối — xem lịch sử trao đổi)
 * — bản thật chỉ chèn qua bôi đen + bấm nút, rõ ràng hơn.
 */
let chunkIdCounter = 0;

function generateChunkId(): string {
  chunkIdCounter += 1;
  return `c${Date.now().toString(36)}${chunkIdCounter}`;
}

class InsertChunkCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const selection = editor.model.document.selection;
    const range = selection.getFirstRange();
    if (!range || range.isCollapsed) {
      await alertDialog("Hãy bôi đen 1 từ/cụm từ trong câu trước khi bấm nút này.");
      return;
    }

    let text = "";
    for (const item of range.getItems()) {
      const asAny = item as any;
      if (asAny.is?.("$textProxy") || asAny.is?.("$text")) text += asAny.data;
    }
    text = text.trim();
    if (!text) return;

    editor.model.change((writer) => {
      writer.remove(range);
      const chunk = writer.createElement("chunk", { chunkId: generateChunkId(), text, order: null });
      editor.model.insertContent(chunk, range.start);
    });
  }

  override refresh() {
    const selection = this.editor.model.document.selection;
    this.isEnabled = !selection.isCollapsed;
  }
}

/** Duyệt toàn tài liệu lấy mọi widget "chunk" theo đúng thứ tự hiển thị — dùng cho panel "Gán thứ tự đúng". */
export function collectChunksInDocumentOrder(editor: Editor) {
  const root = editor.model.document.getRoot()!;
  const range = editor.model.createRangeIn(root);
  const result: { element: any; chunkId: string; text: string; order: number | null }[] = [];
  for (const value of range) {
    if (value.type === "elementStart" && value.item.is("element", "chunk")) {
      const orderRaw = value.item.getAttribute("order");
      result.push({
        element: value.item,
        chunkId: (value.item.getAttribute("chunkId") as string) || "",
        text: (value.item.getAttribute("text") as string) || "",
        order: typeof orderRaw === "number" ? orderRaw : null
      });
    }
  }
  return result;
}

export default class ChunkWidgetPlugin extends Plugin {
  static get requires() {
    return [Widget];
  }

  init() {
    const editor: Editor = this.editor;
    this._defineSchema();
    this._defineConverters();
    editor.commands.add("insertChunk", new InsertChunkCommand(editor));
    this._defineUI();
    // KHÔNG đăng ký phím tắt — xem giải thích ở JustifyCleanupPlugin.ts. Dùng nút trên toolbar.
  }

  private _defineUI() {
    const editor = this.editor;
    editor.ui.componentFactory.add("insertChunk", (locale) => {
      const button = new ButtonView(locale);
      button.set({ label: "Đánh dấu khối sắp xếp", withText: true, tooltip: true, icon: IconMarker });
      button.on("execute", () => editor.execute("insertChunk"));
      const command = editor.commands.get("insertChunk")!;
      button.bind("isEnabled").to(command, "isEnabled");
      return button;
    });
  }

  private _defineSchema() {
    const schema = this.editor.model.schema;
    schema.register("chunk", {
      inheritAllFrom: "$inlineObject",
      allowAttributes: ["chunkId", "text", "order"]
    });
  }

  private _defineConverters() {
    const conversion = this.editor.conversion;

    conversion.for("upcast").elementToElement({
      view: { name: "span", classes: "q-chunk" },
      model: (viewElement, { writer }) => {
        const orderAttr = viewElement.getAttribute("data-order");
        return writer.createElement("chunk", {
          chunkId: viewElement.getAttribute("data-chunk-id") || generateChunkId(),
          text: viewElement.getChild(0)?.is("$text") ? (viewElement.getChild(0) as any).data : "",
          order: orderAttr ? Number(orderAttr) : null
        });
      }
    });

    conversion.for("editingDowncast").elementToElement({
      model: "chunk",
      view: (modelElement, { writer }) => {
        const order = modelElement.getAttribute("order");
        const span = writer.createContainerElement("span", {
          class: "q-chunk",
          "data-chunk-id": modelElement.getAttribute("chunkId"),
          ...(typeof order === "number" ? { "data-order": String(order) } : {})
        });
        writer.insert(writer.createPositionAt(span, 0), writer.createText((modelElement.getAttribute("text") as string) || ""));
        return toWidget(span, writer, { label: "Khối sắp xếp" });
      }
    });

    conversion.for("dataDowncast").elementToElement({
      model: "chunk",
      view: (modelElement, { writer }) => {
        const order = modelElement.getAttribute("order");
        const span = writer.createContainerElement("span", {
          class: "q-chunk",
          "data-chunk-id": modelElement.getAttribute("chunkId"),
          ...(typeof order === "number" ? { "data-order": String(order) } : {})
        });
        writer.insert(writer.createPositionAt(span, 0), writer.createText((modelElement.getAttribute("text") as string) || ""));
        return span;
      }
    });

    // Mirror BlankWidgetPlugin (xem Javadoc fix bug "reconvert lồng nhau" ở đó) — "order" đổi hàng
    // loạt qua panel "Gán thứ tự đúng" phải tự reconvert, gom theo `change:data` (sau khi cả batch
    // model.change() xong), an toàn khi đổi nhiều khối cùng lúc.
    this.editor.model.document.on("change:data", () => {
      const toReconvert = new Set<any>();
      for (const change of this.editor.model.document.differ.getChanges()) {
        if (change.type !== "attribute" || change.attributeKey !== "order") continue;
        const item = (change as any).range?.start?.nodeAfter;
        if (item && item.is && item.is("element", "chunk")) toReconvert.add(item);
      }
      for (const item of toReconvert) this.editor.editing.reconvertItem(item);
    });
  }
}
