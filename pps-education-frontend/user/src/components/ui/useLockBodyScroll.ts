import { useLayoutEffect } from "react";

/**
 * Khóa scroll của trang nền khi có overlay/modal toàn màn hình đang mở (VD TakeExerciseModal,
 * BatchTakeExerciseModal, ReviewVideoTaskModal) — các overlay này dùng `position: fixed` với vùng
 * cuộn riêng bên trong, nhưng `fixed` không chặn scroll của <body> phía sau nên học sinh vẫn có thể
 * cuộn nhầm trang chính. Khôi phục lại overflow cũ khi overlay đóng.
 */
export function useLockBodyScroll(locked: boolean): void {
  useLayoutEffect(() => {
    if (!locked) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [locked]);
}
