package vn.com.pps.education.lms.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-14 — đọc HTML do CKEditor5 soạn (khung
 * "Soạn đề" mới, thay thế dần Excel/Word cho các dạng không phẳng: điền từ giữa đoạn văn dài, điền
 * từ trong bảng, hộp từ vựng...) thành đúng dữ liệu {@code Question} hiện có — KHÔNG tạo loại dữ
 * liệu mới, tái dùng nguyên {@code QuestionType.WORD_BANK} + {@code structuredContent.blanks[]} đã
 * chạy thật từ trước (xem {@link vn.com.pps.education.lms.domain.Question#structuredContent}).
 *
 * Khác hẳn {@link ExcelQuestionRowParser}/{@link WordQuestionRowParser} ở chỗ: input là 1 khối HTML
 * GIÀU ĐỊNH DẠNG (bảng, chữ đậm, ảnh, nhiều ô trống nhúng giữa chữ ở vị trí bất kỳ) thay vì bảng
 * tính phẳng theo hàng/cột — nên {@code content} lưu NGUYÊN HTML (đã sanitize), KHÔNG rút gọn về
 * text thuần như Excel/Word (đã xác nhận với người dùng — tránh mất bảng/ảnh/layout tự do, vốn là
 * lý do chính đổi sang CKEditor5). Hệ quả: Portal học sinh cần thêm tầng hiển thị HTML giàu định
 * dạng riêng (không tái dùng được renderer text-thuần-tách-"___" hiện có của WORD_BANK) — xem việc
 * kế tiếp ở FE.
 *
 * "Ô trống" (không hộp từ) và "Hộp từ vựng" dùng CHUNG parser này — cả 2 đều ra đúng 1
 * {@code QuestionType.WORD_BANK} với {@code structuredContent.blanks[]}, không phân biệt ở tầng dữ
 * liệu (chỉ khác nhau ở có hiển thị thêm khối "Hộp từ vựng" trong HTML hay không — thuần trình bày).
 */
@Service
public class CkeditorWordBankParser {

    private final CkeditorHtmlSanitizer sanitizer;

    public CkeditorWordBankParser(CkeditorHtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    /** {@code audioUrl}: trích từ widget "Audio" nếu có (đã BÓC khỏi {@code sanitizedHtml}) — xem Javadoc {@link CkeditorHtmlSanitizer#extractAndRemoveAudioSrc}. */
    public record ParsedWordBank(String sanitizedHtml, List<String> blanks, String audioUrl) {
    }

    /**
     * Sanitize HTML + lấy danh sách đáp án đúng THEO ĐÚNG THỨ TỰ TÀI LIỆU (không phải theo
     * {@code data-blank-id}) — mirror chính xác cơ chế đã xác nhận với người dùng ở PoC
     * (blankParser.ts#extractBlanksInDocumentOrder phía FE), giờ làm lại ở backend làm nguồn sự
     * thật thật sự (FE PoC chỉ là demo phía trình duyệt).
     */
    public ParsedWordBank parse(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            throw new IllegalArgumentException("Nội dung soạn đề trống.");
        }

        String sanitized = sanitizer.sanitize(rawHtml);

        Document doc = Jsoup.parseBodyFragment(sanitized);
        String audioUrl = sanitizer.extractAndRemoveAudioSrc(doc);
        List<String> blanks = new ArrayList<>();
        for (Element blankEl : doc.select(".q-blank")) {
            blanks.add(blankEl.attr("data-answer"));
        }

        if (blanks.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy ô trống nào trong nội dung — cần ít nhất 1 ô trống (bấm \"Chèn ô trống\" khi soạn).");
        }

        return new ParsedWordBank(doc.body().html(), blanks, audioUrl);
    }
}
