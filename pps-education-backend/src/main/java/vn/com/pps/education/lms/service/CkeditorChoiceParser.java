package vn.com.pps.education.lms.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — đọc HTML do CKEditor5 soạn khi GV
 * dùng widget "Nhóm lựa chọn" (tab "Soạn tự do (CKEditor5)") thành {@code QuestionType.MULTIPLE_CHOICE}/
 * {@code MULTIPLE_ANSWER} (suy ra từ SỐ LƯỢNG lựa chọn đánh dấu đúng — 1 → MULTIPLE_CHOICE, ≥2 →
 * MULTIPLE_ANSWER) + {@code choices[]} CHUẨN, tái dùng 100% UI/chấm điểm phía Học viên hiện có.
 *
 * KHÁC hẳn {@link CkeditorWordBankParser} ở chỗ: widget "Nhóm lựa chọn" KHÔNG giữ lại trong
 * {@code content} — bị BÓC RA và chuyển thành {@code choices[]} (xem {@code stemHtml}), vì Học viên
 * luôn render lựa chọn từ bảng {@code choices} có sẵn (ChoiceButtons/TakeExerciseModal), giữ nguyên
 * HTML widget trong content sẽ khiến lựa chọn hiện LẶP 2 LẦN — đã xác nhận với người dùng khi quyết
 * định hướng này (khác Ô trống: Ô trống KHÔNG có đường hiển thị chuẩn sẵn có để tái dùng nên phải
 * giữ nguyên HTML + xây thêm tầng hiển thị riêng).
 *
 * V1 — CHỈ hỗ trợ ĐÚNG 1 nhóm lựa chọn/tài liệu (ánh xạ 1 Question = 1 nhóm). Nhiều nhóm trong cùng
 * 1 tài liệu bị từ chối — GV tách thành nhiều câu riêng (mirror ràng buộc "KHÔNG trộn Ô trống và
 * Nhóm lựa chọn trong 1 câu" đã áp cho FE, xem CkeditorWordBankComposer.tsx).
 */
@Service
public class CkeditorChoiceParser {

    private final CkeditorHtmlSanitizer sanitizer;

    public CkeditorChoiceParser(CkeditorHtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public record ParsedChoiceOption(String content, String imageUrl, boolean isCorrect) {
    }

    /** {@code stemHtml}: HTML còn lại SAU KHI bóc widget Nhóm lựa chọn ra — lưu vào {@code Question.content}.
     * {@code audioUrl}: trích từ widget "Audio" nếu có — xem Javadoc {@link CkeditorHtmlSanitizer#extractAndRemoveAudioSrc}.
     * {@code trueFalse}: GV chèn qua nút "Chèn Đúng/Sai" riêng (bổ sung 2026-11-15, đã xác nhận với
     * người dùng) — map sang {@code QuestionType.TRUE_FALSE} thay vì suy luận MULTIPLE_CHOICE/
     * MULTIPLE_ANSWER như nhóm lựa chọn thường (xem QuestionBankService#buildCkeditorChoiceRequest). */
    public record ParsedChoice(String stemHtml, boolean multipleAnswer, boolean trueFalse, List<ParsedChoiceOption> options, String audioUrl) {
    }

    public ParsedChoice parse(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            throw new IllegalArgumentException("Nội dung soạn đề trống.");
        }

        String sanitized = sanitizer.sanitize(rawHtml);
        Document doc = Jsoup.parseBodyFragment(sanitized);
        String audioUrl = sanitizer.extractAndRemoveAudioSrc(doc);

        Elements groups = doc.select(".q-choice-group");
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy nhóm lựa chọn nào trong nội dung — cần ít nhất 1 nhóm (bấm \"Chèn nhóm lựa chọn\" khi soạn).");
        }
        if (groups.size() > 1) {
            throw new IllegalArgumentException("Mỗi câu hỏi chỉ hỗ trợ ĐÚNG 1 nhóm lựa chọn — tách thành nhiều câu riêng nếu cần nhiều nhóm.");
        }
        Element group = groups.first();
        boolean trueFalse = "true".equals(group.attr("data-true-false"));

        List<ParsedChoiceOption> options = new ArrayList<>();
        int letterIndex = 0;
        for (Element optionEl : group.select(".q-choice-option")) {
            Element img = optionEl.selectFirst("img");
            String imageUrl = img != null ? img.attr("src") : null;
            String text = optionEl.text().trim();
            String choiceLabel = String.valueOf((char) ('A' + letterIndex));
            letterIndex++;
            String content = !text.isEmpty() ? text : choiceLabel;
            boolean isCorrect = "true".equals(optionEl.attr("data-correct"));
            options.add(new ParsedChoiceOption(content, (imageUrl == null || imageUrl.isBlank()) ? null : imageUrl, isCorrect));
        }
        if (options.size() < 2) {
            throw new IllegalArgumentException("Nhóm lựa chọn cần ít nhất 2 lựa chọn.");
        }
        if (trueFalse && options.size() != 2) {
            throw new IllegalArgumentException("Câu Đúng/Sai phải có ĐÚNG 2 lựa chọn — đừng thêm/xoá lựa chọn khỏi nhóm này.");
        }
        long correctCount = options.stream().filter(ParsedChoiceOption::isCorrect).count();
        if (correctCount == 0) {
            throw new IllegalArgumentException("Chưa đánh dấu lựa chọn nào là đáp án đúng — bấm \"Đánh dấu đúng/sai\" khi chọn 1 lựa chọn.");
        }
        if (trueFalse && correctCount != 1) {
            throw new IllegalArgumentException("Câu Đúng/Sai chỉ được đánh dấu ĐÚNG 1 trong 2 lựa chọn.");
        }

        // Bóc nguyên khối "Nhóm lựa chọn" ra khỏi content — còn lại là đoạn văn/câu dẫn (stem).
        group.remove();
        String stemHtml = doc.body().html().trim();
        if (stemHtml.isBlank()) {
            throw new IllegalArgumentException("Chưa nhập đoạn dẫn/câu hỏi (phần chữ ngoài nhóm lựa chọn) — cần ít nhất 1 dòng, VD \"Chọn đáp án đúng:\".");
        }

        return new ParsedChoice(stemHtml, correctCount >= 2, trueFalse, options, audioUrl);
    }
}
