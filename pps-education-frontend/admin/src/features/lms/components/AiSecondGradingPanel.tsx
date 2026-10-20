import React, { useEffect, useState } from "react";
import { ArrowLeft, Bot, CheckCircle2, GraduationCap, Save, UserCheck } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import CountBadge from "@/components/ui/CountBadge";
import FloatingError from "@/components/ui/FloatingError";
import { formatDateTime } from "@/lib/i18nFormat";
import {
  AiGradedClassSummaryResponse,
  AiGradedItemResponse,
  gradeStudentAnswer,
  listAiGradedClasses,
  listAiGradedItems,
  reviewReflexStep
} from "../api";

/**
 * Hiển thị văn bản AI đã đánh dấu lỗi: Video phản xạ dùng {{err}}...{{/err}}, bài Writing dùng {{mã|đoạn}} —
 * đoạn bị đánh dấu được bôi nền đỏ nhạt, phần còn lại giữ nguyên.
 */
function MarkedText({ text }: { text: string }) {
  const parts: React.ReactNode[] = [];
  const pattern = /\{\{err\}\}([\s\S]*?)\{\{\/err\}\}|\{\{[^|{}]+\|([\s\S]*?)\}\}/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) parts.push(text.slice(last, match.index));
    parts.push(
      <mark key={match.index} className="bg-rose-100 text-rose-700 rounded px-0.5 underline decoration-rose-400">
        {match[1] ?? match[2]}
      </mark>
    );
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return <p className="text-[13px] text-slate-800 whitespace-pre-wrap leading-snug">{parts}</p>;
}

interface ItemCardProps {
  item: AiGradedItemResponse;
  onSaved: (updated: AiGradedItemResponse) => void;
}

