package vn.com.pps.education.dto;

/**
 * SPIKE/TEST riêng (2026-08-22, đã xác nhận với người dùng) — response cho trang test độc lập
 * "AI chấm Speaking", KHÔNG phải DTO của 1 UC đã đặc tả. Xem Javadoc SpeakingAiGradingTestService.
 */
public record SpeakingAiGradingTestResponse(
        String audioUrl,
        String transcript,
        Integer contentScorePercent,
        String contentFeedback,
        Integer grammarScorePercent,
        String grammarFeedback
) {
}
