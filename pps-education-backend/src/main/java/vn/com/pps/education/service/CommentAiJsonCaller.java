package vn.com.pps.education.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lệnh gọi AI dùng chung cho trợ lý nhận xét (UC-74 soạn nháp, UC-75 soát/đề xuất sửa — bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng 2026-09-28/29): nạp prompt hệ thống + chèn rubric nhận xét vào chỗ
 * {@code {{RUBRIC}}}, gửi payload dạng JSON, và chỉ nhận kết quả là 1 object JSON trọn vẹn (bỏ kết quả dở dang
 * vì hết token hoặc không đúng định dạng — caller coi {@code null} là "AI lỗi").
 *
 * <p>Bổ sung 2026-10-01 (đã xác nhận với người dùng): rubric đã dài ~20KB và đi kèm MỌI lệnh gọi, nên mỗi prompt chỉ
 * nhận đúng các mục rubric nó cần (theo tiêu đề {@code ## N.}, xem {@link #selectRubricSections}); caller tự khai báo
 * tập mục và nhiệt độ (chỉ bước viết câu nhận xét dùng nhiệt độ khác 0).</p>
 */
@Component
public class CommentAiJsonCaller {

    private static final Logger log = LoggerFactory.getLogger(CommentAiJsonCaller.class);

    /** Rubric nhận xét do học vụ tự làm giàu — chèn vào chỗ {{RUBRIC}} của mọi prompt trợ lý nhận xét. */
    static final String RUBRIC_FILE = "comment-ai-draft-rubric.md";
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern SECTION_HEADING = Pattern.compile("(?m)^## (\\d+)\\.");
    /** Truyền vào {@code rubricSections} khi prompt cần cả rubric. */
    public static final Set<Integer> ALL_RUBRIC_SECTIONS = Set.of();

    private final NineRouterAiClient aiClient;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper;

    public CommentAiJsonCaller(NineRouterAiClient aiClient, PromptTemplateLoader promptTemplateLoader, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.promptTemplateLoader = promptTemplateLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * @param rubricSections số các mục rubric ({@code ## N.}) chèn vào prompt; {@link #ALL_RUBRIC_SECTIONS} = cả rubric.
     * @param temperature    0 cho các bước cần ổn định (tách ý, soát, sửa tối thiểu); &gt; 0 chỉ cho bước viết câu.
     * @return object JSON AI trả về, hoặc {@code null} khi AI lỗi/kết quả dở dang/không phải JSON.
     */
    public JsonNode callJson(String promptFile, Object payload, String model, Set<Integer> rubricSections, double temperature) {
        String userMessage;
        try {
            userMessage = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("CommentAiJsonCaller: không dựng được payload JSON.", e);
        }
        String rubric = HTML_COMMENT.matcher(promptTemplateLoader.load(RUBRIC_FILE, Map.of())).replaceAll("").trim();
        String systemPrompt = promptTemplateLoader.load(promptFile, Map.of("RUBRIC", selectRubricSections(rubric, rubricSections)));
        NineRouterAiClient.ChatResult result = aiClient.chatWithFinishReason(systemPrompt, userMessage, model, temperature);
        if (result == null || result.content() == null) {
            return null;
        }
        if (result.finishReason() != null && !"stop".equals(result.finishReason())) {
            log.warn("CommentAiJsonCaller: kết quả AI dở dang (finish_reason={}) — bỏ, không dùng nửa chừng.", result.finishReason());
            return null;
        }
        String text = result.content();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("CommentAiJsonCaller: AI không trả JSON ({} ký tự).", text.length());
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            return node.isObject() ? node : null;
        } catch (JsonProcessingException e) {
            log.warn("CommentAiJsonCaller: JSON AI trả về không hợp lệ. {}", e.getOriginalMessage());
            return null;
        }
    }

    /**
     * Giữ phần mở đầu rubric (trước mục {@code ## 1.}) + đúng các mục được yêu cầu, giữ nguyên số mục để prompt dẫn
     * chiếu "rubric mục 4" vẫn khớp. Thiếu mục nào (VD học vụ đổi tiêu đề) thì trả CẢ rubric và ghi cảnh báo — thà tốn
     * token còn hơn âm thầm bỏ mất quy tắc.
     */
    static String selectRubricSections(String rubric, Set<Integer> sections) {
        if (sections == null || sections.isEmpty()) {
            return rubric;
        }
        Matcher matcher = SECTION_HEADING.matcher(rubric);
        List<int[]> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(new int[]{Integer.parseInt(matcher.group(1)), matcher.start()});
        }
        StringBuilder selected = new StringBuilder(rubric.substring(0, starts.isEmpty() ? rubric.length() : starts.get(0)[1]).trim());
        int found = 0;
        for (int i = 0; i < starts.size(); i++) {
            if (sections.contains(starts.get(i)[0])) {
                int end = i + 1 < starts.size() ? starts.get(i + 1)[1] : rubric.length();
                selected.append("\n\n").append(rubric, starts.get(i)[1], end);
                found++;
            }
        }
        if (found < sections.size()) {
            log.warn("CommentAiJsonCaller: rubric thiếu mục {} — gửi cả rubric.", sections);
            return rubric;
        }
        return selected.toString().trim();
    }
}
