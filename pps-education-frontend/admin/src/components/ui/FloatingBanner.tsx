import React, { useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore } from "react";
import { createPortal } from "react-dom";
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/cn";

export type FloatingBannerVariant = "error" | "success" | "warning" | "info";

const STACK_ID = "floating-banner-stack";
export const DEFAULT_AUTO_HIDE_MS: Record<FloatingBannerVariant, number> = { error: 8000, success: 4000, warning: 10000, info: 8000 };
/** Nội dung đang hiển thị → instance đang giữ banner đó (xem chống trùng trong FloatingBanner). */
const activeMessages = new Map<string, symbol>();
const LEADING_EMOJI = /^\s*(?:\p{Extended_Pictographic}️?\s*)+/u;

/**
 * Ngăn xếp chung cố định ở giữa phía trên viewport — mọi banner (lỗi + thành công) portal vào đây nên
 * nhiều thông báo cùng lúc xếp chồng dọc thay vì đè lên nhau. z-[300] cao hơn Modal (z-50) và các popover
 * (z-[200]) để banner không bị lớp phủ modal che mất.
 */
function getStack(): HTMLElement {
  let stack = document.getElementById(STACK_ID);
  if (!stack) {
    stack = document.createElement("div");
    stack.id = STACK_ID;
    stack.setAttribute("aria-live", "assertive");
    stack.className =
      "fixed top-4 left-1/2 -translate-x-1/2 z-[300] flex flex-col items-center gap-2 w-[min(92vw,640px)] pointer-events-none";
    document.body.appendChild(stack);
  }
  return stack;
}

const variantStyles: Record<FloatingBannerVariant, { box: string; icon: string; close: string; Icon: typeof AlertCircle }> = {
  error: {
    box: "border-rose-200 border-l-rose-500 text-rose-700",
    icon: "text-rose-500",
    close: "text-rose-400 hover:text-rose-700 hover:bg-rose-50",
    Icon: AlertCircle
  },
  success: {
    box: "border-emerald-200 border-l-emerald-500 text-emerald-700",
    icon: "text-emerald-500",
    close: "text-emerald-400 hover:text-emerald-700 hover:bg-emerald-50",
    Icon: CheckCircle2
  },
  warning: {
    box: "border-amber-200 border-l-amber-500 text-amber-800",
    icon: "text-amber-500",
    close: "text-amber-400 hover:text-amber-800 hover:bg-amber-50",
    Icon: AlertTriangle
  },
  info: {
    box: "border-sky-200 border-l-sky-500 text-sky-800",
    icon: "text-sky-500",
    close: "text-sky-400 hover:text-sky-800 hover:bg-sky-50",
    Icon: Info
  }
};

export interface FloatingBannerProps {
  /** Nội dung — rỗng/null/false thì không hiện gì. */
  message: React.ReactNode;
  variant?: FloatingBannerVariant;
  /** Gọi khi người dùng bấm đóng hoặc hết thời gian tự ẩn — thường là `() => setError(null)` để lần sau
   *  (kể cả trùng nội dung) vẫn kích hoạt hiện lại banner. */
  onClose?: () => void;
  /** Tự ẩn sau N ms (mặc định: lỗi 8s, thành công 4s, cảnh báo 10s, thông tin 8s); 0 = giữ tới khi người dùng tự đóng. */
  autoHideMs?: number;
}

/**
 * Banner nổi ở giữa phía trên trang (2026-10-01, thay cho khối lỗi nằm trong trang/modal và toast thành
 * công góc dưới phải). Không chiếm chỗ trong layout — đặt ở đâu trong cây JSX cũng được.
 */
