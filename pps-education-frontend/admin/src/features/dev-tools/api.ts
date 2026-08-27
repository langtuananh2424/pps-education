import { apiRequest } from "@/lib/apiClient";

/**
 * SPIKE/TEST riêng (2026-08-22, đã xác nhận với người dùng) — đánh giá khả thi kỹ thuật/chi phí
 * hướng "AI chấm Speaking" (dự kiến thay luồng chấm thủ công Video phản xạ). KHÔNG phải API chính
 * thức của 1 UC — xem Javadoc SpeakingAiGradingTestService.java (BE) để biết pipeline đầy đủ.
 */
export interface SpeakingAiGradingTestResponse {
  audioUrl: string;
  transcript: string;
  contentScorePercent: number | null;
  contentFeedback: string | null;
  grammarScorePercent: number | null;
  grammarFeedback: string | null;
}

/** Khớp SpeakingAiGradingTestService.Provider (BE) — AUTO tự chọn theo key nào đã cấu hình, ưu tiên OPENAI_CLAUDE. */
export type SpeakingAiGradingTestProvider = "AUTO" | "OPENAI_CLAUDE" | "GEMINI";

export function gradeSpeakingTest(
  audio: Blob,
  writingText: string,
  provider: SpeakingAiGradingTestProvider
): Promise<SpeakingAiGradingTestResponse> {
  const formData = new FormData();
  formData.append("audio", audio, "recording.webm");
  if (writingText.trim()) {
    formData.append("writingText", writingText.trim());
  }
  formData.append("provider", provider);
  return apiRequest<SpeakingAiGradingTestResponse>("/dev-tools/speaking-grading-test", { method: "POST", body: formData });
}
