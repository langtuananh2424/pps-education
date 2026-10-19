package vn.com.pps.education.lms.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): kết quả soạn
 * đề nhanh qua file Excel/Word — mirror GradeImportResponse (UC-53), cộng
 * {@code createdQuestions} để FE gắn thẳng câu hỏi vừa tạo vào 1 đề đang
 * soạn (nếu import được gọi từ trong luồng "Soạn & giao đề") mà không cần
 * gọi lại API xem chi tiết cho từng câu.
 */
public record QuestionImportResponse(
        Long id,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary,
        List<QuestionImportedRow> createdQuestions,
        /**
         * Bổ sung 2026-10-19 (đã xác nhận với người dùng) — nguyên 21 cột thô + trạng thái hợp lệ của
         * TỪNG dòng file, chỉ có ý nghĩa khi {@code status="PREVIEW"} (xem QuestionImportService#
         * previewImport) — dùng để FE dựng bảng sửa trực tiếp (QuestionImportPanel.tsx). Rỗng ở kết
         * quả ghi DB thật (COMPLETED/PARTIAL_SUCCESS/FAILED) vì không còn cần sửa nữa.
         */
        List<QuestionImportRowPreview> rowPreviews
) {
    public record QuestionImportedRow(Long id, String content, BigDecimal defaultPoints) {}
}
