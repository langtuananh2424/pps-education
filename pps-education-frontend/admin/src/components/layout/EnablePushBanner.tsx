import { useState } from "react";
import { BellRing, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { enablePushFromUserGesture } from "@/lib/pushNotifications";

/**
 * Banner "Bật thông báo" — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-23. Trước đây là 1
 * nút pill nhét chung vào 1 hàng với Điểm trường/Lớp/Chấm công/ngày/ngôn ngữ/chuông thông báo trong
 * `<header>` (chật, khó nhìn) — đổi sang banner full-width riêng ngay dưới header, mirror y hệt
 * `EnablePushBanner` bên app "user" (Portal Học Sinh/Phụ huynh) để đồng bộ ngôn ngữ hình ảnh, chỉ đổi
 * i18n namespace/key cho đúng cấu trúc `layout.json` của admin.
 *
 * Apple BẮT BUỘC Notification.requestPermission() phải được gọi trực tiếp bên trong 1 thao tác chạm
 * của người dùng — luồng tự động sau khi login (setupPushNotifications trong AppContext) chỉ ĐỌC
 * Notification.permission, không tự gọi requestPermission(). Nút bấm ở đây là đường xin quyền ĐÚNG
 * CHUẨN Apple — bấm là gọi ngay requestPermission() trước mọi await.
 *
 * Chỉ hiện khi trình duyệt hỗ trợ Notification VÀ quyền chưa được cấp — cấp xong là banner tự ẩn.
 */
export default function EnablePushBanner() {
  const { t } = useTranslation("layout");
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
    <div className="px-2 md:px-0 mb-4">
      <div className="flex items-center gap-2 rounded-xl border-2 border-teal-200 bg-teal-50 px-3 py-2.5">
        <BellRing size={18} className="shrink-0 text-teal-700" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="text-xs font-extrabold text-teal-700">{t("header.enablePush.title")}</p>
          <p className="text-[11px] font-semibold text-slate-500">
            {failed ? t("header.enablePush.failedHint") : t("header.enablePush.description")}
          </p>
        </div>
        <button
          type="button"
          onClick={handleEnable}
          disabled={busy}
          aria-label={t("header.enablePush.ariaLabel")}
          className="shrink-0 rounded-lg bg-teal-600 px-3 py-1.5 text-[11px] font-extrabold text-white transition-colors hover:bg-teal-700 disabled:opacity-50"
        >
          {busy ? t("header.enablePush.enabling") : t("header.enablePush.button")}
        </button>
        <button
          type="button"
          onClick={() => setDismissed(true)}
          aria-label={t("header.enablePush.dismiss")}
          className="shrink-0 rounded-lg p-1 text-slate-400 transition-colors hover:bg-white/60"
        >
          <X size={14} aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
