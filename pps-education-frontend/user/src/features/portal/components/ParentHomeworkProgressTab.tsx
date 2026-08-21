import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertCircle, BookOpen, CheckCircle2, Clock, Video } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { HomeworkProgressResponse, listHomeworkProgress } from "../api";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — trước đây mọi % (kể cả dưới ngưỡng
 * đạt, VD 45%) đều hiện y hệt màu xanh + dấu tick, dễ hiểu nhầm là đã ổn. Giờ tách rõ: "Chưa làm bài"/
 * "Đang chờ chấm" (progress không phải số %, `passed` luôn null lúc này) giữ kiểu chữ thường màu trung
 * tính/xanh dương; đã có % thì bắt buộc có `passed` kèm theo (BE tính sẵn theo đúng ngưỡng đạt của
 * từng loại BTVN), hiện thành pill nổi bật xanh lá "Đạt" hoặc đỏ "Chưa đạt" kèm %.
 */
function ProgressBadge({ progress, passed }: { progress: string | null; passed: boolean | null }) {
  const { t } = useTranslation("portal-exercises");
  if (!progress) return null;
  if (passed == null) {
    const isWaiting = progress === "Đang chờ chấm";
    return (
      <p className={`text-[11px] font-bold mt-0.5 flex items-center gap-1 ${isWaiting ? "text-sky-700" : "text-muted"}`}>
        <Clock size={11} aria-hidden="true" /> {progress}
      </p>
    );
  }
  return (
    <span
      className={`inline-flex items-center gap-1 mt-1 px-2 py-1 rounded-lg text-xs font-black ${
        passed ? "bg-emerald-100 text-emerald-800 border border-emerald-300" : "bg-rose-100 text-rose-800 border border-rose-300"
      }`}
    >
      {passed ? <CheckCircle2 size={13} aria-hidden="true" /> : <AlertCircle size={13} aria-hidden="true" />}
      {progress} — {passed ? t("parentHomework.passed") : t("parentHomework.notPassed")}
    </span>
  );
}

interface ParentHomeworkProgressTabProps {
  studentId: number;
  classId: number;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — bấm link "Bài ngữ pháp/nghe"/"Video
   * TKN/PX" ở tab Quá trình học tập (DailyLearningProgressTab) nhảy sang đây, PortalPage set commentId
   * của đúng buổi đó. Phụ huynh chỉ XEM (không có màn làm bài riêng như học sinh — UC-64), nên "nhảy
   * tới bài làm" ở đây nghĩa là cuộn tới + highlight tạm đúng dòng kết quả (đã hiện sẵn % đạt) thay vì
   * mở modal. Dùng commentId (không phải assignmentId) vì 1 dòng ở component này ứng đúng 1
   * StudentComment, khớp thẳng với log.id bên DailyLearningProgressTab.
   */
  highlightCommentId?: number | null;
  onHighlightHandled?: () => void;
}

/** UC-64 (2026-07-29) — Cổng phụ huynh xem tiến độ BTVN đã giao cho con (chỉ xem, không phải giao diện làm bài — con tự làm ở Portal Học sinh). */
export default function ParentHomeworkProgressTab({ studentId, classId, highlightCommentId, onHighlightHandled }: ParentHomeworkProgressTabProps) {
  const { t } = useTranslation("portal-exercises");
  const [items, setItems] = useState<HomeworkProgressResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [justHighlighted, setJustHighlighted] = useState<number | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listHomeworkProgress(studentId, classId)
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("parentHomework.loadError")))
      .finally(() => setLoading(false));
  }, [studentId, classId]);

  // Cuộn tới + highlight tạm ~2.5s đúng dòng theo commentId truyền vào — chỉ chạy được sau khi items
  // đã tải xong (loading=false) và DOM đã render (id="parent-homework-comment-{id}" gắn ở mỗi dòng bên
  // dưới), không thì tìm không ra phần tử. PHẢI đặt trước early-return `if (loading)` (Rules of Hooks).
  useEffect(() => {
    if (loading || highlightCommentId == null) return;
    const el = document.getElementById(`parent-homework-comment-${highlightCommentId}`);
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    setJustHighlighted(highlightCommentId);
    onHighlightHandled?.();
    const timer = setTimeout(() => setJustHighlighted(null), 2500);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, highlightCommentId, items]);

  if (loading) return <p className="text-sm text-muted font-bold">{t("parentHomework.loading")}</p>;

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-black text-ink font-display">{t("parentHomework.title")}</h2>
        <p className="text-xs text-muted font-bold mt-0.5">{t("parentHomework.description")}</p>
      </div>

      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      {items.length === 0 ? (
        <p className="text-xs text-muted font-bold italic text-center py-10">{t("parentHomework.empty")}</p>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <div
              key={item.commentId}
              id={`parent-homework-comment-${item.commentId}`}
              className={`bg-white border rounded-2xl p-4 space-y-3 transition-all ${
                justHighlighted === item.commentId ? "border-teal ring-2 ring-teal/40" : "border-line/80"
              }`}
            >
              <div className="flex items-center gap-1.5 text-[10px] font-extrabold text-muted uppercase">
                <Clock size={11} aria-hidden="true" /> {item.commentDate}
              </div>

              {(item.grammarTitle || item.grammarOfflineText) && (
                <div className="flex items-start gap-2.5 p-3 bg-sky-2 rounded-xl border border-teal/20">
                  <BookOpen size={16} className="text-teal-deep shrink-0 mt-0.5" aria-hidden="true" />
                  <div className="min-w-0">
                    <p className="text-[10px] font-extrabold text-teal-deep uppercase">{t("parentHomework.grammarLabel")}</p>
                    <p className="text-xs font-bold text-ink truncate">{item.grammarTitle ?? item.grammarOfflineText}</p>
                    <ProgressBadge progress={item.grammarProgress} passed={item.grammarPassed} />
                  </div>
                </div>
              )}

              {item.videoTitle && (
                <div className="flex items-start gap-2.5 p-3 bg-amber-50/60 rounded-xl border border-amber-200">
                  <Video size={16} className="text-amber-700 shrink-0 mt-0.5" aria-hidden="true" />
                  <div className="min-w-0">
                    <p className="text-[10px] font-extrabold text-amber-800 uppercase">{t("parentHomework.videoLabel")}</p>
                    <p className="text-xs font-bold text-ink truncate">{item.videoTitle}</p>
                    <ProgressBadge progress={item.videoProgress} passed={item.videoPassed} />
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
