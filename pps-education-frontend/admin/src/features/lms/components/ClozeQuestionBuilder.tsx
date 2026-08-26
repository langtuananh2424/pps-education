import React, { useState } from "react";
import { Check, Plus, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import { QuestionResponse, createExamQuestion } from "../api";

const inputClass = "w-full bg-white border border-slate-200 text-xs px-3.5 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red";
const labelClass = "block font-bold text-slate-700 mb-1 uppercase tracking-wider text-[10px]";

const MIN_OPTIONS = 2;
const MAX_OPTIONS = 6;
const DEFAULT_OPTIONS = 3;

interface BlankRow {
  content: string;
  options: string[];
  correctIndex: number;
}

const emptyBlank = (): BlankRow => ({ content: "", options: Array(DEFAULT_OPTIONS).fill(""), correctIndex: 0 });

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — dạng "Đọc điền từ — Cloze" (Ex5 "Choose
 * the best word (A/B/C)" của đề mẫu): 1 đoạn văn dùng chung + N chỗ trống, MỖI chỗ trống có bộ đáp án
 * RIÊNG (khác "Đọc hiểu — Lưới" GridQuestionBuilder — ở đó mọi câu dùng CHUNG 1 bộ đáp án là tên các
 * đoạn văn/passage). Về bản chất là N câu MULTIPLE_CHOICE dùng chung 1 referencePassage + 1 groupKey —
 * mirror đúng cách ListeningGroupBuilder cho mỗi câu VOICE_MULTIPLE_CHOICE 1 bộ đáp án riêng, chỉ đổi
 * "audio dùng chung" thành "đoạn văn dùng chung". Portal/Admin gộp hiển thị theo groupKey (xem
 * TakeExerciseModal.tsx) — khối "grid" đã tự hiện đúng bộ đáp án riêng từng câu từ bản sửa 2026-08-23,
 * không cần sửa thêm phía học sinh.
 */
export default function ClozeQuestionBuilder({
  examId,
  onCreated,
  onCancel
}: {
  examId: number;
  onCreated: (questions: QuestionResponse[]) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation("lms-question-authoring");
  const [passage, setPassage] = useState("");
  const [blanks, setBlanks] = useState<BlankRow[]>([emptyBlank(), emptyBlank()]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const updateContent = (idx: number, value: string) => {
    setBlanks((prev) => prev.map((b, i) => (i === idx ? { ...b, content: value } : b)));
  };
  const updateOption = (idx: number, optIdx: number, value: string) => {
    setBlanks((prev) => prev.map((b, i) => (i === idx ? { ...b, options: b.options.map((o, oi) => (oi === optIdx ? value : o)) } : b)));
  };
  const updateCorrectIndex = (idx: number, correctIndex: number) => {
    setBlanks((prev) => prev.map((b, i) => (i === idx ? { ...b, correctIndex } : b)));
  };
  const addOption = (idx: number) => {
    setBlanks((prev) => prev.map((b, i) => (i === idx && b.options.length < MAX_OPTIONS ? { ...b, options: [...b.options, ""] } : b)));
  };
  const removeOption = (idx: number, optIdx: number) => {
    setBlanks((prev) =>
      prev.map((b, i) =>
        i === idx && b.options.length > MIN_OPTIONS
          ? {
              ...b,
              options: b.options.filter((_, oi) => oi !== optIdx),
              correctIndex: b.correctIndex === optIdx ? 0 : b.correctIndex > optIdx ? b.correctIndex - 1 : b.correctIndex
            }
          : b
      )
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!passage.trim()) {
      setError(t("clozeQuestionBuilder.errors.passageRequired"));
      return;
    }
    if (blanks.length === 0) {
      setError(t("clozeQuestionBuilder.errors.needAtLeastOne"));
      return;
    }
    if (blanks.some((b) => !b.content.trim())) {
      setError(t("clozeQuestionBuilder.errors.fillAllBlanks"));
      return;
    }
    if (blanks.some((b) => b.options.some((o) => !o.trim()))) {
      setError(t("clozeQuestionBuilder.errors.fillAllOptions"));
      return;
    }

    const referencePassage = passage.trim();
    const groupKey = `cloze-${Date.now()}`;

    setSubmitting(true);
    const created: QuestionResponse[] = [];
    try {
      for (const b of blanks) {
        const result = await createExamQuestion(examId, {
          questionType: "MULTIPLE_CHOICE",
          skill: "READING",
          difficulty: "MEDIUM",
          content: b.content.trim(),
          referencePassage,
          groupKey,
          defaultPoints: 1,
          choices: b.options.map((content_, i) => ({
            choiceLabel: String.fromCharCode(65 + i),
            content: content_.trim(),
            isCorrect: i === b.correctIndex,
            displayOrder: i + 1
          }))
        });
        created.push(result);
      }
      onCreated(created);
    } catch (err) {
      setError(
        created.length > 0
          ? t("clozeQuestionBuilder.errors.partialFailure", { created: created.length, total: blanks.length })
          : err instanceof ApiError
            ? err.message
            : t("clozeQuestionBuilder.errors.createFailed")
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4 text-xs">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div>
        <label className={labelClass}>{t("clozeQuestionBuilder.passageLabel")}</label>
        <p className="text-[9px] text-slate-400 mb-1">{t("clozeQuestionBuilder.passageHint")}</p>
        <textarea
          required
          value={passage}
          onChange={(e) => setPassage(e.target.value)}
          placeholder={t("clozeQuestionBuilder.passagePlaceholder")}
          rows={8}
          className={inputClass}
        />
      </div>

      <div className="space-y-2">
        <span className="font-bold text-slate-700 uppercase tracking-wider text-[9px] block">{t("clozeQuestionBuilder.blanksSectionTitle")}</span>
        <div className="border border-slate-200 rounded-lg divide-y divide-slate-100">
          {blanks.map((b, idx) => (
            <div key={idx} className="p-3 space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-bold text-slate-500 w-8 shrink-0">({idx + 1})</span>
                <input
                  required
                  value={b.content}
                  onChange={(e) => updateContent(idx, e.target.value)}
                  placeholder={t("clozeQuestionBuilder.blankContentPlaceholder", { index: idx + 1 })}
                  className={`flex-1 ${inputClass}`}
                />
                {blanks.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setBlanks((prev) => prev.filter((_, i) => i !== idx))}
                    className="text-slate-400 hover:text-rose-600 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
              <div className="pl-10 space-y-1.5">
                {b.options.map((opt, optIdx) => (
                  <div key={optIdx} className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => updateCorrectIndex(idx, optIdx)}
                      className={`w-5 h-5 rounded-full border flex items-center justify-center font-bold shrink-0 text-[9px] transition-all ${
                        b.correctIndex === optIdx ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-400 hover:border-slate-400"
                      }`}
                    >
                      {b.correctIndex === optIdx ? <Check className="w-3 h-3 stroke-[3]" /> : String.fromCharCode(65 + optIdx)}
                    </button>
                    <input
                      required
                      value={opt}
                      onChange={(e) => updateOption(idx, optIdx, e.target.value)}
                      placeholder={t("common.answerOptionPlaceholder", { letter: String.fromCharCode(65 + optIdx) })}
                      className={`flex-1 ${inputClass}`}
                    />
                    {b.options.length > MIN_OPTIONS && (
                      <button type="button" onClick={() => removeOption(idx, optIdx)} className="text-slate-400 hover:text-rose-600 shrink-0">
                        <X className="w-3 h-3" />
                      </button>
                    )}
                  </div>
                ))}
                {b.options.length < MAX_OPTIONS && (
                  <button type="button" onClick={() => addOption(idx)} className="text-[10px] font-bold text-brand-red hover:underline">
                    {t("clozeQuestionBuilder.addOption")}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="flex items-center justify-between gap-2 pt-2 border-t border-slate-200">
        <Button type="button" variant="secondary" size="sm" onClick={() => setBlanks((prev) => [...prev, emptyBlank()])}>
          <Plus className="w-3.5 h-3.5" /> {t("clozeQuestionBuilder.addBlankButton")}
        </Button>
        <div className="flex items-center gap-2">
          <Button type="button" variant="secondary" onClick={onCancel}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("common.creating") : t("clozeQuestionBuilder.submitCreate", { count: blanks.length })}
          </Button>
        </div>
      </div>
    </form>
  );
}
