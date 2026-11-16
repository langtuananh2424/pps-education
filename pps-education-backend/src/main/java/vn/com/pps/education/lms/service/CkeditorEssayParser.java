package vn.com.pps.education.lms.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — đọc HTML do CKEditor5 soạn khi GV
 * soạn câu "Tự luận" (ESSAY, chấm AI) bằng tab "Soạn tự do (CKEditor5)". KHÁC HẲN các parser widget
 * khác (Ô trống/Nhóm lựa chọn/Khối sắp xếp/Bảng lưới) — câu Tự luận KHÔNG CẦN bất kỳ widget đáp án
 * nào (không có đáp án đúng/sai để máy chấm, học sinh viết tự do, AI chấm sau khi nộp — luồng chấm
 * đã có sẵn, chạy dựa thuần trên {@code QuestionType.ESSAY}, không quan tâm câu hỏi được soạn bằng
 * form tay hay CKEditor5). Vì vậy parser này CHỈ sanitize + trích audio (VD "Nghe đoạn hội thoại rồi
 * viết lại ý chính") — KHÔNG đòi hỏi/parse widget nào, nhưng TỪ CHỐI nếu phát hiện widget đáp án
 * (Ô trống/Nhóm lựa chọn/Khối sắp xếp/Bảng lưới/Lựa chọn trong câu) lẫn vào, tránh GV nhầm lẫn nộp
 * nhầm 1 câu vừa có đáp án tự động vừa đánh dấu Tự luận.
 */
@Service
public class CkeditorEssayParser {

    private final CkeditorHtmlSanitizer sanitizer;

    public CkeditorEssayParser(CkeditorHtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    /** {@code audioUrl}: trích từ widget "Audio" nếu có — xem Javadoc {@link CkeditorHtmlSanitizer#extractAndRemoveAudioSrc}. */
    public record ParsedEssay(String sanitizedHtml, String audioUrl) {
    }

    public ParsedEssay parse(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            throw new IllegalArgumentException("Nội dung đề bài trống.");
        }

        String sanitized = sanitizer.sanitize(rawHtml);
        Document doc = Jsoup.parseBodyFragment(sanitized);
        String audioUrl = sanitizer.extractAndRemoveAudioSrc(doc);

        if (!doc.select(".q-blank, .q-choice-group, .q-word-bank, .q-chunk, .q-grid, .q-grid-row, .q-inline-choice").isEmpty()) {
            throw new IllegalArgumentException(
                    "Câu Tự luận không được có Ô trống/Nhóm lựa chọn/Khối sắp xếp/Bảng lưới/Lựa chọn trong câu — "
                            + "xoá các widget đó hoặc bỏ đánh dấu \"Tự luận\" nếu câu này thật ra cần đáp án tự động.");
        }

        String content = doc.body().html().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("Chưa nhập đề bài Tự luận — cần ít nhất 1 dòng mô tả yêu cầu viết.");
        }

        return new ParsedEssay(content, audioUrl);
    }
}
