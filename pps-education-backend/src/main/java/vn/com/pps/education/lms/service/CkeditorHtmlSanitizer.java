package vn.com.pps.education.lms.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Tách ra từ {@link CkeditorWordBankParser} — dùng chung cho MỌI parser đọc HTML do CKEditor5 soạn
 * (Ô trống/Hộp từ vựng, Nhóm lựa chọn...), tránh định nghĩa Safelist lệch nhau giữa các widget khi
 * thêm loại mới. Danh sách thẻ/attribute đủ cho mọi widget đã xác nhận với người dùng (Ô trống, Hộp
 * từ vựng, Nhóm lựa chọn, Bảng, Audio, định dạng chữ cơ bản, ảnh inline trong lựa chọn). KHÔNG cho
 * script/iframe/event handler (onclick...) — chặn XSS trước khi lưu vào DB.
 */
@Component
public class CkeditorHtmlSanitizer {

    private static Safelist buildSafelist() {
        return new Safelist()
                .addTags("p", "br", "strong", "b", "em", "i", "u", "s", "sub", "sup", "h1", "h2", "h3", "h4",
                        "blockquote", "hr", "ul", "ol", "li", "table", "thead", "tbody", "tr", "td", "th",
                        "span", "div", "img", "audio", "a", "label")
                .addAttributes(":all", "class", "style")
                .addAttributes("span", "data-blank-id", "data-answer", "data-group-id", "data-option-id",
                        "data-correct", "data-chunk-id", "data-order", "data-choice-id", "data-options",
                        "data-correct-index")
                .addAttributes("div", "data-group-id", "data-options", "data-correct-index", "data-true-false")
                .addAttributes("label", "data-option-id", "data-correct")
                .addAttributes("img", "src", "alt")
                .addAttributes("audio", "src", "controls")
                .addAttributes("a", "href")
                .addProtocols("img", "src", "http", "https")
                .addProtocols("audio", "src", "http", "https")
                .addProtocols("a", "href", "http", "https");
    }

    public String sanitize(String rawHtml) {
        return Jsoup.clean(rawHtml, buildSafelist());
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — widget "Audio" (phục vụ các
     * loại Nghe) dùng CHUNG cho mọi parser CKEditor5 (Ô trống/Nhóm lựa chọn/Khối sắp xếp): trích
     * {@code src} rồi BÓC khỏi {@code doc} (mirror cách bóc "Nhóm lựa chọn"/"Khối sắp xếp" — URL
     * audio đã có field riêng {@code Question.audioUrl}, giữ nguyên thẻ trong content sẽ phát lặp
     * 2 trình phát). V1 — chỉ nhận ĐÚNG 1 audio/câu (khớp field audioUrl đơn, không phải mảng).
     */
    public String extractAndRemoveAudioSrc(Document doc) {
        Element audio = doc.selectFirst("audio");
        if (audio == null) return null;
        String src = audio.attr("src");
        audio.remove();
        return src.isBlank() ? null : src;
    }
}
