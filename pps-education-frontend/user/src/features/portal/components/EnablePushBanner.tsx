import { useState } from "react";
import { BellRing, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { enablePushFromUserGesture } from "@/lib/pushNotifications";

/**
 * Banner "Bật thông báo" — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07.
 *
 * Apple BẮT BUỘC Notification.requestPermission() phải được gọi trực tiếp bên trong 1 thao tác chạm
 * của người dùng. Luồng tự động sau khi login (setupPushNotifications trong AppContext) chạy sau
 * nhiều await nên "user activation" đã hết hiệu lực → iOS từ chối thẳng, không hiện dialog, trả về
 * "default" (xác nhận qua bảng push_setup_logs trên staging: mọi lần tự động đều permission-denied,
 * chỉ thao tác thủ công của người dùng mới thành công). Nút bấm ở đây là đường xin quyền ĐÚNG CHUẨN
 * Apple — bấm là gọi ngay requestPermission() trước mọi await.
 *
 * Chỉ hiện khi trình duyệt hỗ trợ Notification VÀ quyền chưa được cấp — cấp xong là banner tự ẩn.
 */
export default function EnablePushBanner() {
  const { t } = useTranslation("portal");
  const supported = typeof Notification !== "undefined";
  const [dismissed, setDismissed] = useState(false);
  const [permission, setPermission] = useState<NotificationPermission | null>(
    supported ? Notification.permission : null
  );
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);

  if (!supported || dismissed || permission === "granted") return null;

  const handleEnable = async () => {
    setBusy(true);
    setFailed(false);
    try {
      const result = await enablePushFromUserGesture();
      setPermission(Notification.permission);
      setFailed(result.status !== "registered");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mb-3 flex items-center gap-2 rounded-xl border-2 border-teal/30 bg-sky-2 px-3 py-2.5">
      <BellRing size={18} className="shrink-0 text-teal-deep" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="text-xs font-extrabold text-teal-deep">{t("enablePush.title")}</p>
        <p className="text-[11px] font-semibold text-muted">
          {failed ? t("enablePush.failedHint") : t("enablePush.description")}
        </p>
      </div>
      <button
        type="button"
        onClick={handleEnable}
        disabled={busy}
        className="shrink-0 rounded-lg bg-teal px-3 py-1.5 text-[11px] font-extrabold text-white transition-colors hover:bg-teal-deep disabled:opacity-50"
      >
        {busy ? t("enablePush.enabling") : t("enablePush.enable")}
      </button>
      <button
        type="button"
        onClick={() => setDismissed(true)}
        aria-label={t("enablePush.dismiss")}
        className="shrink-0 rounded-lg p-1 text-muted transition-colors hover:bg-white/60"
      >
        <X size={14} aria-hidden="true" />
      </button>
    </div>
  );
}
