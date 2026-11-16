package vn.com.pps.education.lms.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — đọc HTML do CKEditor5 soạn khi GV
 * dùng widget "Khối sắp xếp" thành {@code QuestionType.SENTENCE_BUILDING} +
 * {@code structuredContent.chunks[]} (thứ tự ĐÚNG, dùng chấm điểm) +
 * {@code structuredContent.chunkOrder[]} (thứ tự HIỂN THỊ cho học sinh — LUÔN giữ nguyên thứ tự GV
 * gõ, không random) — cơ chế 2 field này đã có sẵn từ trước (V96, dùng cho Excel import), KHÔNG đổi
 * gì ở tầng chấm điểm/hiển thị Học viên (ExerciseAttemptService/TakeExerciseModal), chỉ bổ sung
 * đường parse MỚI.
 *
 * Mirror {@link CkeditorChoiceParser} ở chỗ: widget "Khối sắp xếp" BỊ BÓC khỏi {@code content} —
 * TakeExerciseModal#SentenceBuildingBlock hiện luôn vẽ RIÊNG 1 "hộp khối" (word bank) cho học sinh
 * ghép câu từ đầu, không phải chèn trực tiếp các khối NGAY GIỮA đoạn văn tại đúng vị trí đã gõ —
 * giữ nguyên HTML trong content sẽ khiến các khối hiện SAI chỗ/LẶP so với UI chuẩn đã có.
 *
 * V1 — CHỈ hỗ trợ ĐÚNG 1 nhóm khối (cùng nằm trong 1 khối cha — thẻ p/li/td/th/div gần nhất) mỗi
 * tài liệu, mirror ràng buộc "1 Question = 1 nhóm" đã áp cho Nhóm lựa chọn.
 */
@Service
public class CkeditorChunkParser {

    private static final Set<String> BLOCK_TAGS = Set.of("p", "li", "td", "th", "div");

    private final CkeditorHtmlSanitizer sanitizer;

    public CkeditorChunkParser(CkeditorHtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    /** {@code stemHtml}: HTML còn lại SAU KHI bóc nhóm khối ra — lưu vào {@code Question.content} (hướng dẫn/đoạn văn xung quanh, nếu có). */
    public record ParsedChunk(String stemHtml, List<String> correctOrder, List<String> displayOrder, String audioUrl) {
    }

    public ParsedChunk parse(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            throw new IllegalArgumentException("Nội dung soạn đề trống.");
        }

        String sanitized = sanitizer.sanitize(rawHtml);
        Document doc = Jsoup.parseBodyFragment(sanitized);
        String audioUrl = sanitizer.extractAndRemoveAudioSrc(doc);

        Elements chunkEls = doc.select(".q-chunk");
        if (chunkEls.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy khối sắp xếp nào trong nội dung — bôi đen 1 từ/cụm từ rồi bấm \"Đánh dấu khối sắp xếp\" khi soạn.");
        }

        // Gom các khối LIỀN KỀ cùng 1 khối cha (p/li/td/th/div gần nhất) thành 1 nhóm — mirror
        // blankParser.ts#extractChunkGroupsInDocumentOrder (FE PoC).
        List<List<Element>> groups = new ArrayList<>();
        Element currentParent = null;
        List<Element> currentGroup = new ArrayList<>();
        for (Element el : chunkEls) {
            Element parent = closestBlockAncestor(el);
            if (parent != currentParent) {
                if (currentGroup.size() >= 2) groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentParent = parent;
            }
            currentGroup.add(el);
        }
        if (currentGroup.size() >= 2) groups.add(currentGroup);

        if (groups.isEmpty()) {
            throw new IllegalArgumentException("Nhóm khối sắp xếp cần ít nhất 2 khối liền kề nhau.");
        }
        if (groups.size() > 1) {
            throw new IllegalArgumentException("Mỗi câu hỏi chỉ hỗ trợ ĐÚNG 1 nhóm khối sắp xếp — tách thành nhiều câu riêng nếu cần nhiều nhóm.");
        }
        List<Element> group = groups.get(0);

        List<String> displayOrder = new ArrayList<>();
        List<ChunkEntry> entries = new ArrayList<>();
        for (Element el : group) {
            String text = el.text().trim();
            if (text.isEmpty()) continue;
            Integer order = el.hasAttr("data-order") ? parseIntOrNull(el.attr("data-order")) : null;
            displayOrder.add(text);
            entries.add(new ChunkEntry(text, order));
        }

        boolean hasCustomOrder = entries.stream().anyMatch(e -> e.order != null);
        List<String> correctOrder;
        if (hasCustomOrder) {
            correctOrder = entries.stream()
                    .sorted(Comparator.comparingInt(e -> e.order == null ? Integer.MAX_VALUE : e.order))
                    .map(e -> e.text)
                    .toList();
        } else {
            correctOrder = displayOrder;
        }

        // Bóc nguyên khối cha chứa nhóm (cùng nhóm q-chunk) ra khỏi content — còn lại là hướng dẫn/
        // đoạn văn xung quanh (stem) — mirror CkeditorChoiceParser.
        Element container = closestBlockAncestor(group.get(0));
        if (container != null) container.remove();
        String stemHtml = doc.body().html().trim();

        return new ParsedChunk(stemHtml, correctOrder, displayOrder, audioUrl);
    }

    private record ChunkEntry(String text, Integer order) {
    }

    private Integer parseIntOrNull(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Element closestBlockAncestor(Element el) {
        Element current = el.parent();
        while (current != null) {
            if (BLOCK_TAGS.contains(current.tagName())) return current;
            current = current.parent();
        }
        return null;
    }
}
