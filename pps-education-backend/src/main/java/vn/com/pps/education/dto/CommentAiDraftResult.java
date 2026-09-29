package vn.com.pps.education.dto;

import java.util.List;

/**
 * UC-74 bước 8 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-28) — BẢN XEM TRƯỚC nhận xét
 * do trợ lý AI soạn, chưa ghi DB. Mỗi dòng chỉ mang 2 trường AI được phép điền ({@code attitude},
 * {@code content}) — không có field điểm/BTVN nào để AI không thể đụng tới các trường cần con số chính xác.
 *
 * @param extraction các ý đã tách từ lời giáo viên — FE gửi lại khi bấm "Viết lại cho đa dạng hơn"
 *                   (không phải gọi lại STT/tách ý, không đổi nội dung, chỉ đổi câu chữ).
 */
public record CommentAiDraftResult(
        String transcript,
        String assistantMessage,
        Extraction extraction,
        List<Row> rows,
        List<UnmatchedMention> unmatchedMentions,
        List<SkippedStudent> skippedStudents
) {

    /**
     * @param teacherPronoun đại từ giáo viên tự xưng ("thầy"/"cô") lấy từ lời nói/ghi chú — AI viết nhất quán
     *                       theo đại từ này; {@code null} khi không xác định được (viết "thầy/cô").
     */
    public record Extraction(String classAttitude, List<String> classPoints, List<IndividualPoints> individuals,
                             String teacherPronoun) {
    }

    public record IndividualPoints(Long studentId, String attitude, List<String> points, String evidence) {
    }

    /** @param source {@code CLASS} (dùng ý chung cả lớp) hoặc {@code INDIVIDUAL} (được giáo viên nhắc riêng). */
    public record Row(Long studentId, String studentFullName, String attitude, String content, String source,
                      List<Warning> warnings) {
    }

    /**
     * @param type {@code SIMILAR_IN_SESSION} (giống học sinh khác trong buổi), {@code SIMILAR_TO_PREVIOUS}
     *             (giống nhận xét buổi trước của chính học sinh), {@code LESSON_TITLE} (nhắc tên bài học), {@code CONTAINS_DIGITS} (có chữ số — AI
     *             không được ghi điểm/số liệu), {@code NOT_WRITTEN} (AI không trả nhận xét cho học sinh này).
     * @param similarity tỷ lệ trùng 0..1 (chỉ có với 2 loại SIMILAR_*).
     */
    public record Warning(String type, String message, Double similarity) {
    }

    /** UC-74 A6 — câu nhắc tên chưa gắn được chắc chắn với 1 học sinh. */
    public record UnmatchedMention(String quote, List<Long> candidateStudentIds) {
    }

    /** Học sinh không được soạn (Vắng/Có phép, hoặc nhận xét buổi này đã Gửi duyệt/Đã duyệt). */
    public record SkippedStudent(Long studentId, String studentFullName, String reason) {
    }
}
