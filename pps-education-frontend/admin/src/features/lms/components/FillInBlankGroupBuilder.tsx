import React, { useState } from "react";
import { Plus, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import FileUploadField from "@/components/ui/FileUploadField";
import { QuestionResponse, createExamQuestion, uploadMedia } from "../api";

const inputClass = "w-full bg-white border border-slate-200 text-xs px-3.5 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red";
const labelClass = "block font-bold text-slate-700 mb-1 uppercase tracking-wider text-[10px]";

interface QuestionRow {
  content: string;
  correctAnswerText: string;
  // Bổ sung 2026-08-28 (đã xác nhận với người dùng) — ảnh minh họa RIÊNG từng câu (khớp dạng "Complete
  // each sentence with this/that/these/those", mỗi câu 1 ảnh khác nhau) — tùy chọn, để trống = không ảnh.
  imageUrl: string;
  explanation: string;
}

const emptyQuestionRow = (): QuestionRow => ({ content: "", correctAnswerText: "", imageUrl: "", explanation: "" });

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — 1 nhóm điền từ (referencePassage/
 * wordBox dùng chung + groupKey) đã có sẵn trong Bài đang soạn, dùng để GV chọn "thêm câu vào nhóm
 * này" thay vì luôn phải tạo nhóm mới (mirror ExistingListeningGroup ở ListeningGroupBuilder.tsx —
 * cùng gốc bug: groupKey trước đây chỉ tự sinh bằng Date.now(), không cách nào lấy lại đúng nhóm cũ).
 */
export interface ExistingFillInBlankGroup {
  groupKey: string;
  referencePassage: string | null;
  wordBox: string[];
  questionCount: number;
  /** Nội dung câu hỏi đầu tiên trong nhóm (rút gọn), giúp GV nhận ra đúng nhóm khi có nhiều nhóm điền từ trong 1 Bài. */
  sampleContent: string;
}

/**
 * Bổ sung 2026-08-28 (đã xác nhận với người dùng — "Cách B": mỗi câu trong 1 Ex. là 1 Question
 * FILL_IN_BLANK riêng, tự có "Câu N." + điểm riêng, thay vì gộp hết vào 1 Question nhiều chỗ trống
 * như WORD_BANK trước đó). Về bản chất là N Question FILL_IN_BLANK cùng 1 groupKey (+ referencePassage
 * dùng chung nếu có ghi chú/hộp từ vựng chung) — mirror đúng cách ClozeQuestionBuilder/GridQuestionBuilder
 * đã làm cho Đọc hiểu, chỉ đổi "đáp án trắc nghiệm" thành "đáp án tự do do hệ thống tự chấm exact-match"
 * (khớp QuestionEditorForm kind FILL_IN_BLANK / FILL_IN_BLANK_PICTURE cho câu đơn). Portal đã tự hiện
 * đúng dạng "N câu FILL_IN_BLANK trong 1 nhóm" từ trước (xây cho ListeningGroupBuilder — xem
 * TakeExerciseModal.tsx isFillInBlankRow trong khối "grid"), không cần sửa thêm phía học sinh.
 *
 * Đánh đổi đã xác nhận với người dùng: KHÔNG còn "khung từ dùng chung, mỗi từ chọn 1 lần rồi loại khỏi
 * dropdown câu khác" như WORD_BANK gốc — mỗi câu chấm độc lập theo correctAnswerText của chính nó.
 */
