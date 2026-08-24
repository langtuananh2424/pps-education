import { useEffect, useState } from "react";

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-22) — đếm ngược sống dùng chung: ban đầu
 * cho hạn nộp BTVN (đã bỏ, xem AssignmentsTab.tsx), giờ dùng cho thời gian làm bài (Exercise.
 * timeLimitMinutes, tính từ ExerciseAttempt.startedAt — xem TakeExerciseModal.tsx). Tách hook riêng
 * khỏi CountdownText để nơi cần biết remainingMs (VD tự nộp bài khi hết giờ) không phải parse lại
 * chuỗi hiển thị.
 *
 * Tick mỗi giây — đơn giản hoá cố ý, không tự đổi tần suất theo khoảng còn lại (số đồng hồ đếm ngược
 * hiển thị cùng lúc trên 1 màn luôn rất ít, không đáng lo hiệu năng).
 */
export function useCountdown(targetIso: string | null): { remainingMs: number | null } {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!targetIso) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [targetIso]);

  if (!targetIso) return { remainingMs: null };
  return { remainingMs: new Date(targetIso).getTime() - now };
}

export function formatRemaining(
  remainingMs: number,
  t: (key: string, opts?: Record<string, number>) => string
): string {
  const totalSeconds = Math.max(0, Math.floor(remainingMs / 1000));
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (days > 0) return t("assignments.countdown.days", { d: days, h: hours });
  if (hours > 0) return t("assignments.countdown.hours", { h: hours, m: minutes });
  if (minutes > 0) return t("assignments.countdown.minutes", { m: minutes, s: seconds });
  return t("assignments.countdown.seconds", { s: seconds });
}
