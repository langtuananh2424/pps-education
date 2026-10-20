import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Flag, Pencil, Save } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { formatDateTime } from "@/lib/i18nFormat";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import FloatingError from "@/components/ui/FloatingError";
import {
  EssayGradingHistoryEntry,
  EssayGradingVersion,
  getEssayGradingHistory,
  gradeEssayAnswer
} from "@/features/academic/api";

/**
 * Chấm lần 2 bài Writing do AI chấm (bổ sung ngoài SDD gốc, xác nhận 2026-10-20) — popup "Lịch sử chấm AI" của câu
 * ESSAY ở trang Thống kê BTVN theo lớp, mirror ReflexStudentHistoryModal của Video phản xạ: bản chấm hiện hành mang cờ
 * "Điểm chính thức", bản AI cũ hiện "Không tính"; giáo viên chỉnh điểm + nhận xét ở bản hiện hành (POST
 * /api/answers/{id}/grade chấm đè, điểm AI gốc được giữ để đối chiếu).
 */
export default function EssayGradingHistoryModal({
  assignmentId,
  studentId,
  studentName,
  onClose,
  onChanged
}: {
  assignmentId: number;
  studentId: number;
  studentName: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { t, i18n } = useTranslation("academic-homework");
  const [entries, setEntries] = useState<EssayGradingHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = (showSpinner: boolean) => {
    if (showSpinner) setLoading(true);
    setLoadError(null);
    getEssayGradingHistory(assignmentId, studentId)
      .then(setEntries)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : t("exerciseDetail.studentModal.essayHistory.loadFailed")))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [assignmentId, studentId]);

  // Gom theo câu hỏi, lượt mới nhất lên trên (BE đã sắp theo thứ tự câu rồi lượt giảm dần).
  const groups = useMemo(() => {
    const byQuestion = new Map<number, { order: number; prompt: string; items: EssayGradingHistoryEntry[] }>();
    for (const e of entries) {
      const group = byQuestion.get(e.questionId) ?? { order: e.questionDisplayOrder, prompt: e.questionContent, items: [] };
      group.items.push(e);
      byQuestion.set(e.questionId, group);
    }
    return [...byQuestion.values()].sort((a, b) => a.order - b.order);
  }, [entries]);

  const handleSaved = () => {
    load(false);
    onChanged();
  };

  return (
    <Modal open onClose={onClose} size="xl" title={t("exerciseDetail.studentModal.essayHistory.title", { studentName })}>
      <div className="max-h-[78vh] overflow-y-auto space-y-4 pr-1" data-testid="essay-history-modal">
        <p className="text-[13px] text-slate-500 flex items-center gap-1.5">
          <Flag className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
          {t("exerciseDetail.studentModal.essayHistory.officialHint")}
        </p>
        {loading ? (
          <p className="text-sm text-slate-500">{t("exerciseDetail.studentModal.essayHistory.loading")}</p>
        ) : loadError ? (
          <FloatingError message={loadError} />
        ) : (
          groups.map((group) => (
            <div key={group.order} className="border border-slate-200 rounded-lg p-3 space-y-3">
              <p className="text-sm font-semibold text-slate-700">
                {t("exerciseDetail.studentModal.essayHistory.questionLabel", { order: group.order, prompt: group.prompt })}
              </p>
              {group.items.map((entry) => (
                <EssayAttemptBlock key={entry.studentAnswerId} entry={entry} language={i18n.language} onSaved={handleSaved} />
              ))}
            </div>
          ))
        )}
      </div>
    </Modal>
  );
}

/** Markup lỗi rubric v3 `{{mã|đoạn}}` — tô màu theo mức độ (hậu tố 2 = nặng/đỏ, 1 = nhẹ/vàng, ok/kg = xanh). */
const MARKED_TOKEN = /\{\{(ok|kg|sp1|sp2|gr1|gr2|wd1|wd2|pu1|pu2)\|([\s\S]*?)\}\}/g;

