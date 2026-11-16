package vn.com.pps.education.lms.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-14 — đọc HTML do CKEditor5 soạn cho 2
 * dạng "1 khối nội dung sinh NHIỀU câu hỏi": widget "Bảng lưới" (đọc hiểu lưới/nối đáp án — mỗi
 * dòng 1 câu, các cột là bộ đáp án DÙNG CHUNG) và widget "Lựa chọn trong câu" (cloze — mỗi chỗ chọn
 * 1 câu, bộ đáp án RIÊNG). Cả 2 ra đúng dữ liệu đã có từ trước của GridQuestionBuilder/
 * ClozeQuestionBuilder: N câu MULTIPLE_CHOICE cùng 1 groupKey + cùng 1 referencePassage — màn Học
 * viên tự gộp theo groupKey, không đổi gì ở tầng chấm/hiển thị.
 *
 * Widget bị BÓC khỏi đoạn văn (lưới) hoặc thay bằng mốc "(n) ___" (lựa chọn trong câu — mirror định
 * dạng đoạn văn cloze cũ); phần còn lại lưu làm referencePassage chung. V1 — 1 tài liệu chỉ chứa
 * ĐÚNG 1 trong 2 dạng, và tối đa 1 bảng lưới.
 */
@Service
public class CkeditorGroupParser {

    private static final String OPTION_SEPARATOR = "\\|";

    private final CkeditorHtmlSanitizer sanitizer;

    public CkeditorGroupParser(CkeditorHtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public record ParsedOption(String content, boolean isCorrect) {
    }

    public record ParsedSubQuestion(String content, List<ParsedOption> options) {
    }

    /** {@code kind}: "grid" hoặc "cloze" — dùng làm tiền tố groupKey (mirror builder cũ). */
    public record ParsedGroup(String kind, String passageHtml, String audioUrl, List<ParsedSubQuestion> questions) {
    }

    public ParsedGroup parse(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            throw new IllegalArgumentException("Nội dung soạn đề trống.");
        }
        Document doc = Jsoup.parseBodyFragment(sanitizer.sanitize(rawHtml));
        String audioUrl = sanitizer.extractAndRemoveAudioSrc(doc);

        Elements grids = doc.select(".q-grid");
        Elements inlineChoices = doc.select(".q-inline-choice");
        if (grids.isEmpty() && inlineChoices.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy Bảng lưới hay Lựa chọn trong câu nào trong nội dung.");
        }
        if (!grids.isEmpty() && !inlineChoices.isEmpty()) {
            throw new IllegalArgumentException("Không trộn Bảng lưới và Lựa chọn trong câu trong cùng 1 lần tạo — tách riêng.");
        }
        return grids.isEmpty() ? parseCloze(doc, inlineChoices, audioUrl) : parseGrid(doc, grids, audioUrl);
    }

    private ParsedGroup parseGrid(Document doc, Elements grids, String audioUrl) {
        if (grids.size() > 1) {
            throw new IllegalArgumentException("Mỗi lần tạo chỉ hỗ trợ ĐÚNG 1 Bảng lưới — tách riêng nếu cần nhiều bảng.");
        }
        Element grid = grids.first();
        List<String> options = splitOptions(grid.attr("data-options"));
        if (options.size() < 2) {
            throw new IllegalArgumentException("Bảng lưới cần ít nhất 2 cột đáp án.");
        }
        List<ParsedSubQuestion> questions = new ArrayList<>();
        int rowNumber = 0;
        for (Element row : grid.select(".q-grid-row")) {
            rowNumber++;
            String text = row.text().trim();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Dòng " + rowNumber + " của Bảng lưới chưa có nội dung câu hỏi.");
            }
            int correctIndex = parseIndex(row.attr("data-correct-index"));
            if (correctIndex < 0 || correctIndex >= options.size()) {
                throw new IllegalArgumentException("Dòng " + rowNumber + " của Bảng lưới chưa chọn đáp án đúng.");
            }
            questions.add(new ParsedSubQuestion(text, toOptions(options, correctIndex)));
        }
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Bảng lưới cần ít nhất 1 dòng câu hỏi.");
        }
        grid.remove();
        return new ParsedGroup("grid", doc.body().html().trim(), audioUrl, questions);
    }

    private ParsedGroup parseCloze(Document doc, Elements inlineChoices, String audioUrl) {
        List<ParsedSubQuestion> questions = new ArrayList<>();
        int number = 0;
        for (Element el : inlineChoices) {
            number++;
            List<String> options = splitOptions(el.attr("data-options"));
            if (options.size() < 2) {
                throw new IllegalArgumentException("Lựa chọn trong câu số " + number + " cần ít nhất 2 lựa chọn.");
            }
            int correctIndex = parseIndex(el.attr("data-correct-index"));
            if (correctIndex < 0 || correctIndex >= options.size()) {
                throw new IllegalArgumentException("Lựa chọn trong câu số " + number + " chưa chọn đáp án đúng.");
            }
            questions.add(new ParsedSubQuestion("(" + number + ")", toOptions(options, correctIndex)));
            el.replaceWith(new TextNode("(" + number + ") ___"));
        }
        return new ParsedGroup("cloze", doc.body().html().trim(), audioUrl, questions);
    }

    private List<ParsedOption> toOptions(List<String> options, int correctIndex) {
        List<ParsedOption> result = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            result.add(new ParsedOption(options.get(i), i == correctIndex));
        }
        return result;
    }

    private List<String> splitOptions(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) return result;
        for (String part : raw.split(OPTION_SEPARATOR)) {
            if (!part.isBlank()) result.add(part.trim());
        }
        return result;
    }

    private int parseIndex(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
