import React, { useState } from "react";
import { BookOpen, Plus, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import { QuestionResponse, createExamQuestion } from "../api";

const inputClass = "w-full bg-white border border-slate-200 text-xs px-3.5 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red";
const labelClass = "block font-bold text-slate-700 mb-1 uppercase tracking-wider text-[10px]";

interface PassageRow {
  name: string;
  text: string;
}

interface QuestionRow {
  content: string;
  correctIndex: number;
}

const EMPTY_PASSAGE: PassageRow = { name: "", text: "" };
const emptyQuestion = (): QuestionRow => ({ content: "", correctIndex: 0 });

/**
 * V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — dạng "Đọc hiểu — lưới" (Ex5
 * của đề mẫu): N đoạn văn ngắn (mỗi đoạn gắn 1 tên nhân vật) + M câu hỏi, mỗi câu chọn ĐÚNG 1 trong
 * các tên nhân vật đó. Về bản chất là M câu MULTIPLE_CHOICE dùng chung 1 referencePassage (gộp toàn
 * bộ đoạn văn) + cùng 1 groupKey — Portal/Admin gộp hiển thị thành 1 bảng dựa vào groupKey (xem
 * TakeExerciseModal.tsx). Tách khỏi QuestionEditorForm (soạn 1 câu) theo SRP vì đây là composite tạo
 * NHIỀU Question cùng lúc.
 */
export default function GridQuestionBuilder({
  examId,
  onCreated,
  onCancel
}: {
  examId: number;
  onCreated: (questions: QuestionResponse[]) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation("lms-question-authoring");
  const [passages, setPassages] = useState<PassageRow[]>([{ ...EMPTY_PASSAGE }, { ...EMPTY_PASSAGE }, { ...EMPTY_PASSAGE }]);
  const [questions, setQuestions] = useState<QuestionRow[]>([emptyQuestion(), emptyQuestion()]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const updatePassage = (idx: number, field: keyof PassageRow, value: string) => {
    setPassages((prev) => prev.map((p, i) => (i === idx ? { ...p, [field]: value } : p)));
  };

  const updateQuestionContent = (idx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, content: value } : q)));
  };

  const updateQuestionAnswer = (idx: number, correctIndex: number) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, correctIndex } : q)));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (passages.some((p) => !p.name.trim() || !p.text.trim())) {
      setError(t("gridQuestionBuilder.errors.fillAllPassages"));
      return;
    }
    if (questions.some((q) => !q.content.trim())) {
      setError(t("gridQuestionBuilder.errors.fillAllQuestions"));
      return;
    }
    if (questions.length === 0) {
      setError(t("gridQuestionBuilder.errors.needAtLeastOne"));
      return;
    }

    const referencePassage = passages.map((p) => `${p.name.trim()}: ${p.text.trim()}`).join("\n\n");
    const groupKey = `grid-${Date.now()}`;
    const choiceLabels = passages.map((p, i) => ({ choiceLabel: String.fromCharCode(65 + i), content: p.name.trim() }));

    setSubmitting(true);
    const created: QuestionResponse[] = [];
    try {
      for (let i = 0; i < questions.length; i++) {
        const q = questions[i];
        const result = await createExamQuestion(examId, {
          questionType: "MULTIPLE_CHOICE",
          skill: "READING",
          difficulty: "MEDIUM",
          content: q.content.trim(),
          referencePassage,
          groupKey,
          defaultPoints: 1,
          choices: choiceLabels.map((c, idx) => ({ ...c, isCorrect: idx === q.correctIndex, displayOrder: idx + 1 }))
        });
        created.push(result);
      }
      onCreated(created);
    } catch (err) {
      setError(
        created.length > 0
          ? t("gridQuestionBuilder.errors.partialFailure", { created: created.length, total: questions.length })
          : err instanceof ApiError
            ? err.message
            : t("gridQuestionBuilder.errors.createFailed")
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4 text-xs">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 space-y-3">
        <div className="flex items-center gap-1 text-slate-700 font-bold uppercase tracking-wider text-[9px]">
          <BookOpen className="w-4 h-4 text-slate-500" />
          <span>{t("gridQuestionBuilder.passagesSectionTitle")}</span>
        </div>
        {passages.map((p, idx) => (
          <div key={idx} className="grid grid-cols-1 md:grid-cols-4 gap-2 items-start">
            <input
              required
              value={p.name}
              onChange={(e) => updatePassage(idx, "name", e.target.value)}
              placeholder={t("gridQuestionBuilder.passageNamePlaceholder", { index: idx + 1 })}
              className={inputClass}
            />
            <textarea
              required
              rows={2}
              value={p.text}
              onChange={(e) => updatePassage(idx, "text", e.target.value)}
              placeholder={t("gridQuestionBuilder.passageTextPlaceholder")}
              className={`md:col-span-3 ${inputClass}`}
            />
          </div>
        ))}
        <p className="text-[9px] text-slate-400">{t("gridQuestionBuilder.passagesHint")}</p>
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <span className="font-bold text-slate-700 uppercase tracking-wider text-[9px]">{t("gridQuestionBuilder.questionsSectionTitle")}</span>
          <Button type="button" variant="secondary" size="sm" onClick={() => setQuestions((prev) => [...prev, emptyQuestion()])}>
            <Plus className="w-3.5 h-3.5" /> {t("common.addQuestionButton")}
          </Button>
        </div>
        <div className="border border-slate-200 rounded-lg divide-y divide-slate-100">
          {questions.map((q, idx) => (
            <div key={idx} className="p-3 space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-bold text-slate-500 w-6 shrink-0">{idx + 1}.</span>
                <input
                  required
                  value={q.content}
                  onChange={(e) => updateQuestionContent(idx, e.target.value)}
                  placeholder={t("common.questionContentPlaceholder")}
                  className={`flex-1 ${inputClass}`}
                />
                {questions.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setQuestions((prev) => prev.filter((_, i) => i !== idx))}
                    className="text-slate-400 hover:text-rose-600 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
              <div className="flex items-center gap-2 pl-8">
                {passages.map((p, pIdx) => (
                  <button
                    key={pIdx}
                    type="button"
                    onClick={() => updateQuestionAnswer(idx, pIdx)}
                    className={`px-2.5 py-1 rounded-lg border text-[10px] font-bold transition-all ${
                      q.correctIndex === pIdx ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-500 hover:border-slate-400"
                    }`}
                  >
                    {String.fromCharCode(65 + pIdx)}. {p.name.trim() || "—"}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-200">
        <Button type="button" variant="secondary" onClick={onCancel}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" variant="primary" disabled={submitting}>
          {submitting ? t("common.creating") : t("gridQuestionBuilder.submitCreate", { count: questions.length })}
        </Button>
      </div>
    </form>
  );
}
