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
 * Banner cài đặt PWA lên màn hình chính. Android/Chrome bắt được sự kiện
 * `beforeinstallprompt` nên có thể tự trigger; iOS Safari không có API này
 * nên chỉ hướng dẫn thao tác thủ công (Chia sẻ → Thêm vào MH chính).
 */
export default function InstallPrompt() {
  const { t } = useTranslation("common");
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [showIosHint, setShowIosHint] = useState(false);
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

  return (
    <div className="card-geometric fixed bottom-4 left-4 right-4 z-50 mx-auto flex max-w-md items-center gap-3 sm:left-auto sm:right-4">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-teal text-white">
        {showIosHint ? <Share size={18} /> : <Download size={18} />}
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-black text-ink">{t("installPrompt.title")}</p>
        <p className="text-xs text-muted">
          {showIosHint ? t("installPrompt.iosHint") : t("installPrompt.androidHint")}
        </p>
      </div>
      {!showIosHint && (
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
  );
}