function tokenClassName(code: string): string {
  if (code === "ok" || code === "kg") return "text-emerald-700 underline decoration-emerald-400 decoration-2 underline-offset-2 font-semibold";
  return code.endsWith("2")
    ? "text-red-600 underline decoration-red-500 decoration-2 underline-offset-2 font-semibold"
    : "text-amber-700 underline decoration-amber-400 decoration-2 underline-offset-2 font-semibold";
}

function MarkedEssay({ text }: { text: string }) {
  const parts: React.ReactNode[] = [];
  let last = 0;
  let key = 0;
  let match: RegExpExecArray | null;
  const pattern = new RegExp(MARKED_TOKEN.source, "g");
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) parts.push(<React.Fragment key={key++}>{text.slice(last, match.index)}</React.Fragment>);
    parts.push(
      <span key={key++} className={tokenClassName(match[1])}>
        {match[2]}
        <sup className="ml-0.5 text-[9px] font-black uppercase no-underline">{match[1].replace(/\d$/, "")}</sup>
      </span>
    );
    last = pattern.lastIndex;
  }
  if (last < text.length) parts.push(<React.Fragment key={key++}>{text.slice(last)}</React.Fragment>);
  return <span className="whitespace-pre-wrap">{parts}</span>;
}

function EssayAttemptBlock({
  entry,
  language,
  onSaved
}: {
  entry: EssayGradingHistoryEntry;
  language: string;
  onSaved: () => void;
}) {
  const { t } = useTranslation("academic-homework");
  const keyPrefix = "exerciseDetail.studentModal.essayHistory";
  // Bản AI mới nhất = điểm AI gốc để đối chiếu khi giáo viên đã chấm lại.
  const aiOriginal = entry.versions.find((v) => v.gradingSource === "AI");
  // Bản hiện hành mang bài viết đã đánh dấu lỗi của AI (bản giáo viên chấm đè mang theo markedAnswer).
  const markedText = entry.versions.find((v) => v.markedAnswer)?.markedAnswer ?? entry.answerText;

  return (
    <div className="space-y-2">
      <p className="text-[12px] font-bold uppercase tracking-wider text-slate-400">
        {t(`${keyPrefix}.attemptLabel`, { number: entry.attemptNumber })}
      </p>
      {markedText && (
        <p className="text-sm text-slate-700">
          <span className="font-semibold">{t(`${keyPrefix}.answerLabel`)}: </span>
          <MarkedEssay text={markedText} />
        </p>
      )}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        {entry.versions.map((version) => (
          <EssayVersionRow
            key={version.id}
            studentAnswerId={entry.studentAnswerId}
            version={version}
            aiOriginal={version.gradingSource === "HUMAN" ? aiOriginal : undefined}
            language={language}
            onSaved={onSaved}
          />
        ))}
      </div>
    </div>
  );
}

