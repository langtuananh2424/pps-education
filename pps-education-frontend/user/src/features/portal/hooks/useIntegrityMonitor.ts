import { useEffect, useRef, useState } from "react";
import type { IntegrityEventInput, IntegrityEventType } from "../api";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — giám sát
 * học sinh thoát ra ngoài (đổi tab/thu nhỏ/thoát fullscreen) khi đang làm
 * bài. KHÔNG khóa được màn hình thật (không có API trình duyệt nào cho
 * phép) — đây là "ép toàn màn hình best-effort + phát hiện thoát ra".
 *
 * QUAN TRỌNG (đã xác nhận với người dùng — học sinh chủ yếu làm bài trên
 * mobile web): iOS Safari KHÔNG hỗ trợ Fullscreen API cho phần tử DOM
 * thường (giới hạn WebKit lâu năm) — bước ép fullscreen tự bỏ qua êm khi
 * không hỗ trợ (`document.fullscreenEnabled === false`), KHÔNG chặn/báo
 * lỗi luồng làm bài. Page Visibility API (`visibilitychange`) +
 * `window blur/focus` mới là tín hiệu chính, hoạt động ổn định trên cả
 * iOS/Android/desktop.
 *
 * 2 chế độ dùng:
 * - Tự động gửi định kỳ: truyền autoFlushIntervalMs + onFlush (dùng cho
 *   TakeExerciseModal — có attemptId tồn tại sẵn để gửi real-time).
 * - Đệm rồi gửi tay: không truyền autoFlushIntervalMs, tự gọi flush()
 *   ngay trước khi nộp (dùng cho ReviewVideoTaskModal — UC-23b không có
 *   "phiên bắt đầu ghi âm" ở backend nên không gửi được real-time, xem
 *   Javadoc ReviewVideoQuestionIntegrityContextResolver ở backend).
 */
export interface UseIntegrityMonitorOptions {
  enabled: boolean;
  /** Lọc nhiễu phía client trước khi đưa vào hàng đợi (VD popup xin quyền, thoáng 1 nhịp) — server áp ngưỡng thật lại lần nữa. */
  minViolationDurationMs?: number;
  /** Có truyền thì hook tự gửi theo lô định kỳ; không truyền thì chỉ đệm, gọi flush() thủ công. */
  autoFlushIntervalMs?: number;
  onFlush?: (events: IntegrityEventInput[]) => void;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 — gọi ngay sau khi ghi nhận
   * FULLSCREEN_EXITED, để nơi gọi (VD ReflexVideoTaskPage) tự yêu cầu lại fullscreen ngay lập tức
   * (auto re-enter, best-effort). KHÔNG thể chặn thật việc thoát fullscreen — trình duyệt luôn cho
   * phép người dùng nhấn Esc, đây chỉ giảm ma sát chứ không khóa cứng (xem ghi chú đầu file).
   */
  onFullscreenExit?: () => void;
}

export interface UseIntegrityMonitorResult {
  violationCount: number;
  isMonitoringActive: boolean;
  /** True trong ít giây ngay sau khi vừa phát hiện 1 lần vi phạm mới — dùng để bật popup cảnh báo tức
   * thời cho học sinh (khác banner tĩnh vốn chỉ hiện số đếm, dễ bị bỏ qua). Tự tắt lại sau JUST_VIOLATED_MS. */
  justViolated: boolean;
  /** Lấy + xóa hàng đợi hiện có, đồng thời gọi onFlush (nếu có) — dùng ở chế độ "đệm rồi gửi tay". */
  flush: () => IntegrityEventInput[];
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-17 — gọi NGAY TRƯỚC KHI mở dialog chọn
   * file hệ điều hành (input[type=file], câu ESSAY/SPEAKING nộp audio). Mở dialog này luôn bắn
   * `window blur` y hệt đổi tab thật (trình duyệt không phân biệt được 2 trường hợp), nếu không có
   * "vùng ân hạn" thì học sinh chọn file hơi lâu 1 chút cũng bị tính nhầm vi phạm. KHÔNG tắt hẳn giám
   * sát — chỉ bỏ qua ĐÚNG 1 lần blur→focus kế tiếp trong FILE_PICKER_GRACE_MS, dùng 1 lần rồi tự xoá
   * (không rò rỉ sang lần đổi tab thật sau đó); nếu học sinh lợi dụng nút này để rời tab lâu hơn
   * ngưỡng, hết hạn ân hạn vẫn bị tính vi phạm như bình thường.
   */
  suppressForFilePicker: () => void;
}

const DEFAULT_MIN_VIOLATION_DURATION_MS = 1000;
const JUST_VIOLATED_MS = 3500;
/** Đủ thời gian học sinh duyệt thư mục chọn file audio đã ghi âm sẵn, không quá dài để tránh bị lợi dụng làm "vé thoát tab" miễn phí. */
const FILE_PICKER_GRACE_MS = 90_000;

