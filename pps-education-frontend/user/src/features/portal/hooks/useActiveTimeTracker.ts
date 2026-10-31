import { useEffect, useRef } from "react";

/**
 * Bổ sung ngoài SDD gốc, đã yêu cầu 2026-10-26 — đo NGẦM thời gian học sinh thực sự làm BTVN (không có UI nào
 * hiện số này cho học sinh; chỉ hiện ở trang "Thống kê BTVN" của admin, không dùng chấm điểm).
 *
 * Chỉ đếm khi tab đang hiện VÀ cửa sổ đang focus (cùng tín hiệu với useIntegrityMonitor) nên rời tab/khóa
 * màn hình/đóng app là tự dừng. Mỗi tick giới hạn tối đa MAX_TICK_MS để máy ngủ/treo rồi tỉnh dậy không bị
 * cộng một cú nhảy lớn. Gửi gộp mỗi FLUSH_INTERVAL_MS, và gửi nốt phần còn lại khi rời trang/đóng modal/ẩn tab.
 * Gửi lỗi thì giữ lại số giây chưa gửi để lần sau gửi bù (server vẫn tự chặn không cho cộng quá thời gian thực).
 *
 * `attemptIds` = các lượt đang làm dở được đo cùng lúc (Bài lẻ = 1 phần tử; Lô = N Bài mở cùng 1 màn hình,
 * thời gian được chia đều cho N lượt để tổng cả Lô khớp thời gian thực học sinh ngồi làm).
 */
const TICK_MS = 1000;
const MAX_TICK_MS = 2500;
const FLUSH_INTERVAL_MS = 15_000;

export function useActiveTimeTracker(
  attemptIds: number[],
  send: (attemptId: number, seconds: number) => Promise<unknown>,
  enabled: boolean
): void {
  const idsKey = attemptIds.join(",");
  const sendRef = useRef(send);
  sendRef.current = send;
  const idsRef = useRef(attemptIds);
  idsRef.current = attemptIds;

  useEffect(() => {
    if (!enabled || idsRef.current.length === 0) return;

    let pendingMs = 0;
    let lastTickAt = Date.now();

    const flush = () => {
      const ids = idsRef.current;
      const seconds = Math.floor(pendingMs / 1000);
      if (ids.length === 0 || seconds < 1) return;
      const perAttempt = Math.max(1, Math.round(seconds / ids.length));
      pendingMs -= seconds * 1000;
      ids.forEach((id) => {
        sendRef.current(id, perAttempt).catch(() => {
          pendingMs += (perAttempt * 1000) / ids.length; // gửi lỗi: trả lại phần của lượt này để gửi bù
        });
      });
    };

    const tick = () => {
      const now = Date.now();
      const delta = Math.min(now - lastTickAt, MAX_TICK_MS);
      lastTickAt = now;
      if (document.visibilityState === "visible" && document.hasFocus()) {
        pendingMs += delta;
      }
    };

    const tickId = setInterval(tick, TICK_MS);
    const flushId = setInterval(flush, FLUSH_INTERVAL_MS);
    const handleVisibility = () => {
      tick();
      if (document.visibilityState === "hidden") flush();
    };
    document.addEventListener("visibilitychange", handleVisibility);
    window.addEventListener("pagehide", flush);

    return () => {
      clearInterval(tickId);
      clearInterval(flushId);
      document.removeEventListener("visibilitychange", handleVisibility);
      window.removeEventListener("pagehide", flush);
      tick();
      flush();
    };
  }, [enabled, idsKey]);
}