export default function FillInBlankGroupBuilder({
  examId,
  existingGroups,
  onCreated,
  onCancel
}: {
  examId: number;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — các nhóm điền từ đã có sẵn trong Bài đang soạn, cho phép chọn "thêm câu vào nhóm này". Rỗng/undefined = Bài chưa có nhóm điền từ nào, chỉ tạo mới được. */
  existingGroups?: ExistingFillInBlankGroup[];
  onCreated: (questions: QuestionResponse[]) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation("lms-question-authoring");
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — "new" = tạo nhóm điền từ mới (hành
   * vi cũ, groupKey tự sinh bằng Date.now() lúc submit); 1 groupKey cụ thể = đang thêm câu vào ĐÚNG
   * nhóm đó (sharedNote/sharedWordBox khoá theo nhóm đã chọn, không cho sửa lại vì phải khớp câu cũ).
   */
  const [appendTarget, setAppendTarget] = useState<string>("new");
  const [sharedNote, setSharedNote] = useState("");
  // Bổ sung 2026-08-28 (đã xác nhận với người dùng) — hộp từ vựng THAM KHẢO (tĩnh, không phải đáp án
  // đúng) hiện dạng lưới ô chữ 1 lần phía trên cả nhóm câu, khớp hình thức "khung từ" đề giấy gốc
  // (Ex.1) — tách khỏi sharedNote (văn bản hướng dẫn tự do) để render thành lưới ô riêng biệt.
  const [sharedWordBox, setSharedWordBox] = useState<string[]>([]);
  const [questions, setQuestions] = useState<QuestionRow[]>([emptyQuestionRow(), emptyQuestionRow()]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isAppending = appendTarget !== "new";

  /** Chọn "thêm vào nhóm có sẵn" — khoá sharedNote/sharedWordBox theo đúng nhóm, chỉ còn soạn thêm câu mới. "new" quay lại tạo nhóm mới, trả các field về rỗng. */
  const handleSelectAppendTarget = (value: string) => {
    setAppendTarget(value);
    const group = existingGroups?.find((g) => g.groupKey === value);
    if (group) {
      setSharedNote(group.referencePassage ?? "");
      setSharedWordBox(group.wordBox);
    } else {
      setSharedNote("");
      setSharedWordBox([]);
    }
    setQuestions([emptyQuestionRow(), emptyQuestionRow()]);
  };

  const updateContent = (idx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, content: value } : q)));
  };
  const updateCorrectAnswerText = (idx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, correctAnswerText: value } : q)));
  };
  const updateImageUrl = (idx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, imageUrl: value } : q)));
  };
  const updateExplanation = (idx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, explanation: value } : q)));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (questions.length === 0) {
      setError(t("fillInBlankGroupBuilder.errors.needAtLeastOne"));
      return;
    }
    if (questions.some((q) => !q.content.trim())) {
      setError(t("fillInBlankGroupBuilder.errors.fillAllContent"));
      return;
    }
    if (questions.some((q) => !q.correctAnswerText.trim())) {
      setError(t("fillInBlankGroupBuilder.errors.fillAllCorrectAnswers"));
      return;
    }

    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — đang thêm vào nhóm có sẵn thì
    // dùng ĐÚNG groupKey cũ (câu mới sẽ gộp chung nhóm với các câu đã có) thay vì luôn sinh groupKey mới.
    const groupKey = isAppending ? appendTarget : `fillblank-${Date.now()}`;
    const referencePassage = sharedNote.trim() || undefined;
    const trimmedWordBox = sharedWordBox.map((w) => w.trim()).filter(Boolean);
    const structuredContent = trimmedWordBox.length > 0 ? { wordBox: trimmedWordBox } : undefined;

    setSubmitting(true);
    const created: QuestionResponse[] = [];
    try {
      for (const q of questions) {
        const result = await createExamQuestion(examId, {
          questionType: "FILL_IN_BLANK",
          difficulty: "MEDIUM",
          content: q.content.trim(),
          imageUrl: q.imageUrl.trim() || undefined,
          referencePassage,
          correctAnswerText: q.correctAnswerText.trim(),
          explanation: q.explanation.trim() || undefined,
          structuredContent,
          groupKey,
          defaultPoints: 1
        });
        created.push(result);
      }
      onCreated(created);
    } catch (err) {
      setError(
        created.length > 0
          ? t("fillInBlankGroupBuilder.errors.partialFailure", { created: created.length, total: questions.length })
          : err instanceof ApiError
            ? err.message
            : t("fillInBlankGroupBuilder.errors.createFailed")
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4 text-xs">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — chỉ hiện khi Bài đang soạn đã có sẵn ≥1 nhóm điền từ (existingGroups), cho GV chọn thêm câu vào nhóm cũ thay vì luôn tạo nhóm mới. */}
      {existingGroups && existingGroups.length > 0 && (
        <div>
          <label className={labelClass}>{t("fillInBlankGroupBuilder.appendTargetLabel")}</label>
          <select value={appendTarget} onChange={(e) => handleSelectAppendTarget(e.target.value)} className={inputClass}>
            <option value="new">{t("fillInBlankGroupBuilder.appendTargetNew")}</option>
            {existingGroups.map((g) => (
              <option key={g.groupKey} value={g.groupKey}>
                {t("fillInBlankGroupBuilder.appendTargetOption", { count: g.questionCount, content: g.sampleContent })}
              </option>
            ))}
          </select>
          {isAppending && <p className="text-[10px] text-slate-400 mt-1">{t("fillInBlankGroupBuilder.appendTargetHint")}</p>}
        </div>
      )}

      <div>
        <label className={labelClass}>{t("fillInBlankGroupBuilder.sharedNoteLabel")}</label>
        <p className="text-[9px] text-slate-400 mb-1">{t("fillInBlankGroupBuilder.sharedNoteHint")}</p>
        <textarea
          value={sharedNote}
          onChange={(e) => setSharedNote(e.target.value)}
          placeholder={t("fillInBlankGroupBuilder.sharedNotePlaceholder")}
          rows={2}
          disabled={isAppending}
          className={`${inputClass} disabled:opacity-60 disabled:cursor-not-allowed`}
        />
      </div>

      <div className="bg-amber-50/40 p-4 rounded-xl border border-amber-200 space-y-2">
        <label className={labelClass}>{t("fillInBlankGroupBuilder.wordBoxLabel")}</label>
        <p className="text-[9px] text-slate-400 mb-1">{t("fillInBlankGroupBuilder.wordBoxHint")}</p>
        <div className="flex flex-wrap gap-1.5">
          {sharedWordBox.map((w, idx) => (
            <div key={idx} className="flex items-center gap-1 bg-white border border-slate-200 rounded-lg pl-2.5 pr-1 py-1">
              <input
                value={w}
                onChange={(e) => setSharedWordBox((prev) => prev.map((x, i) => (i === idx ? e.target.value : x)))}
                placeholder={t("fillInBlankGroupBuilder.wordBoxItemPlaceholder")}
                disabled={isAppending}
                className="text-xs w-24 focus:outline-none disabled:opacity-60"
              />
              <button
                type="button"
                disabled={isAppending}
                onClick={() => setSharedWordBox((prev) => prev.filter((_, i) => i !== idx))}
                className="text-slate-400 hover:text-rose-600 shrink-0 disabled:opacity-40 disabled:hover:text-slate-400"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          ))}
        </div>
        <Button type="button" variant="secondary" size="sm" disabled={isAppending} onClick={() => setSharedWordBox((prev) => [...prev, ""])}>
          {t("fillInBlankGroupBuilder.addWordBoxItem")}
        </Button>
      </div>

      <div className="space-y-2">
        <span className="font-bold text-slate-700 uppercase tracking-wider text-[9px] block">{t("fillInBlankGroupBuilder.questionsSectionTitle")}</span>
        <div className="border border-slate-200 rounded-lg divide-y divide-slate-100">
          {questions.map((q, idx) => (
            <div key={idx} className="p-3 space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-bold text-slate-500 w-6 shrink-0">{idx + 1}.</span>
                <input
                  required
                  value={q.content}
                  onChange={(e) => updateContent(idx, e.target.value)}
                  placeholder={t("fillInBlankGroupBuilder.contentPlaceholder")}
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
              <div className="pl-8 space-y-1.5">
                <input
                  required
                  value={q.correctAnswerText}
                  onChange={(e) => updateCorrectAnswerText(idx, e.target.value)}
                  placeholder={t("fillInBlankGroupBuilder.correctAnswerPlaceholder")}
                  className={inputClass}
                />
                <FileUploadField
                  value={q.imageUrl}
                  onChange={(v) => updateImageUrl(idx, v)}
                  onUpload={(file) => uploadMedia(file, "LMS_QUESTION")}
                  accept="image/*"
                  placeholder={t("fillInBlankGroupBuilder.imagePlaceholder")}
                />
                <input
                  value={q.explanation}
                  onChange={(e) => updateExplanation(idx, e.target.value)}
                  placeholder={t("fillInBlankGroupBuilder.explanationPlaceholder")}
                  className={inputClass}
                />
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="flex items-center justify-between gap-2 pt-2 border-t border-slate-200">
        <Button type="button" variant="secondary" size="sm" onClick={() => setQuestions((prev) => [...prev, emptyQuestionRow()])}>
          <Plus className="w-3.5 h-3.5" /> {t("common.addQuestionButton")}
        </Button>
        <div className="flex items-center gap-2">
          <Button type="button" variant="secondary" onClick={onCancel}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting
              ? t("common.creating")
              : isAppending
                ? t("fillInBlankGroupBuilder.submitAppend", { count: questions.length })
                : t("fillInBlankGroupBuilder.submitCreate", { count: questions.length })}
          </Button>
        </div>
      </div>
    </form>
  );
}