export function useIntegrityMonitor(options: UseIntegrityMonitorOptions): UseIntegrityMonitorResult {
  const { enabled, minViolationDurationMs = DEFAULT_MIN_VIOLATION_DURATION_MS, autoFlushIntervalMs, onFlush, onFullscreenExit } = options;
  const [violationCount, setViolationCount] = useState(0);
  const [isMonitoringActive, setIsMonitoringActive] = useState(false);
  const [justViolated, setJustViolated] = useState(false);

  const pendingEventsRef = useRef<IntegrityEventInput[]>([]);
  const outOfFocusSinceRef = useRef<number | null>(null);
  const filePickerGraceUntilRef = useRef<number | null>(null);
  const everEnteredFullscreenRef = useRef(false);
  const justViolatedTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const onFlushRef = useRef(onFlush);
  onFlushRef.current = onFlush;
  const onFullscreenExitRef = useRef(onFullscreenExit);
  onFullscreenExitRef.current = onFullscreenExit;

  const suppressForFilePicker = () => {
    filePickerGraceUntilRef.current = Date.now() + FILE_PICKER_GRACE_MS;
  };

  const flush = (): IntegrityEventInput[] => {
    const events = pendingEventsRef.current;
    pendingEventsRef.current = [];
    if (events.length > 0) {
      onFlushRef.current?.(events);
    }
    return events;
  };

  const queueEvent = (eventType: IntegrityEventType, startedAtMs: number, endedAtMs: number) => {
    if (endedAtMs - startedAtMs < minViolationDurationMs) {
      return;
    }
    pendingEventsRef.current = [
      ...pendingEventsRef.current,
      {
        eventType,
        startedAt: new Date(startedAtMs).toISOString(),
        endedAt: new Date(endedAtMs).toISOString(),
        userAgent: navigator.userAgent
      }
    ];
    setViolationCount((c) => c + 1);

    setJustViolated(true);
    if (justViolatedTimeoutRef.current) clearTimeout(justViolatedTimeoutRef.current);
    justViolatedTimeoutRef.current = setTimeout(() => setJustViolated(false), JUST_VIOLATED_MS);
  };

  useEffect(() => {
    if (!enabled) return;
    setIsMonitoringActive(true);

    // Best-effort — iOS Safari không hỗ trợ (fullscreenEnabled=false), tự bỏ qua êm, không throw ra ngoài.
    if (document.fullscreenEnabled) {
      document.documentElement.requestFullscreen().then(
        () => {
          everEnteredFullscreenRef.current = true;
        },
        () => undefined
      );
    }

    const markOutOfFocusStart = () => {
      if (outOfFocusSinceRef.current == null) {
        outOfFocusSinceRef.current = Date.now();
      }
    };
    const markBackInFocus = () => {
      if (outOfFocusSinceRef.current != null && document.visibilityState === "visible" && document.hasFocus()) {
        const withinFilePickerGrace = filePickerGraceUntilRef.current != null && Date.now() <= filePickerGraceUntilRef.current;
        filePickerGraceUntilRef.current = null; // dùng 1 lần — không rò rỉ sang lần blur không liên quan sau đó
        if (!withinFilePickerGrace) {
          queueEvent("OUT_OF_FOCUS", outOfFocusSinceRef.current, Date.now());
        }
        outOfFocusSinceRef.current = null;
      }
    };

    const handleVisibilityChange = () => {
      if (document.hidden) markOutOfFocusStart();
      else markBackInFocus();
    };
    const handleBlur = () => markOutOfFocusStart();
    const handleFocus = () => markBackInFocus();
    const handleFullscreenChange = () => {
      // Chỉ có ý nghĩa nếu trước đó THỰC SỰ đã vào được fullscreen (tránh bắn sai trên iOS chưa từng vào).
      if (everEnteredFullscreenRef.current && !document.fullscreenElement && document.visibilityState === "visible") {
        queueEvent("FULLSCREEN_EXITED", Date.now(), Date.now() + minViolationDurationMs);
        onFullscreenExitRef.current?.();
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    window.addEventListener("blur", handleBlur);
    window.addEventListener("focus", handleFocus);
    document.addEventListener("fullscreenchange", handleFullscreenChange);

    let intervalId: ReturnType<typeof setInterval> | undefined;
    if (autoFlushIntervalMs) {
      intervalId = setInterval(flush, autoFlushIntervalMs);
    }

    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      window.removeEventListener("blur", handleBlur);
      window.removeEventListener("focus", handleFocus);
      document.removeEventListener("fullscreenchange", handleFullscreenChange);
      if (intervalId) clearInterval(intervalId);
      if (justViolatedTimeoutRef.current) clearTimeout(justViolatedTimeoutRef.current);
      flush();
      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => undefined);
      }
      setIsMonitoringActive(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);

  return { violationCount, isMonitoringActive, justViolated, flush, suppressForFilePicker };
}
