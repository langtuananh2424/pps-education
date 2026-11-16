import { Plugin, createDropdown, addToolbarToDropdown, ToolbarSeparatorView, ToolbarLineBreakView } from "ckeditor5";
import type { Editor } from "ckeditor5";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — gom TOÀN BỘ nút widget riêng của hệ thống
 * (Ô trống/Hộp từ vựng/Nhóm lựa chọn/Khối sắp xếp/Lựa chọn trong câu/Bảng lưới/Audio/Dọn dòng & Căn
 * đều — 17 nút) vào 1 DROPDOWN DUY NHẤT "Chèn câu hỏi" thay vì rải thẳng ra toolbar chiếm nhiều dòng.
 * Người dùng BẤM MỞ/ĐÓNG khi cần — không chiếm chỗ toolbar lúc không dùng.
 *
 * LƯU Ý quan trọng (đã sửa lại theo đúng ý người dùng sau lần đầu làm sai) — bên TRONG panel xổ
 * xuống, các nút KHÔNG xếp thành 1 list dọc từng dòng (nhìn như menu danh sách), mà vẫn giữ layout
 * "cục" nhiều hàng/nhiều cột y như toolbar gốc trước khi gộp vào dropdown (các nhóm cách nhau bởi
 * `ToolbarSeparatorView`/`ToolbarLineBreakView`, tự xuống dòng khi hết chỗ ngang) — chỉ khác là cả
 * CỤC đó giờ ẩn trong 1 panel bấm mở/đóng, không nằm thẳng trên toolbar chính nữa. Không truyền
 * `isVertical: true` (mặc định `addToolbarToDropdown` tạo `ToolbarView` KHÔNG gom "...", tự xuống
 * dòng theo chiều ngang khi hết chỗ — đúng layout "shouldNotGroupWhenFull" cũ, chỉ nằm trong panel).
 *
 * Dùng `addToolbarToDropdown` (API CHÍNH THỨC của CKEditor5, cùng cơ chế nút "..."/"Chèn bảng" built-in
 * dùng) — KHÔNG tự viết lại: chỉ gọi lại `editor.ui.componentFactory.create(name)` cho từng nút ĐÃ
 * đăng ký sẵn ở các plugin khác (BlankWidgetPlugin, WordBankWidgetPlugin...), không định nghĩa lại
 * hành vi nút nào. Toolbar con bên trong panel chỉ dựng 1 LẦN khi GV mở dropdown lần đầu (lazy).
 *
 * "|" → `ToolbarSeparatorView` (vạch dọc, cùng hàng), "-" → `ToolbarLineBreakView` (ngắt hàng cứng) —
 * mirror đúng cách CKEditor5 tự diễn giải chuỗi `"|"`/`"-"` trong config `toolbar.items` chuẩn
 * (xem `ToolbarView#_buildItemsFromConfig`). Đặt "Dọn dòng & Căn đều" ở CUỐI, chung hàng với nhóm
 * Audio (không để lẻ loi 1 mình ở hàng đầu như bản trước).
 */
const WIDGET_TOKENS: string[] = [
  "insertBlank", "setBlankAnswer", "|",
  "insertWordBank", "editWordBank", "|",
  "insertChoiceGroup", "insertTrueFalse", "addChoiceOption", "toggleChoiceCorrect", "|",
  "insertChunk", "|",
  "insertInlineChoice", "editInlineChoice",
  "-",
  "insertChoiceGrid", "addGridRow", "removeGridRow", "setGridRowCorrect", "editGridOptions", "|",
  "insertAudio", "uploadAudio", "editAudio", "|",
  "cleanupAndJustify"
];

export default class QuestionWidgetsMenuPlugin extends Plugin {
  init() {
    const editor: Editor = this.editor;

    editor.ui.componentFactory.add("insertQuestionWidget", (locale) => {
      const dropdownView = createDropdown(locale);
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — KHÔNG hiện icon + chữ "Chèn câu hỏi"
      // chiếm chỗ trên toolbar nữa, CHỈ còn đúng mũi tên xổ xuống (`ck-dropdown__arrow` —
      // DropdownView tự vẽ, không cần icon riêng) đặt ở GÓC PHẢI của khung soạn — xem CSS
      // `.ck-pps-insert-question-widget` (margin-left: auto đẩy sát mép phải hàng toolbar).
      dropdownView.set("class", "ck-pps-insert-question-widget");
      dropdownView.buttonView.set({
        withText: false,
        tooltip: "Chèn câu hỏi: Ô trống, Hộp từ vựng, Nhóm lựa chọn, Khối sắp xếp, Lựa chọn trong câu, Bảng lưới, Audio..."
      });

      addToolbarToDropdown(
        dropdownView,
        () =>
          WIDGET_TOKENS.map((token) => {
            if (token === "|") return new ToolbarSeparatorView();
            if (token === "-") return new ToolbarLineBreakView();
            return editor.ui.componentFactory.create(token);
          }),
        { class: "ck-pps-question-widgets-dropdown" }
      );

      return dropdownView;
    });
  }
}
