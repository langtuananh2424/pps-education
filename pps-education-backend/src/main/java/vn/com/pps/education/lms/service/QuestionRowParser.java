package vn.com.pps.education.lms.service;

import java.io.InputStream;
import java.util.List;

/**
 * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — soạn đề
 * nhanh bằng file mẫu): đọc 1 định dạng file thành danh sách câu hỏi thô,
 * CHƯA validate nghiệp vụ (đủ 4 đáp án, cần audioUrl với Voice...) — việc
 * đó thuộc về QuestionImportService (SRP: parser chỉ biết đọc đúng cú
 * pháp định dạng file, không biết quy tắc 1 câu hỏi hợp lệ theo loại).
 *
 * Thêm định dạng import mới (VD sau này) = thêm 1 implementation, không
 * sửa QuestionImportService — xem ví dụ AttendanceMethod ở
 * .claude/rules/solid.md (Open/Closed).
 */
public interface QuestionRowParser {

    /** true nếu parser này đọc được file theo tên (phần mở rộng). */
    boolean supports(String filename);

    /** Đọc toàn bộ file, trả về danh sách câu hỏi thô theo đúng thứ tự trong file (1 phần tử = 1 câu hỏi). */
    List<ParsedQuestionRow> parse(InputStream inputStream);

    /**
     * Dữ liệu 1 câu hỏi đọc thô từ file — toàn String, KHÔNG parse
     * enum/số/bắt buộc ở đây (QuestionImportService làm việc đó, xem
     * mapToRequest). {@code rowNumber} dùng để báo lỗi (số dòng Excel
     * hoặc số thứ tự block Word, 1-based — tính RIÊNG theo từng sheet khi
     * file Excel nhiều sheet, xem {@code sheetLabel}).
     *
     * {@code sheetLabel} (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng 2026-10-05 — mẫu Excel nhiều sheet theo nhóm loại câu hỏi) — tên
     * sheet Excel chứa dòng này (VD "Trắc nghiệm"), dùng để báo lỗi rõ hơn
     * khi file có nhiều sheet (ghép "Tên sheet · dòng N" thay vì chỉ "dòng
     * N", xem QuestionImportService#rowError). {@code null} khi file chỉ 1
     * sheet (hành vi cũ) hoặc đọc từ Word (không có khái niệm sheet).
     */
    record ParsedQuestionRow(
            int rowNumber,
            String sheetLabel,
            String kind,
            String difficulty,
            String content,
            String choiceA,
            String choiceB,
            String choiceC,
            String choiceD,
            String correctAnswer,
            String audioUrl,
            String imageUrl,
            String referencePassage,
            String defaultPoints,
            String explanation,
            String tags,
            /**
             * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-14 — dòng hướng dẫn hiện phía
             * trên câu hỏi (chữ nghiêng, VD "Ex. 1: Fill in each blank..."), TÙY CHỌN, để trống không
             * sao. Chỉ có ý nghĩa với các loại WORD_BANK soạn tay nội dung đoạn văn/nhiều chỗ trống
             * (DIEN_TU_HOP_TU_VUNG/_ANH, DIEN_TU_DOAN_VAN) — xem QuestionImportService. Trước đây KHÔNG
             * có cột nào chứa được dòng này (cột "Transcript" đã dùng cho Hộp từ vựng), khiến đề dạng
             * "A. ... B. ..." phân nhóm trong 1 bộ không hiện được chỉ dẫn, học sinh khó hình dung đề yêu
             * cầu gì dù component hiển thị (WordBankBlock/WordBankPreview) đã có sẵn chỗ hiện (prop
             * `instruction`), trước đó chỉ truyền cho NGHE_PHIEU_THONG_TIN (tái dùng referencePassage).
             */
            String instruction,
            /**
             * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-17 — phục vụ loại
             * DOC_HIEU_NOI_DAP_AN (đọc hiểu — nối đáp án), cho tới 10 đáp án dùng chung (A-J, nới từ 8
             * lên 10 cùng ngày sau khi phát hiện 1 bài ngữ pháp nối câu thực tế dùng tới J) thay vì tối
             * đa 4 như DOC_HIEU_LUOI (VD bài "ghép N người với 1-trong-10 mô tả"). CHỈ
             * QuestionImportService#mapToMatchingGroupRequests đọc 6 field này — DOC_HIEU_LUOI
             * (buildSharedGroupChoices gọi với maxChoices=4), TRAC_NGHIEM (buildChoices) và DOC_DIEN_TU
             * (mapToClozeGroupRequests) không đụng tới, vẫn cap ở D như cũ.
             */
            String choiceE,
            String choiceF,
            String choiceG,
            String choiceH,
            String choiceI,
            String choiceJ,
            /**
             * Bổ sung 2026-10-19 (đã xác nhận với người dùng) — cột "Kỹ năng" tường minh
             * (LISTENING/READING/WRITING/SPEAKING/GRAMMAR/OTHER, khớp {@code Question.Skill}), dùng cho
             * MỌI kind — fix gap thật: trước đây skill chỉ tự suy ra NGẦM theo kind (NGHE_*→LISTENING,
             * SPEAKING→SPEAKING), các kind Đọc/Ngữ pháp/Viết luôn để null dù enum đã có đủ 6 giá trị từ
             * đầu (xem QuestionImportService#resolveSkill). BẮT BUỘC với ANH_TRAC_NGHIEM/ANH_DIEN_TU (2
             * kind dùng chung cho cả 4 kỹ năng, tên kind không tự nói lên kỹ năng như các kind cũ) — tùy
             * chọn với mọi kind khác (để trống giữ nguyên hành vi suy luận ngầm cũ).
             */
            String skill,
            /**
             * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-20 — "Số thứ tự hiển thị ghi
             * đè" (khớp số in sẵn trong ảnh/sơ đồ sách gốc, VD sơ đồ IELTS Listening đánh số 36-40),
             * xem Javadoc {@code Question.displayNumberOverride}. TÙY CHỌN, để trống giữ nguyên hành vi
             * tự đếm tuần tự cũ. Số nguyên thô CHƯA ép kiểu ở đây — xem
             * QuestionImportService#resolveDisplayNumberOverride.
             */
            String displayNumberOverride
    ) {}
}
