package vn.com.pps.education.service;

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
     * hoặc số thứ tự block Word, 1-based).
     */
    record ParsedQuestionRow(
            int rowNumber,
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
            String tags
    ) {}
}
