import React, { useState } from "react";
import { Check, Headphones, Image as ImageIcon, Plus, Volume2, PenLine, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import FileUploadField from "@/components/ui/FileUploadField";
import { QuestionResponse, QuestionType, createExamQuestion, uploadMedia } from "../api";

const inputClass = "w-full bg-white border border-slate-200 text-xs px-3.5 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red";
const labelClass = "block font-bold text-slate-700 mb-1 uppercase tracking-wider text-[10px]";

type ListeningSubKind = "VOICE_MULTIPLE_CHOICE" | "VOICE_PICTURE_CHOICE" | "LISTENING_AUDIO_SUBMISSION" | "LISTENING_FILL_IN_BLANK";

const subKindMeta: Record<ListeningSubKind, { icon: typeof Volume2; activeClass: string; iconClass: string }> = {
  VOICE_MULTIPLE_CHOICE: { icon: Volume2, activeClass: "bg-blue-50 border-blue-400 text-blue-800 ring-1 ring-blue-300", iconClass: "text-blue-600" },
  VOICE_PICTURE_CHOICE: { icon: ImageIcon, activeClass: "bg-indigo-50 border-indigo-400 text-indigo-800 ring-1 ring-indigo-300", iconClass: "text-indigo-600" },
  LISTENING_AUDIO_SUBMISSION: { icon: Headphones, activeClass: "bg-sky-50 border-sky-400 text-sky-800 ring-1 ring-sky-300", iconClass: "text-sky-600" },
  LISTENING_FILL_IN_BLANK: { icon: PenLine, activeClass: "bg-violet-50 border-violet-400 text-violet-800 ring-1 ring-violet-300", iconClass: "text-violet-600" }
};

interface QuestionRow {
  content: string;
  // Chỉ dùng khi subKind=VOICE_MULTIPLE_CHOICE/VOICE_PICTURE_CHOICE.
  options: string[];
  // V143 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23) — chỉ dùng khi
  // subKind=VOICE_PICTURE_CHOICE, song song với `options` (dùng làm chú thích tùy chọn ở kind này).
  imageUrls: string[];
  correctIndex: number;
  // Chỉ dùng khi subKind=LISTENING_FILL_IN_BLANK.
  correctAnswerText: string;
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — nội dung gợi ý tapescript (mở khóa
  // sau khi học sinh nghe hết audio đủ số lần cấu hình, xem ListeningHintService) — QuestionEditorForm
  // đã có field này (chung cho mọi kind), ListeningGroupBuilder trước đây thiếu vì soạn nhiều câu/lần.
  explanation: string;
}

const emptyQuestionRow = (): QuestionRow => ({
  content: "",
  options: ["", "", "", ""],
  imageUrls: ["", "", "", ""],
  correctIndex: 0,
  correctAnswerText: "",
  explanation: ""
});

/**
 * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — cho phép thêm/bớt số đáp án mỗi câu (mặc định 4,
 * không phải mọi câu đều cần đúng 4 — VD chỉ cần 3), mirror MAX_CHOICES/handleAddOption ở
 * QuestionEditorForm.tsx. Áp dụng riêng cho từng câu (mỗi câu trong danh sách tự thêm/bớt độc lập).
 */
const MIN_OPTIONS = 2;
const MAX_OPTIONS = 8;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — GV nước ngoài cần 1 file audio (1
 * bài nghe) dùng chung cho NHIỀU câu hỏi, thay vì luôn 1-audio-1-câu như QuestionEditorForm. Về bản
 * chất là N Question cùng skill=LISTENING + cùng audioUrl + cùng groupKey (Portal/Admin gộp hiển thị
 * theo groupKey — xem TakeExerciseModal.tsx), tách khỏi QuestionEditorForm theo SRP giống hệt cách
 * GridQuestionBuilder.tsx đã làm cho "Đọc hiểu — Lưới" (GV Việt Nam). Trong 3 loại con, chỉ Trắc
 * nghiệm Voice có bộ 4 đáp án/câu, Nghe điền từ có đáp án đúng để tự chấm, Nghe & nộp audio không có
 * đáp án mẫu (chấm tay ở "Hàng chờ chấm bài") — khớp đúng hành vi của 3 kind tương ứng bên
 * QuestionEditorForm.tsx.
 */
export default function ListeningGroupBuilder({
  examId,
  onCreated,
  onCancel
}: {
  examId: number;
  onCreated: (questions: QuestionResponse[]) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation("lms-question-authoring");
  const [subKind, setSubKind] = useState<ListeningSubKind>("VOICE_MULTIPLE_CHOICE");
  const [audioUrl, setAudioUrl] = useState("");
  const [transcript, setTranscript] = useState("");
  const [questions, setQuestions] = useState<QuestionRow[]>([emptyQuestionRow(), emptyQuestionRow()]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Đổi loại câu hỏi con: reset danh sách câu hỏi để không lẫn field của loại cũ (đáp án/option). */
  const handleSelectSubKind = (value: ListeningSubKind) => {
    setSubKind(value);
    setQuestions([emptyQuestionRow(), emptyQuestionRow()]);
  };

  const updateContent = (idx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, content: value } : q)));
  };
  const updateOption = (idx: number, optIdx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, options: q.options.map((o, oi) => (oi === optIdx ? value : o)) } : q)));
  };
  const updateImageUrl = (idx: number, optIdx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, imageUrls: q.imageUrls.map((u, oi) => (oi === optIdx ? value : u)) } : q)));
  };
  const updateCorrectIndex = (idx: number, correctIndex: number) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, correctIndex } : q)));
  };
  const updateCorrectAnswerText = (idx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, correctAnswerText: value } : q)));
  };
  const updateExplanation = (idx: number, value: string) => {
    setQuestions((prev) => prev.map((q, i) => (i === idx ? { ...q, explanation: value } : q)));
  };
  const addOption = (idx: number) => {
    setQuestions((prev) =>
      prev.map((q, i) => (i === idx && q.options.length < MAX_OPTIONS ? { ...q, options: [...q.options, ""], imageUrls: [...q.imageUrls, ""] } : q))
    );
  };
  const removeOption = (idx: number, optIdx: number) => {
    setQuestions((prev) =>
      prev.map((q, i) =>
        i === idx && q.options.length > MIN_OPTIONS
          ? {
              ...q,
              options: q.options.filter((_, oi) => oi !== optIdx),
              imageUrls: q.imageUrls.filter((_, oi) => oi !== optIdx),
              correctIndex: q.correctIndex === optIdx ? 0 : q.correctIndex > optIdx ? q.correctIndex - 1 : q.correctIndex
            }
          : q
      )
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!audioUrl.trim()) {
      setError(t("listeningGroupBuilder.errors.audioRequired"));
      return;
    }
    if (questions.length === 0) {
      setError(t("listeningGroupBuilder.errors.needAtLeastOne"));
      return;
    }
    if (questions.some((q) => !q.content.trim())) {
      setError(t("listeningGroupBuilder.errors.fillAllContent"));
      return;
    }
    if (subKind === "VOICE_MULTIPLE_CHOICE" && questions.some((q) => q.options.some((o) => !o.trim()))) {
      setError(t("listeningGroupBuilder.errors.fillAllOptions"));
      return;
    }
    if (subKind === "VOICE_PICTURE_CHOICE" && questions.some((q) => q.imageUrls.some((u) => !u.trim()))) {
      setError(t("listeningGroupBuilder.errors.fillAllImages"));
      return;
    }
    if (subKind === "LISTENING_FILL_IN_BLANK" && questions.some((q) => !q.correctAnswerText.trim())) {
      setError(t("listeningGroupBuilder.errors.fillAllCorrectAnswers"));
      return;
    }

    const questionType: QuestionType =
      subKind === "VOICE_MULTIPLE_CHOICE" || subKind === "VOICE_PICTURE_CHOICE" ? "MULTIPLE_CHOICE" : subKind === "LISTENING_AUDIO_SUBMISSION" ? "SPEAKING" : "FILL_IN_BLANK";
    const groupKey = `listening-${Date.now()}`;
    const referencePassage = transcript.trim() || undefined;

    setSubmitting(true);
    const created: QuestionResponse[] = [];
    try {
      for (const q of questions) {
        const result = await createExamQuestion(examId, {
          questionType,
          skill: "LISTENING",
          difficulty: "MEDIUM",
          content: q.content.trim(),
          audioUrl: audioUrl.trim(),
          referencePassage,
          correctAnswerText: subKind === "LISTENING_FILL_IN_BLANK" ? q.correctAnswerText.trim() : undefined,
          explanation: q.explanation.trim() || undefined,
          groupKey,
          defaultPoints: 1,
          choices:
            subKind === "VOICE_MULTIPLE_CHOICE"
              ? q.options.map((content_, i) => ({ choiceLabel: String.fromCharCode(65 + i), content: content_.trim(), isCorrect: i === q.correctIndex, displayOrder: i + 1 }))
              : subKind === "VOICE_PICTURE_CHOICE"
                ? q.options.map((content_, i) => ({
                    choiceLabel: String.fromCharCode(65 + i),
                    content: content_.trim() || String.fromCharCode(65 + i),
                    imageUrl: q.imageUrls[i]?.trim() || undefined,
                    isCorrect: i === q.correctIndex,
                    displayOrder: i + 1
                  }))
                : undefined
        });
        created.push(result);
      }
      onCreated(created);
    } catch (err) {
      setError(
        created.length > 0
          ? t("listeningGroupBuilder.errors.partialFailure", { created: created.length, total: questions.length })
          : err instanceof ApiError
            ? err.message
            : t("listeningGroupBuilder.errors.createFailed")
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4 text-xs">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div>
        <label className={labelClass}>{t("listeningGroupBuilder.subKindLabel")}</label>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
          {(Object.entries(subKindMeta) as [ListeningSubKind, (typeof subKindMeta)[ListeningSubKind]][]).map(([value, meta]) => {
            const Icon = meta.icon;
            const active = subKind === value;
            return (
              <button
                key={value}
                type="button"
                onClick={() => handleSelectSubKind(value)}
                className={`p-2.5 rounded-xl border font-bold flex flex-col items-center gap-1 transition-all ${
                  active ? `${meta.activeClass} shadow-xs` : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
                }`}
              >
                <Icon className={`w-4 h-4 ${active ? "" : meta.iconClass}`} />
                <span>{t(`questionKind.${value}`)}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 space-y-3">
        <div className="flex items-center gap-1 text-slate-700 font-bold uppercase tracking-wider text-[9px]">
          <Headphones className="w-4 h-4 text-slate-500" />
          <span>{t("listeningGroupBuilder.audioSectionTitle")}</span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[9px] uppercase">{t("common.audioFileLabel")}</label>
            <FileUploadField value={audioUrl} onChange={setAudioUrl} onUpload={(file) => uploadMedia(file, "LMS_QUESTION")} accept="audio/*" placeholder={t("common.chooseAudioFile")} />
          </div>
          <div>
            <label className="block font-bold text-slate-600 mb-1 text-[9px] uppercase">{t("common.transcriptLabel")}</label>
            <input value={transcript} onChange={(e) => setTranscript(e.target.value)} placeholder={t("common.transcriptPlaceholder")} className={inputClass} />
          </div>
        </div>
      </div>

      <div className="space-y-2">
        <span className="font-bold text-slate-700 uppercase tracking-wider text-[9px] block">{t("listeningGroupBuilder.questionsSectionTitle")}</span>
        <div className="border border-slate-200 rounded-lg divide-y divide-slate-100">
          {questions.map((q, idx) => (
            <div key={idx} className="p-3 space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-bold text-slate-500 w-6 shrink-0">{idx + 1}.</span>
                <input
                  required
                  value={q.content}
                  onChange={(e) => updateContent(idx, e.target.value)}
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

              {subKind === "VOICE_MULTIPLE_CHOICE" && (
                <div className="pl-8 space-y-1.5">
                  {q.options.map((opt, optIdx) => (
                    <div key={optIdx} className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => updateCorrectIndex(idx, optIdx)}
                        className={`w-5 h-5 rounded-full border flex items-center justify-center font-bold shrink-0 text-[9px] transition-all ${
                          q.correctIndex === optIdx ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-400 hover:border-slate-400"
                        }`}
                      >
                        {q.correctIndex === optIdx ? <Check className="w-3 h-3 stroke-[3]" /> : String.fromCharCode(65 + optIdx)}
                      </button>
                      <input
                        required
                        value={opt}
                        onChange={(e) => updateOption(idx, optIdx, e.target.value)}
                        placeholder={t("common.answerOptionPlaceholder", { letter: String.fromCharCode(65 + optIdx) })}
                        className={`flex-1 ${inputClass}`}
                      />
                      {q.options.length > MIN_OPTIONS && (
                        <button type="button" onClick={() => removeOption(idx, optIdx)} className="text-slate-400 hover:text-rose-600 shrink-0">
                          <X className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
                  ))}
                  {q.options.length < MAX_OPTIONS && (
                    <button type="button" onClick={() => addOption(idx)} className="text-[10px] font-bold text-brand-red hover:underline">
                      {t("questionEditorForm.addOption")}
                    </button>
                  )}
                </div>
              )}

              {subKind === "VOICE_PICTURE_CHOICE" && (
                <div className="pl-8 space-y-1.5">
                  {q.imageUrls.map((url, optIdx) => (
                    <div key={optIdx} className="flex items-start gap-2">
                      <button
                        type="button"
                        onClick={() => updateCorrectIndex(idx, optIdx)}
                        className={`w-5 h-5 rounded-full border flex items-center justify-center font-bold shrink-0 text-[9px] transition-all mt-1 ${
                          q.correctIndex === optIdx ? "bg-emerald-500 border-emerald-500 text-white" : "bg-white border-slate-300 text-slate-400 hover:border-slate-400"
                        }`}
                      >
                        {q.correctIndex === optIdx ? <Check className="w-3 h-3 stroke-[3]" /> : String.fromCharCode(65 + optIdx)}
                      </button>
                      <div className="flex-1 space-y-1">
                        <FileUploadField
                          value={url}
                          onChange={(v) => updateImageUrl(idx, optIdx, v)}
                          onUpload={(file) => uploadMedia(file, "LMS_QUESTION")}
                          accept="image/*"
                          placeholder={t("questionEditorForm.choiceImagePlaceholder", { letter: String.fromCharCode(65 + optIdx) })}
                        />
                        <input
                          value={q.options[optIdx]}
                          onChange={(e) => updateOption(idx, optIdx, e.target.value)}
                          placeholder={t("questionEditorForm.choiceCaptionPlaceholder")}
                          className={`w-full ${inputClass}`}
                        />
                      </div>
                      {q.imageUrls.length > MIN_OPTIONS && (
                        <button type="button" onClick={() => removeOption(idx, optIdx)} className="text-slate-400 hover:text-rose-600 shrink-0 mt-1">
                          <X className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
                  ))}
                  {q.imageUrls.length < MAX_OPTIONS && (
                    <button type="button" onClick={() => addOption(idx)} className="text-[10px] font-bold text-brand-red hover:underline">
                      {t("questionEditorForm.addOption")}
                    </button>
                  )}
                </div>
              )}

              {subKind === "LISTENING_FILL_IN_BLANK" && (
                <div className="pl-8">
                  <input
                    required
                    value={q.correctAnswerText}
                    onChange={(e) => updateCorrectAnswerText(idx, e.target.value)}
                    placeholder={t("listeningGroupBuilder.correctAnswerPlaceholder")}
                    className={inputClass}
                  />
                </div>
              )}

              <div className="pl-8">
                <input
                  value={q.explanation}
                  onChange={(e) => updateExplanation(idx, e.target.value)}
                  placeholder={t("listeningGroupBuilder.explanationPlaceholder")}
                  className={inputClass}
                />
              </div>
            </div>
          ))}
        </div>
        {subKind === "LISTENING_AUDIO_SUBMISSION" && (
          <p className="text-[9px] text-slate-400">{t("listeningGroupBuilder.audioSubmissionHint")}</p>
        )}
      </div>

      <div className="flex items-center justify-between gap-2 pt-2 border-t border-slate-200">
        {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — đặt cạnh footer thay vì đầu
            danh sách để không phải cuộn lên trên khi đã có nhiều câu hỏi. */}
        <Button type="button" variant="secondary" size="sm" onClick={() => setQuestions((prev) => [...prev, emptyQuestionRow()])}>
          <Plus className="w-3.5 h-3.5" /> {t("common.addQuestionButton")}
        </Button>
        <div className="flex items-center gap-2">
          <Button type="button" variant="secondary" onClick={onCancel}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("common.creating") : t("listeningGroupBuilder.submitCreate", { count: questions.length })}
          </Button>
        </div>
      </div>
    </form>
  );
}
