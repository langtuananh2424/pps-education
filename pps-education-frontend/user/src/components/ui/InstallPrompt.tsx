import React, { useEffect, useState } from "react";
import { Download, Share, X } from "lucide-react";
import { useTranslation } from "react-i18next";

const DISMISSED_KEY = "pps-install-prompt-dismissed";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

function isStandalone(): boolean {
  return (
    window.matchMedia("(display-mode: standalone)").matches ||
    (window.navigator as { standalone?: boolean }).standalone === true
  );
}

function isIos(): boolean {
  const ua = window.navigator.userAgent;
  const isIphoneOrIpad = /iphone|ipad|ipod/i.test(ua);
  const isIpadOS13Plus = navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1;
  return isIphoneOrIpad || isIpadOS13Plus;
}

/**
 * iOS chỉ cho phép "Thêm vào Màn hình chính" từ SAFARI — Chrome/Firefox/Edge trên iOS tuy cũng chạy
 * WebKit nhưng không có mục này trong menu Chia sẻ. Nhận diện để cảnh báo, tránh người dùng loay hoay
 * tìm mãi không thấy (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07).
 */
function isIosSafari(): boolean {
  const ua = window.navigator.userAgent;
  return isIos() && !/crios|fxios|edgios|opios/i.test(ua);
}

/**
 * Banner cài đặt PWA lên màn hình chính. Android/Chrome bắt được sự kiện `beforeinstallprompt` nên
 * có thể tự trigger cài đặt bằng 1 nút bấm.
 *
 * iOS thì KHÔNG có API nào tương đương — Apple cố tình không hỗ trợ `beforeinstallprompt` và cũng
 * không có cách nào khác để chủ động mở hộp thoại cài đặt. Đây là hạn chế cứng của WebKit, mọi PWA
 * trên iOS đều buộc phải hướng dẫn thao tác thủ công. Vì vậy trên iOS banner này mở rộng thành hướng
 * dẫn từng bước (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07 — trước đây chỉ có 1
 * dòng chữ ngắn, người dùng dễ bỏ qua và không biết bấm vào đâu).
 */
export default function InstallPrompt() {
  const { t } = useTranslation("common");
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [showIosHint, setShowIosHint] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [dismissed, setDismissed] = useState(() => localStorage.getItem(DISMISSED_KEY) === "1");

  useEffect(() => {
    if (isStandalone() || dismissed) return;

    if (isIos()) {
      setShowIosHint(true);
      return;
    }

    const handler = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };
    window.addEventListener("beforeinstallprompt", handler);
    return () => window.removeEventListener("beforeinstallprompt", handler);
  }, [dismissed]);

  const dismiss = () => {
    localStorage.setItem(DISMISSED_KEY, "1");
    setDismissed(true);
    setDeferredPrompt(null);
    setShowIosHint(false);
  };

  const handleInstall = async () => {
    if (!deferredPrompt) return;
    await deferredPrompt.prompt();
    await deferredPrompt.userChoice;
    setDeferredPrompt(null);
  };

  if (dismissed || (!deferredPrompt && !showIosHint)) return null;

  const iosSteps = [t("installPrompt.iosSteps.step1"), t("installPrompt.iosSteps.step2"), t("installPrompt.iosSteps.step3")];

  return (
    <div className="card-geometric fixed bottom-4 left-4 right-4 z-50 mx-auto max-w-md sm:left-auto sm:right-4">
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-teal text-white">
          {showIosHint ? <Share size={18} /> : <Download size={18} />}
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-black text-ink">{t("installPrompt.title")}</p>
          <p className="text-xs text-muted">
            {showIosHint ? t("installPrompt.iosHint") : t("installPrompt.androidHint")}
          </p>
        </div>
        {showIosHint ? (
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            className="btn-geometric-active shrink-0 !px-3 !py-2 text-xs"
          >
            {expanded ? t("installPrompt.hideGuide") : t("installPrompt.showGuide")}
          </button>
        ) : (
          <button type="button" onClick={handleInstall} className="btn-geometric-active shrink-0 !px-3 !py-2 text-xs">
            {t("installPrompt.installButton")}
          </button>
        )}
        <button
          type="button"
          onClick={dismiss}
          aria-label={t("installPrompt.close")}
          className="shrink-0 text-muted hover:text-ink"
        >
          <X size={16} />
        </button>
      </div>

      {showIosHint && expanded && (
        <div className="mt-3 border-t border-line/60 pt-3">
          {!isIosSafari() && (
            <p className="mb-2 rounded-lg bg-amber-50 px-2.5 py-2 text-[11px] font-bold text-amber-800">
              {t("installPrompt.iosSteps.safariOnly")}
            </p>
          )}
          <ol className="space-y-2">
            {iosSteps.map((step, index) => (
              <li key={index} className="flex gap-2 text-xs text-ink">
                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-teal text-[10px] font-black text-white">
                  {index + 1}
                </span>
                <span className="font-semibold leading-relaxed">{step}</span>
              </li>
            ))}
          </ol>
          <p className="mt-2 text-[11px] font-semibold text-muted">{t("installPrompt.iosSteps.whyBother")}</p>
        </div>
      )}
    </div>
  );
}
