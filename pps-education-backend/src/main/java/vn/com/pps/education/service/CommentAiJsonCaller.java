package vn.com.pps.education.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lệnh gọi AI dùng chung cho trợ lý nhận xét (UC-74 soạn nháp, UC-75 soát/đề xuất sửa — bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng 2026-09-28/29): nạp prompt hệ thống + chèn rubric nhận xét vào chỗ
 * {@code {{RUBRIC}}}, gửi payload dạng JSON, và chỉ nhận kết quả là 1 object JSON trọn vẹn (bỏ kết quả dở dang
 * vì hết token hoặc không đúng định dạng — caller coi {@code null} là "AI lỗi").
 */
@Component
public class CommentAiJsonCaller {

    private static final Logger log = LoggerFactory.getLogger(CommentAiJsonCaller.class);

    /** Rubric nhận xét do học vụ tự làm giàu — chèn vào chỗ {{RUBRIC}} của mọi prompt trợ lý nhận xét. */
    static final String RUBRIC_FILE = "comment-ai-draft-rubric.md";
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private final NineRouterAiClient aiClient;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper;

    public CommentAiJsonCaller(NineRouterAiClient aiClient, PromptTemplateLoader promptTemplateLoader, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.promptTemplateLoader = promptTemplateLoader;
        this.objectMapper = objectMapper;
    }

    /** @return object JSON AI trả về, hoặc {@code null} khi AI lỗi/kết quả dở dang/không phải JSON. */
    public JsonNode callJson(String promptFile, Object payload, String model) {
        String userMessage;
        try {
            userMessage = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("CommentAiJsonCaller: không dựng được payload JSON.", e);
        }
        String rubric = HTML_COMMENT.matcher(promptTemplateLoader.load(RUBRIC_FILE, Map.of())).replaceAll("").trim();
        String systemPrompt = promptTemplateLoader.load(promptFile, Map.of("RUBRIC", rubric));
        NineRouterAiClient.ChatResult result = aiClient.chatWithFinishReason(systemPrompt, userMessage, model);
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
}
