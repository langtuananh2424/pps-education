import React, { useEffect, useState } from "react";
import { Award, CheckCircle2, Save } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import { formatDateTime } from "@/lib/i18nFormat";
import { GradeReviewVideoSubmissionRequest, ReviewVideoSubmissionResponse, gradeReviewVideoSubmission } from "../api";

const DEFAULT_MAX_SCORE = 10;

interface ReviewVideoGradingPanelProps {
  submission: ReviewVideoSubmissionResponse | null;
  questionPrompt: string | null;
  videoTitle: string | null;
  onSaved: (updated: ReviewVideoSubmissionResponse) => void;
}

/**
 * UC-23b Main Flow bước 4: giáo viên nghe audio + chấm điểm/nhận xét cho 1 attempt Video phản xạ đã
 * nộp. KHÔNG dùng chung với GradingWorkshop.tsx (component đó buộc chặt vào type mock StudentExamSubmission
 * của Exercise UC-24/27 — khác cấu trúc dữ liệu, để riêng cho rõ ràng thay vì ép dùng chung).
 */
export default function ReviewVideoGradingPanel({ submission, questionPrompt, videoTitle, onSaved }: ReviewVideoGradingPanelProps) {
  const { t, i18n } = useTranslation("lms-grading");
  const [score, setScore] = useState("");
  const [maxScore, setMaxScore] = useState(String(DEFAULT_MAX_SCORE));
  const [feedback, setFeedback] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!submission) return;
    setScore(submission.score != null ? String(submission.score) : "");
    setMaxScore(submission.maxScore != null ? String(submission.maxScore) : String(DEFAULT_MAX_SCORE));
    setFeedback(submission.feedback ?? "");
    setError(null);
  }, [submission]);

  if (!submission) {
    return (
      <div className="h-64 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-xs italic gap-1.5 text-center p-4">
        <Award className="w-6 h-6 text-slate-300" />
        <span>{t("reviewVideoGradingPanel.emptyState")}</span>
      </div>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const request: GradeReviewVideoSubmissionRequest = {
        score: parseFloat(score),
        maxScore: parseFloat(maxScore),
        feedback: feedback.trim() ? feedback.trim() : undefined
      };
      const updated = await gradeReviewVideoSubmission(submission.id, request);
      onSaved(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("reviewVideoGradingPanel.errors.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="border-b border-slate-100 pb-2.5 flex items-center justify-between">
        <div>
          <span className="text-[10px] font-mono font-bold text-slate-400">
            {t("reviewVideoGradingPanel.header.attemptLabel", { attemptNumber: submission.attemptNumber })}
          </span>
          <h3 className="text-xs font-bold text-slate-800">{submission.studentFullName}</h3>
        </div>
        {submission.gradedAt && (
          <span className="flex items-center gap-1 text-[10px] font-bold text-emerald-600">
            <CheckCircle2 className="w-3.5 h-3.5" /> {t("reviewVideoGradingPanel.header.gradedBadge")}
          </span>
        )}
      </div>

      <div className="p-3.5 bg-slate-50 rounded-lg border border-slate-100 space-y-2">
        <span className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{videoTitle ?? t("reviewVideoGradingPanel.videoFallback")}</span>
        {questionPrompt && <p className="text-xs font-semibold text-slate-800">{questionPrompt}</p>}
        <p className="text-[10px] text-slate-400">
          {t("reviewVideoGradingPanel.submittedAt", { time: formatDateTime(submission.submittedAt, i18n.language) })}
        </p>
        <audio controls src={submission.audioUrl} className="w-full h-9" />
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <form onSubmit={handleSubmit} className="space-y-4 pt-1">
        <div className="flex gap-3">
          <div className="flex-1 space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("reviewVideoGradingPanel.scoreLabel")}</label>
            <input
              type="number"
              required
              min={0}
              step={0.1}
              value={score}
              onChange={(e) => setScore(e.target.value)}
              className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
            />
          </div>
          <div className="flex-1 space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("reviewVideoGradingPanel.maxScoreLabel")}</label>
            <input
              type="number"
              required
              min={0}
              step={0.1}
              value={maxScore}
              onChange={(e) => setMaxScore(e.target.value)}
              className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
            />
          </div>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("reviewVideoGradingPanel.feedbackLabel")}</label>
          <textarea
            placeholder={t("reviewVideoGradingPanel.feedbackPlaceholder")}
            value={feedback}
            onChange={(e) => setFeedback(e.target.value)}
            rows={3}
            className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
          />
        </div>

        <Button type="submit" variant="primary" disabled={saving} className="w-full">
          <Save className="w-4 h-4" />
          {saving ? t("reviewVideoGradingPanel.saving") : t("reviewVideoGradingPanel.saveButton")}
        </Button>
      </form>
    </div>
  );
}