function EssayVersionRow({
  studentAnswerId,
  version,
  aiOriginal,
  language,
  onSaved
}: {
  studentAnswerId: number;
  version: EssayGradingVersion;
  aiOriginal?: EssayGradingVersion;
  language: string;
  onSaved: () => void;
}) {
  const { t } = useTranslation("academic-homework");
  const keyPrefix = "exerciseDetail.studentModal.essayHistory";
  const isAi = version.gradingSource === "AI";
  const [editing, setEditing] = useState(false);
  const [score, setScore] = useState("");
  const [feedback, setFeedback] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const startEditing = () => {
    setScore(String(version.score));
    setFeedback(version.feedback ?? "");
    setSaveError(null);
    setEditing(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaveError(null);
    try {
      await gradeEssayAnswer(studentAnswerId, {
        score: parseFloat(score),
        maxScore: version.maxScore,
        feedback: feedback.trim() ? feedback.trim() : undefined
      });
      setEditing(false);
      onSaved();
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : t(`${keyPrefix}.saveFailed`));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      data-testid={version.official ? "essay-version-official" : "essay-version-old"}
      className={`rounded-lg p-2.5 space-y-1.5 border ${
        version.official ? "bg-emerald-50/40 border-emerald-200" : "bg-slate-50 border-slate-100 opacity-90"
      }`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-slate-600 flex items-center gap-1.5">
          {isAi ? t(`${keyPrefix}.aiLabel`) : t(`${keyPrefix}.teacherLabel`)}
          {version.official ? (
            <span className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-700 bg-emerald-100 rounded-full px-2 py-0.5">
              <Flag className="w-3 h-3" /> {t(`${keyPrefix}.officialBadge`)}
            </span>
          ) : (
            <span className="text-[11px] font-semibold text-slate-400 bg-slate-100 rounded-full px-2 py-0.5">
              {t(`${keyPrefix}.notCountedBadge`)}
            </span>
          )}
        </span>
        <span className="text-[13px] font-semibold text-slate-600">
          {t(`${keyPrefix}.score`, { score: version.score, maxScore: version.maxScore })}
          {aiOriginal && (
            <span className="ml-1.5 font-normal text-slate-400">{t(`${keyPrefix}.aiOriginalScore`, { score: aiOriginal.score })}</span>
          )}
        </span>
      </div>
      <p className="text-[12px] text-slate-400">
        {t(`${keyPrefix}.gradedBy`, { name: version.graderName, time: formatDateTime(version.gradedAt, language) })}
      </p>
      {version.feedback && (
        <p className="text-sm text-slate-600">
          <span className="font-semibold">{t(`${keyPrefix}.feedbackLabel`)}: </span>
          {version.feedback}
        </p>
      )}
      {isAi && version.criteriaScores && version.criteriaScores.length > 0 && (
        <div className="text-[13px] text-slate-600">
          <span className="font-semibold">{t(`${keyPrefix}.criteriaLabel`)}: </span>
          <ul className="list-disc list-inside">
            {version.criteriaScores.map((c) => (
              <li key={c.criterion}>
                {c.criterion}: {c.percent}%
              </li>
            ))}
          </ul>
        </div>
      )}

      {version.official &&
        (editing ? (
          <form onSubmit={handleSave} className="space-y-2 pt-2 border-t border-emerald-100">
            <div className="flex items-center gap-2">
              <label className="text-[12px] font-bold uppercase text-slate-500 shrink-0">
                {t(`${keyPrefix}.scoreLabel`, { max: version.maxScore })}
              </label>
              <input
                type="number"
                required
                min={0}
                max={version.maxScore}
                step={0.1}
                value={score}
                onChange={(ev) => setScore(ev.target.value)}
                data-testid="essay-score-input"
                className="w-24 bg-white border border-slate-200 text-sm px-2 py-1.5 rounded-lg focus:outline-none"
              />
            </div>
            <textarea
              rows={2}
              value={feedback}
              onChange={(ev) => setFeedback(ev.target.value)}
              placeholder={t(`${keyPrefix}.feedbackPlaceholder`)}
              data-testid="essay-feedback-input"
              className="w-full bg-white border border-slate-200 text-sm px-2 py-1.5 rounded-lg focus:outline-none"
            />
            {saveError && <p className="text-[13px] text-rose-600">{saveError}</p>}
            <div className="flex gap-2">
              <Button type="submit" variant="primary" disabled={saving} className="!py-1.5">
                <Save className="w-4 h-4" />
                {saving ? t(`${keyPrefix}.saving`) : t(`${keyPrefix}.save`)}
              </Button>
              <Button type="button" variant="secondary" disabled={saving} onClick={() => setEditing(false)} className="!py-1.5">
                {t(`${keyPrefix}.cancel`)}
              </Button>
            </div>
          </form>
        ) : (
          <div className="pt-1">
            <button
              type="button"
              onClick={startEditing}
              className="inline-flex items-center gap-1 text-[13px] font-bold text-brand-orange hover:underline cursor-pointer"
            >
              <Pencil className="w-3.5 h-3.5" /> {t(`${keyPrefix}.edit`)}
            </button>
          </div>
        ))}
    </div>
  );
}
