package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — tách system prompt của các service
 * chấm AI ra file `.txt` riêng dưới {@code resources/prompts/} thay vì nối chuỗi trong code Java, để dễ
 * đọc/sửa nội dung prompt (không cần đụng vào logic Java). Placeholder dạng {@code {{TEN_BIEN}}}, thay
 * bằng {@link String#replace} — không dùng template engine đầy đủ vì chỉ cần thay thế đơn giản.
 */
@Component
public class PromptTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateLoader.class);

    /** Cache theo tên file — chỉ đọc mỗi file 1 lần trong suốt vòng đời ứng dụng (nội dung tĩnh, không đổi lúc chạy). */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * @param fileName tên file dưới {@code resources/prompts/} (VD "reflex-speaking-grading-system-prompt.txt").
     * @param placeholders map {@code {{TEN_BIEN}} -> giá trị thay thế} (không cần bọc sẵn {{ }} trong key).
     * @throws IllegalStateException nếu không tìm thấy file — lỗi cấu hình/đóng gói, không phải input
     *         người dùng, nên throw thẳng thay vì trả null (khác rubric — rubric thiếu là chuyện nghiệp
     *         vụ bình thường, thiếu prompt template là lỗi build/deploy).
     */
    public String load(String fileName, Map<String, String> placeholders) {
        String template = cache.computeIfAbsent(fileName, this::readClasspathFile);
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String readClasspathFile(String fileName) {
        String classpath = "prompts/" + fileName;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalStateException("PromptTemplateLoader: không tìm thấy " + classpath + " trên classpath.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("PromptTemplateLoader: đọc " + classpath + " thất bại.", e);
        }
    }
}