export default function FloatingBanner({ message, variant = "error", onClose, autoHideMs }: FloatingBannerProps) {
  const { t } = useTranslation("common");
  const [visible, setVisible] = useState(false);
  const [isOwner, setIsOwner] = useState(true);
  const instanceId = useRef(Symbol("floating-banner"));
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const hideAfter = autoHideMs ?? DEFAULT_AUTO_HIDE_MS[variant];
  const hasMessage = message !== null && message !== undefined && message !== false && message !== "";

  useEffect(() => {
    setVisible(hasMessage);
    if (!hasMessage || hideAfter <= 0) return;
    const timer = window.setTimeout(() => {
      setVisible(false);
      onCloseRef.current?.();
    }, hideAfter);
    return () => window.clearTimeout(timer);
  }, [message, hasMessage, hideAfter]);

  // Emoji đầu câu (✅/⚠️/📅... có sẵn trong nhiều câu i18n) bỏ đi — banner đã có icon + màu riêng.
  const text = typeof message === "string" ? message.replace(LEADING_EMOJI, "") : message;

  // Cùng 1 nội dung đôi khi được báo từ 2 nơi (VD danh sách + modal con mở cùng lúc; trang Nhận xét báo
  // "lưu nháp lỗi" (vàng) đúng lúc popup Rời trang báo cùng câu đó (đỏ)) — chỉ instance đầu tiên giữ banner
  // cho mỗi nội dung chữ, bất kể màu, tránh 2 banner y hệt chồng nhau.
  const dedupeKey = visible && typeof text === "string" ? text.trim() : null;
  useLayoutEffect(() => {
    if (!dedupeKey) return;
    const id = instanceId.current;
    const owner = activeMessages.get(dedupeKey);
    if (owner && owner !== id) {
      setIsOwner(false);
      return;
    }
    activeMessages.set(dedupeKey, id);
    setIsOwner(true);
    return () => {
      if (activeMessages.get(dedupeKey) === id) activeMessages.delete(dedupeKey);
    };
  }, [dedupeKey]);

  if (!hasMessage || !visible || !isOwner) return null;

  const close = () => {
    setVisible(false);
    onCloseRef.current?.();
  };
  const style = variantStyles[variant];

  return createPortal(
    <div
      role={variant === "error" ? "alert" : "status"}
      data-variant={variant}
      className={cn(
        "pointer-events-auto w-full flex items-start gap-2.5 bg-white border border-l-4 text-sm font-medium rounded-xl shadow-2xl px-4 py-3 animate-in fade-in slide-in-from-top-4 duration-300",
        style.box
      )}
    >
      <style.Icon className={cn("w-4 h-4 mt-0.5 shrink-0", style.icon)} />
      <div className="flex-1 min-w-0 whitespace-pre-line break-words">{text}</div>
      <button
        type="button"
        onClick={close}
        aria-label={t("floatingError.close")}
        className={cn("shrink-0 -mr-1 p-0.5 rounded-md transition-colors", style.close)}
      >
        <X className="w-4 h-4" />
      </button>
    </div>,
    getStack()
  );
}

/* ---------------------------------------------------------------------------------------------------
 * API gọi thẳng từ event handler (không cần state) — thay cho alertDialog khi báo lỗi/thành công 1 lần.
 * FloatingBannerHost (mount 1 lần ở App.tsx) render các thông báo đang chờ.
 * ------------------------------------------------------------------------------------------------- */
interface QueuedBanner {
  id: number;
  variant: FloatingBannerVariant;
  message: string;
}

let queue: QueuedBanner[] = [];
let nextId = 1;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

function push(variant: FloatingBannerVariant, message: string) {
  if (!message) return;
  queue = [...queue.filter((b) => !(b.variant === variant && b.message === message)), { id: nextId++, variant, message }];
  emit();
}

function remove(id: number) {
  queue = queue.filter((b) => b.id !== id);
  emit();
}

export const notifyError = (message: string) => push("error", message);
export const notifySuccess = (message: string) => push("success", message);
export const notifyWarning = (message: string) => push("warning", message);
export const notifyInfo = (message: string) => push("info", message);

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
const getQueue = () => queue;

export function FloatingBannerHost() {
  const items = useSyncExternalStore(subscribe, getQueue);
  return (
    <>
      {items.map((b) => (
        <FloatingBanner key={b.id} variant={b.variant} message={b.message} onClose={() => remove(b.id)} />
      ))}
    </>
  );
}
