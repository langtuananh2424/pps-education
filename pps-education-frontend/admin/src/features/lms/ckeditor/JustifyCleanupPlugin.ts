import { ButtonView, Command, Plugin } from "ckeditor5";
import { IconAlignJustify } from "ckeditor5";
import type { Editor } from "ckeditor5";

/**
 * Bổ sung ngoài PoC, đã xác nhận với người dùng — khi dán đề từ Word/PDF, mỗi dòng (ngắt dòng cứng
 * trong file gốc) thường rơi vào 1 `<p>` RIÊNG (hoặc 1 xuống dòng MỀM `<br>`/Shift+Enter ngay TRONG
 * CÙNG 1 `<p>`, tuỳ nguồn dán — xác nhận thật qua báo lỗi của người dùng: phần đầu là `<p>` tách
 * rời, phần sau lại là 1 `<p>` DUY NHẤT chứa nhiều `<br>`) thay vì 1 đoạn văn liền mạch, nên GV phải
 * tự tay xoá từng chỗ ngắt dòng rồi mới căn justify được — chậm. 1 nút DUY NHẤT xử lý CẢ 2 trường
 * hợp: (1) xoá mọi xuống dòng mềm (`softBreak`) trong tài liệu, thay bằng khoảng trắng; (2) gộp các
 * đoạn văn thường (`paragraph`) LIỀN KỀ nhau thành 1 đoạn (nối bằng dấu cách); rồi căn "Justify" cho
 * đoạn văn. CHỈ đụng `paragraph`/`softBreak` — không đụng heading/list/table/widget (blank/wordBank
 * là block/inline object riêng, schema không cho gộp nhầm vào).
 *
 * Phạm vi áp dụng (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — ĐANG CÓ bôi đen (selection
 * không rỗng) → CHỈ áp dụng cho các đoạn văn nằm trong vùng bôi đen đó (dùng
 * `Selection#getSelectedBlocks()` — API chuẩn CKEditor5 trả về MỌI block mà selection chạm tới);
 * KHÔNG bôi đen gì (chỉ đặt con trỏ) → áp dụng cho CẢ tài liệu như trước, tiện khi GV vừa dán xong
 * toàn bộ đề và muốn dọn 1 lần cho hết.
 */
class CleanupAndJustifyCommand extends Command {
  override execute() {
    const editor = this.editor;
    const model = editor.model;
    const root = model.document.getRoot();
    if (!root) return;

    const selection = model.document.selection;
    const scopedBlocks: Set<any> | null = selection.isCollapsed ? null : new Set(Array.from(selection.getSelectedBlocks()));

    model.change((writer) => {
      // (1) Xoá mọi xuống dòng mềm (Shift+Enter) trong phạm vi — thay mỗi `softBreak` bằng 1 khoảng trắng.
      const softBreaks: any[] = [];
      for (const value of model.createRangeIn(root)) {
        if (value.type === "elementStart" && value.item.is("element", "softBreak")) {
          if (!scopedBlocks || scopedBlocks.has(value.item.parent)) softBreaks.push(value.item);
        }
      }
      for (const sb of softBreaks) {
        const pos = writer.createPositionBefore(sb);
        writer.remove(writer.createRangeOn(sb));
        writer.insertText(" ", pos);
      }

      // (2) Gộp các đoạn `paragraph` liền kề thành 1 (CHỈ khi CẢ HAI đoạn đều trong phạm vi) — quét
      // lại TỪ ĐẦU sau MỖI lần gộp (break ngay khi gộp xong 1 cặp) thay vì tiếp tục trên 1 con trỏ
      // cũ: bản đầu chỉ gộp đúng 1 cặp rồi dừng oan (bug thật, người dùng báo lại sau khi bấm nút
      // vẫn chỉ gộp 1 dòng tiêu đề + 1 đoạn đầu).
      let mergedSomething = true;
      while (mergedSomething) {
        mergedSomething = false;
        for (const node of Array.from(root.getChildren())) {
          if (!node.is("element", "paragraph")) continue;
          if (scopedBlocks && !scopedBlocks.has(node)) continue;
          const next = node.nextSibling;
          if (next && next.is("element", "paragraph") && (!scopedBlocks || scopedBlocks.has(next))) {
            writer.insertText(" ", writer.createPositionAt(node, "end"));
            writer.merge(writer.createPositionAfter(node));
            mergedSomething = true;
            break; // cây đã đổi — quét lại từ đầu ở vòng while ngoài, không dùng tiếp danh sách cũ
          }
        }
      }

      // Căn "Justify" cho các đoạn văn trong phạm vi.
      for (const node of root.getChildren()) {
        if (node.is("element", "paragraph") && (!scopedBlocks || scopedBlocks.has(node))) {
          writer.setAttribute("alignment", "justify", node);
        }
      }
    });
  }

  override refresh() {
    this.isEnabled = true;
  }
}

export default class JustifyCleanupPlugin extends Plugin {
  init() {
    const editor: Editor = this.editor;
    editor.commands.add("cleanupAndJustify", new CleanupAndJustifyCommand(editor));

    editor.ui.componentFactory.add("cleanupAndJustify", (locale) => {
      const button = new ButtonView(locale);
      button.set({
        label: "Dọn dòng & Căn đều",
        withText: true,
        tooltip: "Gộp các dòng rời rạc (dán từ Word/PDF) thành đoạn văn liền mạch, rồi căn đều 2 bên — chỉ áp dụng cho phần đang bôi đen, hoặc cả bài nếu không bôi đen",
        icon: IconAlignJustify
      });
      button.on("execute", () => editor.execute("cleanupAndJustify"));
      return button;
    });

    // KHÔNG đăng ký phím tắt — đã thử "Ctrl+Alt+J" (trùng AltGr bàn phím tiếng Việt) rồi
    // "Ctrl+Shift+J" (trùng phím tắt mở DevTools Console của Chrome), cả 2 đều bị hệ điều hành/
    // trình duyệt chặn trước khi tới CKEditor5. Nút bấm trên toolbar là cách DUY NHẤT chắc chắn
    // không xung đột — xem _defineUI ở trên.
  }
}
