import { ButtonView, Command, Plugin } from "ckeditor5";
import { IconImageUpload, IconMedia, IconPencil } from "ckeditor5";
import type { Editor } from "ckeditor5";
import { toWidget, Widget } from "ckeditor5";
import { uploadQuestionMedia } from "./mediaUpload";
import { alertDialog, promptDialog } from "./dialogs";

/**
 * Chuyển từ PoC (feature/ckeditor5-poc) sang khung soạn câu hỏi THẬT — widget "Audio", phục vụ các
 * câu cần nghe. CKEditor5 KHÔNG có sẵn plugin "Audio" chính thức (MediaEmbed chỉ nhận link YouTube/
 * Vimeo... qua oEmbed, không phải file audio thô) — widget tự viết, nhúng thẳng thẻ HTML5
 * `<audio controls>` chuẩn.
 *
 * Backend TỰ TRÍCH {@code src} của audio ĐẦU TIÊN tìm thấy thành {@code Question.audioUrl} (và tự
 * set skill=LISTENING) rồi BÓC widget khỏi content khi lưu — xem Javadoc
 * CkeditorHtmlSanitizer#extractAndRemoveAudioSrc (backend) — tránh phát LẶP 2 trình phát (1 từ
 * content, 1 từ audioUrl chuẩn mà QuestionPreview/TakeExerciseModal đã tự vẽ riêng). V1 — chỉ nhận
 * ĐÚNG 1 audio/câu, khớp field audioUrl đơn (không phải mảng).
 *
 * Chỉ qua URL (chưa có backend nhận upload file audio) — giống Ảnh.
 */
class InsertAudioCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const url = await promptDialog({ title: "Chèn Audio", label: "Dán URL file audio (mp3/wav...):", defaultValue: "https://" });
    if (!url) return;
    editor.model.change((writer) => {
      const audio = writer.createElement("audio", { src: url.trim() });
      editor.model.insertObject(audio, null, null, { setSelection: "after" });
    });
  }

  override refresh() {
    const model = this.editor.model;
    const selection = model.document.selection;
    const parent = selection.focus!.parent as any;
    const checkAt = parent.is("element", "$root") ? parent : parent.parent;
    this.isEnabled = !!checkAt && model.schema.checkChild(checkAt, "audio");
  }
}

/** Chọn file audio từ máy → tải lên kho media → chèn widget Audio với URL vừa nhận. */
class UploadAudioCommand extends Command {
  override execute() {
    const editor = this.editor;
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "audio/*";
    input.onchange = async () => {
      const file = input.files?.[0];
      if (!file) return;
      try {
        const url = await uploadQuestionMedia(file);
        editor.model.change((writer) => {
          const audio = writer.createElement("audio", { src: url });
          editor.model.insertObject(audio, null, null, { setSelection: "after" });
        });
      } catch (err) {
        await alertDialog(err instanceof Error ? `Tải audio thất bại: ${err.message}` : "Tải audio thất bại.");
      }
    };
    input.click();
  }

  override refresh() {
    const model = this.editor.model;
    const selection = model.document.selection;
    const parent = selection.focus!.parent as any;
    const checkAt = parent.is("element", "$root") ? parent : parent.parent;
    this.isEnabled = !!checkAt && model.schema.checkChild(checkAt, "audio");
  }
}

class EditAudioCommand extends Command {
  override async execute() {
    const editor = this.editor;
    const element = this.getSelectedAudio();
    if (!element) return;
    const current = (element.getAttribute("src") as string) || "";
    const url = await promptDialog({ title: "Sửa URL Audio", label: "Sửa URL file audio:", defaultValue: current });
    if (!url) return;
    editor.model.change((writer) => {
      writer.setAttribute("src", url.trim(), element);
    });
  }

  override refresh() {
    this.isEnabled = !!this.getSelectedAudio();
  }

  private getSelectedAudio() {
    const selection = this.editor.model.document.selection;
    const element = selection.getSelectedElement();
    return element && element.name === "audio" ? element : null;
  }
}

/** Dùng cho CkeditorWordBankComposer.tsx đếm/đọc URL audio hiện có trong tài liệu để hiện trạng thái. */
export function findFirstAudioSrc(editor: Editor): string | null {
  const root = editor.model.document.getRoot();
  if (!root) return null;
  const range = editor.model.createRangeIn(root);
  for (const value of range) {
    if (value.type === "elementStart" && value.item.is("element", "audio")) {
      return (value.item.getAttribute("src") as string) || null;
    }
  }
  return null;
}

export default class AudioWidgetPlugin extends Plugin {
  static get requires() {
    return [Widget];
  }

  init() {
    const editor: Editor = this.editor;
    this._defineSchema();
    this._defineConverters();
    editor.commands.add("insertAudio", new InsertAudioCommand(editor));
    editor.commands.add("uploadAudio", new UploadAudioCommand(editor));
    editor.commands.add("editAudio", new EditAudioCommand(editor));
    this._defineUI();
    // KHÔNG đăng ký phím tắt — xem giải thích ở JustifyCleanupPlugin.ts. Dùng nút trên toolbar.
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

    addButton("insertAudio", "Chèn Audio (link)", "insertAudio", IconMedia);
    addButton("uploadAudio", "Tải Audio từ máy", "uploadAudio", IconImageUpload);
    addButton("editAudio", "Sửa URL Audio", "editAudio", IconPencil);
  }

  private _defineSchema() {
    const schema = this.editor.model.schema;
    schema.register("audio", {
      inheritAllFrom: "$blockObject",
      allowAttributes: ["src"]
    });
  }

  private _defineConverters() {
    const conversion = this.editor.conversion;

    conversion.for("upcast").elementToElement({
      view: { name: "audio" },
      model: (viewElement, { writer }) => writer.createElement("audio", { src: viewElement.getAttribute("src") || "" })
    });

    conversion.for("editingDowncast").elementToElement({
      model: "audio",
      view: (modelElement, { writer }) => {
        const wrapper = writer.createContainerElement("div", { class: "q-audio" });
        const player = writer.createRawElement("audio", {
          controls: "controls",
          src: modelElement.getAttribute("src") as string
        });
        writer.insert(writer.createPositionAt(wrapper, 0), player);
        return toWidget(wrapper, writer, { label: "Audio" });
      }
    });

    conversion.for("dataDowncast").elementToElement({
      model: "audio",
      view: (modelElement, { writer }) =>
        writer.createContainerElement("audio", {
          controls: "controls",
          src: modelElement.getAttribute("src") as string,
          class: "q-audio"
        })
    });

    // Mirror BlankWidgetPlugin (xem Javadoc fix bug "reconvert lồng nhau" ở đó) — `createRawElement`
    // không tự vẽ lại khi "src" đổi qua EditAudioCommand, phải tự reconvert.
    this.editor.model.document.on("change:data", () => {
      const toReconvert = new Set<any>();
      for (const change of this.editor.model.document.differ.getChanges()) {
        if (change.type !== "attribute" || change.attributeKey !== "src") continue;
        const item = (change as any).range?.start?.nodeAfter;
        if (item && item.is && item.is("element", "audio")) toReconvert.add(item);
      }
      for (const item of toReconvert) this.editor.editing.reconvertItem(item);
    });
  }
}
