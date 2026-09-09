import { useLayoutEffect } from "react";

/**
 * Khóa scroll của trang nền khi có overlay/modal toàn màn hình đang mở (VD TakeExerciseModal,
 * BatchTakeExerciseModal, ReviewVideoTaskModal) — các overlay này dùng `position: fixed` với vùng
 * cuộn riêng bên trong, nhưng `fixed` không chặn scroll của <body> phía sau.
 *
 * CHỈ set `overflow: hidden` trên body KHÔNG đủ trên Safari/WebKit (iOS) — bug đã biết: touch
 * scroll/rubber-band vẫn cuộn được trang nền dù body có overflow hidden (xác nhận thực tế 2026-09-05:
 * vẫn lộ 2 thanh cuộn trên desktop Edge/Safari + shortcut iOS "Add to Home Screen", dù hoạt động đúng
 * trên tab Safari/Edge thường của iPhone). Dùng kỹ thuật `position: fixed` trên body (giữ nguyên vị
 * trí cuộn qua `top` âm rồi khôi phục lúc mở khóa) — cách các thư viện body-scroll-lock dùng để chặn
 * được cả trên iOS.
 */
export function useLockBodyScroll(locked: boolean): void {
  useLayoutEffect(() => {
    if (!locked) return;
    const { body } = document;
    const scrollY = window.scrollY;
    const previous = {
      position: body.style.position,
      top: body.style.top,
      left: body.style.left,
      right: body.style.right,
      width: body.style.width,
      overflow: body.style.overflow
    };
    body.style.position = "fixed";
    body.style.top = `-${scrollY}px`;
    body.style.left = "0";
    body.style.right = "0";
    body.style.width = "100%";
    body.style.overflow = "hidden";
    return () => {
      body.style.position = previous.position;
      body.style.top = previous.top;
      body.style.left = previous.left;
      body.style.right = previous.right;
      body.style.width = previous.width;
      body.style.overflow = previous.overflow;
      window.scrollTo(0, scrollY);
    };
  }, [locked]);
}
