package vn.com.pps.education.lms.dto;

import vn.com.pps.education.common.CriteriaScoreItem;

import java.util.List;

/**
 * SPIKE/TEST riêng (2026-08-22, đã xác nhận với người dùng) — response cho trang test độc lập
 * "AI chấm Speaking", KHÔNG phải DTO của 1 UC đã đặc tả. Xem Javadoc SpeakingAiGradingTestService.
 *
 * 4 field pronunciation* (bổ sung 2026-10-21, đã xác nhận với người dùng, chỉ có giá trị khi
 * provider=AZURE_STACK) — điểm thô thang 100 do Azure Speech Pronunciation Assessment trả về,
 * KHÔNG quy đổi sang band IELTS (xem Javadoc SpeakingAiGradingTestService#callAzurePronunciation).
 * pronunciationProsodyScore có thể null vì Prosody hiện chỉ hỗ trợ locale en-US và là tính năng còn
 * mới của Azure, không phải mọi region/giọng đều trả về được.
 */
public record SpeakingAiGradingTestResponse(
        String audioUrl,
        String transcript,
        Integer contentScorePercent,
        String contentFeedback,
        Integer grammarScorePercent,
        String grammarFeedback,
        Integer pronunciationAccuracyScore,
        Integer pronunciationFluencyScore,
        Integer pronunciationCompletenessScore,
        Integer pronunciationProsodyScore,
        Integer pronunciationOverallScore,
        String pronunciationRawResponse,
        Double audioSeconds,
        Long costGroqVnd,
        Long costClaudeVnd,
        Long costAzureVnd,
        Long costTotalVnd,
        List<CriteriaScoreItem> reflexCriteria,
        Integer reflexUnlockPercent,
        Integer reflexFinalPercent,
        String reflexFeedback,
        String reflexHint,
        String reflexGateNote,
        String reflexMarkedTranscript,
        String reflexFabricationWarning,
        String reflexErrorMessage,
        List<PronunciationWordDetail> pronunciationWords,
        String groqLowConfidenceWarning
) {
}
