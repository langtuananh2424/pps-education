import { apiRequest } from "@/lib/apiClient";
import { getAccessToken } from "@/lib/tokenStorage";

/** Khớp AiTokenUsageSummaryResponse thật của backend (V192) — xem AiTokenUsageController. */
export interface AiTokenUsageTotals {
  callCount: number;
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
  reasoningTokens: number;
  /** Lượt bị loại kết quả (9Router trả sai model / nội dung rỗng) nhưng VẪN tốn tiền. */
  rejectedCalls: number;
  /** Lượt không gắn được học sinh — giải thích vì sao tổng theo học sinh nhỏ hơn tổng chung. */
  unattributedCalls: number;
}

export interface AiTokenUsageByStep {
  step: string;
  callCount: number;
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
  reasoningTokens: number;
  avgElapsedMs: number;
}

export interface AiTokenUsageByStudent {
  studentId: number;
  studentName: string;
  callCount: number;
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
  reasoningTokens: number;
}

export interface AiTokenUsageByAssignment {
  assignmentId: number;
  assignmentName: string;
  callCount: number;
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
  reasoningTokens: number;
}

export interface AiTokenUsageSummary {
  totals: AiTokenUsageTotals;
  byStep: AiTokenUsageByStep[];
  byStudent: AiTokenUsageByStudent[];
  byAssignment: AiTokenUsageByAssignment[];
}

export function getAiTokenUsageSummary(from: string, to: string): Promise<AiTokenUsageSummary> {
  return apiRequest<AiTokenUsageSummary>(`/ai-token-usage/summary?from=${from}&to=${to}`);
}

/** 1 lượt chấm vừa xong, đẩy qua SSE — xem AiTokenUsageStream.publish phía backend. */
export interface AiTokenUsageEvent {
  step: string;
  studentName: string | null;
  servedModel: string;
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
  reasoningTokens: number;
  elapsedMs: number;
  at: string;
}

/**
 * Nối kênh SSE để trang tự cập nhật khi có lượt chấm mới.
 *
 * Dùng fetch + ReadableStream chứ KHÔNG dùng EventSource: EventSource không cho đặt header nên sẽ phải
 * nhét access token vào query string, tức token lọt vào access log của nginx và lịch sử trình duyệt.
 * fetch giữ được header Authorization như mọi request khác.
 *
 * @returns hàm huỷ đăng ký — gọi khi rời trang để đóng kết nối.
 */
export function subscribeAiTokenUsage(onEvent: (event: AiTokenUsageEvent) => void): () => void {
  const controller = new AbortController();
  let stopped = false;

  async function connect(): Promise<void> {
    try {
      const response = await fetch("/api/ai-token-usage/stream", {
        headers: { Authorization: `Bearer ${getAccessToken() ?? ""}`, Accept: "text/event-stream" },
        signal: controller.signal
      });
      if (!response.ok || !response.body) return;

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      for (;;) {
        const { done, value } = await reader.read();
        if (done || stopped) break;
        buffer += decoder.decode(value, { stream: true });
        // Khung SSE kết thúc bằng 1 dòng trống; phần dư giữ lại chờ chunk sau vì 1 sự kiện có thể bị
        // cắt đôi giữa 2 lần đọc.
        const frames = buffer.split("\n\n");
        buffer = frames.pop() ?? "";
        for (const frame of frames) {
          const dataLine = frame.split("\n").find((line) => line.startsWith("data:"));
          if (!dataLine) continue;
          try {
            onEvent(JSON.parse(dataLine.slice(5).trim()) as AiTokenUsageEvent);
          } catch {
            // Khung dị dạng thì bỏ qua — số liệu đầy đủ vẫn lấy được qua /summary khi tải lại trang.
          }
        }
      }
    } catch {
      // Mất mạng/backend restart: im lặng, không làm vỡ trang. Người dùng tải lại là có số liệu đầy đủ.
    }
  }

  void connect();
  return () => {
    stopped = true;
    controller.abort();
  };
}
