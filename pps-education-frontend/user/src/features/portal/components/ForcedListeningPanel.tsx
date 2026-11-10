import { useState } from "react";
import { useTranslation } from "react-i18next";
import { CheckCircle2, Loader2, Lock, RotateCcw, ScrollText, XCircle } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import { ExerciseQuestionResponse, SectionResultResponse, SectionResultsResponse, viewTapescript } from "../api";

interface ForcedListeningPanelProps {
  attemptId: number;
  results: SectionResultsResponse;
  questions: ExerciseQuestionResponse[];
  /** Cho phép làm lại tự nguyện bài đã đạt (đang xem lượt đã nộp, còn quyền mở lượt mới, chưa xem đáp án). */
  canReopen: boolean;
  busy: boolean;
  onReopen: (sectionId: number) => void;
}

/**
 * V226 (Nghe ép, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-09) — bảng điểm từng bài tập
 * con + lời thoại (tapescript) đã mở. Backend CỐ TÌNH không trả câu nào đúng/sai: học sinh chỉ biết câu
 * sai khi lời thoại câu đó đã được mở. Mỗi lần học sinh mở một lời thoại là gọi API (backend ghi 1 lượt
 * xem cho thống kê giáo viên); đóng rồi mở lại tính thêm 1 lượt.
 */
export default function ForcedListeningPanel({ attemptId, results, questions, canReopen, busy, onReopen }: ForcedListeningPanelProps) {
  const { t } = useTranslation("portal-exercises");
  const [openText, setOpenText] = useState<Record<number, string>>({});
  const [loadingId, setLoadingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const orderOf = (questionId: number) => questions.find((q) => q.questionId === questionId)?.displayOrder ?? questionId;

  const toggleTapescript = async (questionId: number) => {
    if (openText[questionId] !== undefined) {
      setOpenText((prev) => {
        const next = { ...prev };
        delete next[questionId];
        return next;
      });
      return;
    }
    setLoadingId(questionId);
    setError(null);
    try {
      const res = await viewTapescript(attemptId, questionId);
      setOpenText((prev) => ({ ...prev, [questionId]: res.tapescript ?? "" }));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("forcedListening.tapescriptError")));
    } finally {
      setLoadingId(null);
    }
  };

  const sectionStatus = (s: SectionResultResponse) => {
    if (s.passed) return { label: t("forcedListening.passed"), className: "bg-emerald-50 text-emerald-700 border-emerald-200", Icon: CheckCircle2 };
    if (s.latestScorePercent == null) return { label: t("forcedListening.notAttempted"), className: "bg-slate-50 text-slate-600 border-slate-200", Icon: Lock };
    return { label: t("forcedListening.notPassed"), className: "bg-rose-50 text-rose-700 border-rose-200", Icon: XCircle };
  };

  return (
    <section className="rounded-2xl border border-line/60 bg-slate-50/60 p-4 space-y-3" aria-label={t("forcedListening.title")}>
      <div>
        <h4 className="text-sm font-black text-ink">{t("forcedListening.title")}</h4>
        <p className="text-xs font-bold text-muted mt-0.5">{t("forcedListening.intro")}</p>
      </div>

      {results.allPassed && (
        <p className="text-xs font-extrabold text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-xl px-3 py-2">
          {t("forcedListening.allPassed")}
          {results.overallPercent != null && ` ${t("forcedListening.overall", { percent: results.overallPercent })}`}
        </p>
      )}

      {error && <p className="text-xs font-bold text-rose-600">{error}</p>}

      <ul className="space-y-2">
        {results.sections.map((s) => {
          const status = sectionStatus(s);
          return (
            <li key={s.sectionId} className="rounded-xl border border-line/60 bg-white p-3 space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-extrabold text-ink">{s.title}</p>
                <span className={`inline-flex items-center gap-1 text-[11px] font-extrabold border rounded-full px-2.5 py-1 ${status.className}`}>
                  <status.Icon size={12} /> {status.label}
                </span>
              </div>
              <p className="text-xs font-bold text-muted">
                {t("forcedListening.threshold", { percent: s.passThresholdPercent })}
                {s.latestScorePercent != null && ` · ${t("forcedListening.latestScore", { percent: s.latestScorePercent })}`}
                {s.bestScorePercent != null && ` · ${t("forcedListening.bestScore", { percent: s.bestScorePercent })}`}
                {!s.passed && s.failedAttemptsCount > 0 && ` · ${t("forcedListening.failedAttempts", { count: s.failedAttemptsCount })}`}
              </p>
              {!s.passed && s.attemptsUntilNextTapescript != null && s.failedAttemptsCount > 0 && (
                <p className="text-[11px] font-bold text-amber-700">{t("forcedListening.untilNextTapescript", { count: s.attemptsUntilNextTapescript })}</p>
              )}

              {s.unlockedQuestionIds.length > 0 && (
                <div className="space-y-1.5">
                  <p className="text-[11px] font-black uppercase tracking-wide text-muted">{t("forcedListening.tapescriptTitle")}</p>
                  {s.unlockedQuestionIds.map((questionId) => {
                    const number = orderOf(questionId);
                    const text = openText[questionId];
                    return (
                      <div key={questionId} className="space-y-1">
                        <button
                          onClick={() => toggleTapescript(questionId)}
                          disabled={loadingId === questionId}
                          className="inline-flex items-center gap-1.5 text-xs font-extrabold text-teal-deep bg-teal/10 hover:bg-teal/20 border border-teal/20 rounded-lg px-3 py-1.5 disabled:opacity-60"
                        >
                          {loadingId === questionId ? <Loader2 size={12} className="animate-spin" /> : <ScrollText size={12} />}
                          {text !== undefined ? t("forcedListening.hideTapescript", { number }) : t("forcedListening.viewTapescript", { number })}
                        </button>
                        {text !== undefined && (
                          <p className="text-sm font-bold text-ink whitespace-pre-line bg-amber-50/60 border border-amber-100 rounded-xl p-3">
                            {text || t("forcedListening.tapescriptEmpty")}
                          </p>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}

              {s.passed && canReopen && (
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    onClick={() => onReopen(s.sectionId)}
                    disabled={busy}
                    className="inline-flex items-center gap-1.5 text-xs font-extrabold text-ink bg-white hover:bg-slate-100 border border-line rounded-lg px-3 py-1.5 disabled:opacity-60"
                  >
                    <RotateCcw size={12} /> {t("forcedListening.reopen")}
                  </button>
                  <span className="text-[11px] font-bold text-muted">{t("forcedListening.reopenHint")}</span>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
