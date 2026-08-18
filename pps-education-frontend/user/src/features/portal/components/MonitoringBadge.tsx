import { useState } from "react";
import { ShieldAlert } from "lucide-react";
import { useTranslation } from "react-i18next";

/**
 * Chip ghim góc báo trạng thái giám sát chống gian lận (bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng 2026-08-12) — dùng CHUNG cho mọi màn làm bài có bật useIntegrityMonitor (TakeExerciseModal —
 * trắc nghiệm/điền từ/nghe/nói..., ReflexVideoTaskPage — Video phản xạ), thay cho banner amber căng
 * hết chiều rộng trước đây. Đặt cạnh nút Thoát/Đóng ở header, luôn "ghim" cố định vì header không
 * cuộn. Bấm để bật/tắt dòng cảnh báo đầy đủ (đóng lại khi bấm ra ngoài).
 */
export default function MonitoringBadge({ violationCount, exitNote }: { violationCount: number; exitNote?: string }) {
  const { t } = useTranslation("portal-exercises");
  const [open, setOpen] = useState(false);
  const hasViolation = violationCount > 0;
  return (
    <div className="relative shrink-0">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={t("monitoring.badgeAriaLabel")}
        className={`flex items-center gap-1 h-9 sm:h-10 px-2.5 sm:px-3 rounded-full border text-[11px] sm:text-xs font-extrabold transition-colors ${
          hasViolation ? "bg-rose-50 border-rose-200 text-rose-700" : "bg-amber-50 border-amber-200 text-amber-700"
        }`}
      >
        <ShieldAlert size={14} className="shrink-0" />
        {hasViolation && <span>{violationCount}</span>}
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-[105]" onClick={() => setOpen(false)} />
          <div
            className="absolute right-0 top-full mt-2 z-[106] w-64 max-w-[80vw] bg-white border border-line/60 rounded-xl shadow-lg p-3 text-left"
            onClick={(e) => e.stopPropagation()}
          >
            <p className="text-[11px] font-bold text-amber-800 leading-relaxed">
              {t("monitoring.tooltip", { exitNote: exitNote ?? t("monitoring.defaultExitNote") })}
              {hasViolation && t("monitoring.tooltipViolationSuffix", { count: violationCount })}
            </p>
          </div>
        </>
      )}
    </div>
  );
}
