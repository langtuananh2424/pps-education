package vn.com.pps.education.common;

/**
 * V192 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — mức tiêu thụ token của ĐÚNG 1
 * lệnh gọi AI, do {@link vn.com.pps.education.service.NineRouterAiClient} đọc từ field {@code usage} của
 * response và trả ngược lên cho service gọi.
 *
 * Truyền TƯỜNG MINH qua kiểu trả về (không dùng ThreadLocal/biến tĩnh) vì service gọi mới là nơi biết
 * ngữ cảnh nghiệp vụ (học sinh nào, câu hỏi nào, đang ở bước chấm nào) — client tầng dưới cố tình không
 * biết gì về học sinh, đúng hướng phụ thuộc 1 chiều ở .claude/rules/architecture.md.
 *
 * @param servedModel      model 9Router THỰC SỰ route tới (có thể khác model đã yêu cầu).
 * @param audioAttached    lệnh gọi có đính kèm audio hay không — audio chiếm phần lớn promptTokens.
 * @param cachedTokens     phần promptTokens được tính giá cache (0 = không có bằng chứng cache hit).
 * @param reasoningTokens  token thinking; tính tiền như output nhưng ở 1 số provider KHÔNG nằm trong
 *                         completionTokens, nên phải cộng riêng khi ước tính chi phí.
 * @param elapsedMs        thời gian chờ thực tế, GỒM cả lần thử lại 429/503 và 2 giây ngủ giữa 2 lần —
 *                         đây là thời gian học sinh phải chờ, không phải độ trễ thuần của model.
 */
public record AiTokenUsage(String servedModel, boolean audioAttached, int promptTokens, int cachedTokens,
                           int completionTokens, int reasoningTokens, long elapsedMs) {

    /** Response không có field usage (1 số provider/route STT không trả) — vẫn giữ được model + latency. */
    public static AiTokenUsage unknown(String servedModel, boolean audioAttached, long elapsedMs) {
        return new AiTokenUsage(servedModel, audioAttached, 0, 0, 0, 0, elapsedMs);
    }
}
