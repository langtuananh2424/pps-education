package vn.com.pps.education.lms.dto;

/**
 * Bổ sung 2026-10-19 (đã xác nhận với người dùng) — 1 dòng THÔ của file Excel đang xem trước, đủ
 * nguyên 21 cột để FE dựng bảng dạng Excel cho sửa trực tiếp (QuestionImportPanel.tsx), rồi dựng lại
 * file .xlsx từ dữ liệu đã sửa (buildXlsxTemplateBlob phía FE) gửi lại qua ĐÚNG pipeline parse/
 * validate hiện có — không viết thêm bộ validate song song bằng JS (xem .claude/rules/solid.md mục D).
 * Mirror 1:1 field của {@link vn.com.pps.education.lms.service.QuestionRowParser.ParsedQuestionRow}
 * (dữ liệu thô CHƯA ép kiểu), cộng {@code valid}/{@code error} từ kết quả validate ở
 * QuestionImportService#importQuestionsIntoBank.
 */
public record QuestionImportRowPreview(
        int rowNumber,
        String sheetLabel,
        String kind,
        String difficulty,
        String content,
        String choiceA,
        String choiceB,
        String choiceC,
        String choiceD,
        String choiceE,
        String choiceF,
        String choiceG,
        String choiceH,
        String choiceI,
        String choiceJ,
        String correctAnswer,
        String audioUrl,
        String imageUrl,
        String referencePassage,
        String defaultPoints,
        String explanation,
        String tags,
        String instruction,
        boolean valid,
        String error
) {}