/** 1 bài AI đã chấm: hiện đề, bài làm, điểm + nhận xét gốc của AI và form để giáo viên chấm lần 2. */
function AiGradedItemCard({ item, onSaved }: ItemCardProps) {
  const { t, i18n } = useTranslation("lms-grading");
  const [score, setScore] = useState("");
  const [feedback, setFeedback] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setScore(String(item.reviewed ? item.reviewedScore ?? item.aiScore : item.aiScore));
    setFeedback(item.reviewed ? item.reviewedFeedback ?? "" : "");
    setError(null);
  }, [item]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const value = parseFloat(score);
      const trimmed = feedback.trim() ? feedback.trim() : undefined;
      if (item.itemType === "ESSAY") {
        await gradeStudentAnswer(item.refId, { score: value, maxScore: item.maxScore, feedback: trimmed });
        onSaved({
          ...item,
          reviewed: true,
          reviewedScore: value,
          reviewedFeedback: trimmed ?? null,
          reviewedAt: new Date().toISOString(),
          reviewerName: item.reviewerName
        });
      } else {
        const step = item.itemType === "REFLEX_WRITING" ? "WRITING" : "SPEAKING";
        onSaved(await reviewReflexStep(item.refId, step, { score: value, feedback: trimmed }));
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("aiSecondGrading.errors.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  const aiTime = item.aiGradedAt ? formatDateTime(item.aiGradedAt, i18n.language) : "";
  const reviewedTime = item.reviewedAt ? formatDateTime(item.reviewedAt, i18n.language) : "";
  const text = item.itemType === "REFLEX_SPEAKING" ? item.markedText : item.markedText ?? item.studentText;

  return (
    <div className="border border-slate-200 rounded-xl p-3 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="text-sm font-bold text-slate-800 truncate">
            {item.studentFullName}
          </h3>
          <p className="text-[12px] text-slate-500 truncate" title={item.sourceTitle}>
            <span className="font-semibold text-slate-600">{t(`aiSecondGrading.itemType.${item.itemType}`)}</span> · {item.sourceTitle}
          </p>
        </div>
        {item.reviewed ? (
          <span className="flex items-center gap-1 text-[12px] font-bold text-emerald-600 shrink-0">
            <CheckCircle2 className="w-3.5 h-3.5" /> {t("aiSecondGrading.reviewedBadge")}
          </span>
        ) : (
          <span className="flex items-center gap-1 text-[12px] font-bold text-amber-600 shrink-0">
            <Bot className="w-3.5 h-3.5" /> {t("aiSecondGrading.awaitingBadge")}
          </span>
        )}
      </div>

      <div className="px-2.5 py-2 bg-slate-50 rounded-lg border border-slate-100 space-y-1.5">
        {item.prompt && <p className="text-[13px] font-semibold text-slate-700 line-clamp-2">{item.prompt}</p>}
        {item.audioUrl && <audio controls src={item.audioUrl} className="w-full h-8" />}
        {text && (
          <div className="max-h-28 overflow-y-auto">
            <MarkedText text={text} />
          </div>
        )}
      </div>

      <div className="flex items-start gap-2 text-sm" title={aiTime}>
        <span className="flex items-center gap-1 text-[12px] font-bold text-violet-600 shrink-0 pt-0.5">
          <Bot className="w-3.5 h-3.5" /> {t("aiSecondGrading.aiResultLabel")}
        </span>
        <span className="font-bold text-violet-700 shrink-0">
          {item.aiScore}/{item.maxScore}
        </span>
        {item.aiFeedback && <span className="text-[13px] text-slate-600 line-clamp-2">{item.aiFeedback}</span>}
      </div>

      {item.reviewed && (
        <div className="flex items-start gap-2 text-sm" title={reviewedTime}>
          <span className="flex items-center gap-1 text-[12px] font-bold text-emerald-600 shrink-0 pt-0.5">
            <UserCheck className="w-3.5 h-3.5" /> {item.reviewerName ?? t("aiSecondGrading.teacherResultLabel")}
          </span>
          <span className="font-bold text-emerald-700 shrink-0">
            {item.reviewedScore}/{item.maxScore}
          </span>
          {item.reviewedFeedback && <span className="text-[13px] text-slate-600 line-clamp-2">{item.reviewedFeedback}</span>}
        </div>
      )}

      <FloatingError message={error} onClose={() => setError(null)} />

      <form onSubmit={handleSubmit} className="flex items-center gap-2 pt-1 border-t border-slate-100">
        <input
          type="number"
          required
          min={0}
          max={item.maxScore}
          step={0.1}
          value={score}
          onChange={(e) => setScore(e.target.value)}
          aria-label={t("aiSecondGrading.scoreLabel", { max: item.maxScore })}
          title={t("aiSecondGrading.scoreLabel", { max: item.maxScore })}
          className="w-20 shrink-0 bg-slate-50 border border-slate-200 text-sm px-2 py-1.5 rounded-lg focus:outline-none"
        />
        <input
          type="text"
          value={feedback}
          onChange={(e) => setFeedback(e.target.value)}
          placeholder={t("aiSecondGrading.feedbackPlaceholder")}
          aria-label={t("aiSecondGrading.feedbackLabel")}
          className="flex-1 min-w-0 bg-slate-50 border border-slate-200 text-sm px-2 py-1.5 rounded-lg focus:outline-none"
        />
        <Button type="submit" variant="primary" disabled={saving} className="shrink-0 !py-1.5">
          <Save className="w-4 h-4" />
          {saving ? t("aiSecondGrading.saving") : item.reviewed ? t("aiSecondGrading.updateButton") : t("aiSecondGrading.saveButton")}
        </Button>
      </form>
    </div>
  );
}

interface AiSecondGradingPanelProps {
  onSaved: () => void;
}

/**
 * Chấm lần 2 (bổ sung ngoài SDD gốc, xác nhận 2026-10-19): giáo viên xem các bài AI đã chấm (Video phản xạ
 * Viết/Nói + bài tập Writing) của lớp mình đứng tên và chấm đè khi thấy AI chấm chưa đúng. Điểm giáo viên là
 * điểm chính thức của học sinh, điểm gốc của AI vẫn được lưu để đối chiếu.
 */
export default function AiSecondGradingPanel({ onSaved }: AiSecondGradingPanelProps) {
  const { t } = useTranslation("lms-grading");
  const [classes, setClasses] = useState<AiGradedClassSummaryResponse[]>([]);
  const [loadingClasses, setLoadingClasses] = useState(true);
  const [selectedClass, setSelectedClass] = useState<AiGradedClassSummaryResponse | null>(null);
  const [includeReviewed, setIncludeReviewed] = useState(false);
  const [items, setItems] = useState<AiGradedItemResponse[]>([]);
  const [loadingItems, setLoadingItems] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadClasses = () => {
    setLoadingClasses(true);
    listAiGradedClasses()
      .then(setClasses)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("aiSecondGrading.errors.loadClassesFailed")))
      .finally(() => setLoadingClasses(false));
  };

  useEffect(loadClasses, []);

  useEffect(() => {
    if (!selectedClass) {
      setItems([]);
      return;
    }
    setLoadingItems(true);
    listAiGradedItems(selectedClass.classId, includeReviewed)
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("aiSecondGrading.errors.loadItemsFailed")))
      .finally(() => setLoadingItems(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedClass, includeReviewed]);

  const handleSaved = (updated: AiGradedItemResponse) => {
    // Bài vừa chấm lại: nếu đang ẩn bài đã chấm lại thì gỡ khỏi danh sách, ngược lại cập nhật tại chỗ.
    setItems((prev) =>
      prev
        .map((i) => (i.itemType === updated.itemType && i.refId === updated.refId ? updated : i))
        .filter((i) => includeReviewed || !i.reviewed)
    );
    onSaved();
  };

  const backToClasses = () => {
    setSelectedClass(null);
    loadClasses();
  };

  return (
    <div className="space-y-4">
      <FloatingError message={error} onClose={() => setError(null)} />
      <p className="text-sm text-slate-500">{t("aiSecondGrading.intro")}</p>

      {!selectedClass ? (
        loadingClasses ? (
          <p className="text-sm text-slate-500">{t("aiSecondGrading.loadingClasses")}</p>
        ) : classes.length === 0 ? (
          <div className="h-40 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-sm italic gap-1.5 text-center p-4">
            <Bot className="w-6 h-6 text-slate-300" />
            <span>{t("aiSecondGrading.emptyClasses")}</span>
          </div>
        ) : (
          <div className="space-y-2 max-w-xl">
            {classes.map((c) => (
              <button
                key={c.classId}
                type="button"
                onClick={() => setSelectedClass(c)}
                className="w-full flex items-center gap-3 p-3.5 rounded-xl border border-slate-200 bg-white hover:border-brand-orange/40 hover:bg-orange-50/40 transition-colors text-left cursor-pointer"
              >
                <GraduationCap className="w-4 h-4 text-brand-orange shrink-0" />
                <span className="text-sm font-bold text-slate-800 flex-1">
                  {c.classCode} — {c.className}
                </span>
                <CountBadge count={c.awaitingReviewCount} />
              </button>
            ))}
          </div>
        )
      ) : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <button
              type="button"
              onClick={backToClasses}
              className="flex items-center gap-1.5 text-sm font-bold text-slate-500 hover:text-slate-800 cursor-pointer"
            >
              <ArrowLeft className="w-3.5 h-3.5" /> {t("examsPage.queue.backToClasses")}
            </button>
            <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer">
              <input type="checkbox" checked={includeReviewed} onChange={(e) => setIncludeReviewed(e.target.checked)} />
              {t("aiSecondGrading.includeReviewed")}
            </label>
          </div>
          <h2 className="text-sm font-bold text-slate-800">
            {selectedClass.classCode} — {selectedClass.className}
          </h2>

          {loadingItems ? (
            <p className="text-sm text-slate-500">{t("aiSecondGrading.loadingItems")}</p>
          ) : items.length === 0 ? (
            <div className="h-32 border border-dashed rounded-xl flex items-center justify-center text-slate-400 text-sm italic">
              {t("aiSecondGrading.emptyItems")}
            </div>
          ) : (
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-3">
              {items.map((item) => (
                <AiGradedItemCard key={`${item.itemType}-${item.refId}`} item={item} onSaved={handleSaved} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
