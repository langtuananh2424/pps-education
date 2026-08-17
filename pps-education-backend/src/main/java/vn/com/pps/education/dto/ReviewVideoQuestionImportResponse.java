package vn.com.pps.education.dto;

import java.util.List;
import java.util.Map;

/**
 * UC-23 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — kết quả
 * import Excel câu hỏi Kho Video Ôn tập (REFLEX hoặc CONNECTION), mirror
 * {@link QuestionImportResponse} của Kho đề (UC-40) nhưng
 * {@code createdQuestions} chỉ cần id + mô tả ngắn (không có defaultPoints
 * vì câu hỏi Kho Video Ôn tập không chấm điểm theo thang điểm).
 */
public record ReviewVideoQuestionImportResponse(
        Long jobId,
        String sourceFileName,
        Integer totalRows,
        int successRows,
        int failedRows,
        String status,
        List<Map<String, Object>> errorSummary,
        List<ImportedQuestion> createdQuestions
) {
    public record ImportedQuestion(Long id, String summary) {
    }
}
