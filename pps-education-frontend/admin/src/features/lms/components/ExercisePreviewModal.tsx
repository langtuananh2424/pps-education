import React, { useEffect, useState } from "react";
import { CheckCircle2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import Modal from "@/components/ui/Modal";
import { ExerciseQuestionResponse, ExerciseResponse, QuestionResponse, getExamQuestion, listExerciseQuestions } from "../api";

const choiceTypes: QuestionResponse["questionType"][] = ["MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"];

/** Bổ sung 2026-08-28 — chia mảng thành các hàng cố định `size` phần tử, dùng để dựng bảng hộp từ vựng (wordBox). */
function chunkArray<T>(items: T[], size: number): T[][] {
  const rows: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    rows.push(items.slice(i, i + size));
  }
  return rows;
}

interface ExercisePreviewModalProps {
  exercise: ExerciseResponse;
  onClose: () => void;
}

/**
 * Xem lại đề đúng bố cục học viên sẽ thấy (câu hỏi + lựa chọn theo thứ tự
 * displayOrder), CỘNG THÊM đáp án đúng được tô sáng — chỉ phục vụ GV tự đối
 * chiếu trước khi giao/sau khi giao, KHÔNG phải endpoint học viên gọi được
 * (xem ghi chú bảo mật ở lms/api.ts#getQuestion).
 */
export default function ExercisePreviewModal({ exercise, onClose }: ExercisePreviewModalProps) {
  const { t } = useTranslation("lms-question-authoring");
  const [exerciseQuestions, setExerciseQuestions] = useState<ExerciseQuestionResponse[]>([]);
  const [fullQuestions, setFullQuestions] = useState<Record<number, QuestionResponse>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    listExerciseQuestions(exercise.id)
      .then(async (eqs) => {
        const sorted = [...eqs].sort((a, b) => a.displayOrder - b.displayOrder);
        setExerciseQuestions(sorted);
        const fulls = await Promise.all(sorted.map((eq) => getExamQuestion(exercise.examId, eq.questionId).catch(() => null)));
        const map: Record<number, QuestionResponse> = {};
        fulls.forEach((q, i) => {
          if (q) map[sorted[i].questionId] = q;
        });
        setFullQuestions(map);
      })
      .finally(() => setLoading(false));
  }, [exercise.id, exercise.examId]);

  return (
    <Modal
      open
      onClose={onClose}
      title={t("exercisePreviewModal.modalTitle", { title: exercise.title })}
      description={t("exercisePreviewModal.modalDescription")}
      size="lg"
    >
      <div className="space-y-3 text-xs text-slate-600 mb-4 pb-3 border-b border-slate-100">
        <div className="flex gap-4">
          <span>
            {t("exercisePreviewModal.totalPoints")} <strong>{exercise.totalPoints}</strong>
          </span>
          {exercise.timeLimitMinutes != null && (
            <span>
              {t("exercisePreviewModal.timeLimit")} <strong>{t("exercisePreviewModal.timeLimitMinutes", { minutes: exercise.timeLimitMinutes })}</strong>
            </span>
          )}
          <span>
            {t("exercisePreviewModal.showCorrectAnswers")}{" "}
            <strong>{exercise.showCorrectAnswers ? t("exercisePreviewModal.yes") : t("exercisePreviewModal.no")}</strong>
          </span>
        </div>
      </div>

      {loading ? (
        <p className="text-xs text-slate-500 text-center py-6">{t("exercisePreviewModal.loading")}</p>
      ) : exerciseQuestions.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-6">{t("exercisePreviewModal.empty")}</p>
      ) : (
        <div className="space-y-5 max-h-[60vh] overflow-y-auto pr-1">
          {exerciseQuestions.map((eq, index) => {
            const q = fullQuestions[eq.questionId];
            return (
              <div key={eq.id} className="border border-slate-200 rounded-xl p-4">
                <div className="flex items-start justify-between gap-3 mb-2">
                  {/*
                   * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — tách nhãn "Câu N." ra dòng riêng
                   * khỏi nội dung: dạng WORD_BANK/FILL_IN_BLANK_PICTURE nhiều câu con thường tự đánh số
                   * "1. 2. 3..." ngay trong nội dung (khớp đề gốc nhiều câu/1 hộp từ) — để chung 1 dòng
                   * với "Câu N." gây nhìn nhầm thành 2 số dính nhau ("Câu 1. 1. Tom is...").
                   */}
                  <p className="text-sm font-bold text-slate-800">
                    <span className="block text-slate-500 text-xs uppercase tracking-wider mb-1">
                      {t("exercisePreviewModal.questionNumber", { index: index + 1 })}
                    </span>
                    <span className="whitespace-pre-line">{q?.content ?? eq.questionContent}</span>
                  </p>
                  <span className="text-[10px] font-bold uppercase text-slate-400 shrink-0">{eq.points} {t("common.pointsSuffix")}</span>
                </div>
                <p className="text-[10px] text-slate-400 uppercase font-bold mb-2">{t(`exercisePreviewModal.questionTypeLabels.${eq.questionType}`)}</p>

                {q?.imageUrl && <img src={q.imageUrl} alt="" className="max-h-40 rounded-lg mb-2" />}
                {q?.audioUrl && <audio controls src={q.audioUrl} className="mb-2 w-full" />}
                {q?.referencePassage && <p className="text-xs text-slate-500 bg-slate-50 p-2 rounded-lg mb-2">{q.referencePassage}</p>}
                {/* Bổ sung 2026-08-28 (đã xác nhận với người dùng) — hộp từ vựng tham khảo tĩnh (FillInBlankGroupBuilder), xem cùng khái niệm ở TakeExerciseModal.tsx. Dựng bằng <table> để khớp đúng bảng trong đề giấy gốc. */}
                {q?.structuredContent?.wordBox && q.structuredContent.wordBox.length > 0 && (
                  <table className="w-full border-collapse text-[11px] mb-2">
                    <tbody>
                      {chunkArray(q.structuredContent.wordBox, 4).map((row, ri) => (
                        <tr key={ri}>
                          {row.map((w, ci) => (
                            <td key={ci} className="border border-slate-200 text-center font-bold text-slate-700 px-1.5 py-1.5">
                              {w}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {choiceTypes.includes(eq.questionType) && q && (
                  <div className="space-y-1.5">
                    {q.choices.map((c) => (
                      <div
                        key={c.id}
                        className={`flex items-center gap-2 text-xs p-2 rounded-lg border ${
                          c.isCorrect ? "bg-emerald-50 border-emerald-200 text-emerald-700 font-bold" : "border-slate-100 text-slate-600"
                        }`}
                      >
                        {c.isCorrect && <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />}
                        <span className="font-mono text-[10px] shrink-0">{c.choiceLabel}.</span>
                        {/* V143 — dạng Listening chọn đáp án bằng hình: hiện thumbnail để GV xem đúng những gì học sinh sẽ thấy. */}
                        {c.imageUrl && <img src={c.imageUrl} alt={c.content} className="w-8 h-8 object-cover rounded shrink-0" />}
                        <span>{c.content}</span>
                      </div>
                    ))}
                  </div>
                )}

                {eq.questionType === "SPEAKING" && (
                  <p className="text-[11px] text-slate-400 italic">{t("exercisePreviewModal.speakingHint")}</p>
                )}
                {eq.questionType === "ESSAY" && (
                  <p className="text-[11px] text-slate-400 italic">{t("exercisePreviewModal.essayHint")}</p>
                )}
                {eq.questionType === "WORD_BANK" && q?.structuredContent?.blanks && (
                  <p className="text-[11px] text-emerald-700 font-bold bg-emerald-50 border border-emerald-200 rounded-lg p-2">
                    {t("exercisePreviewModal.wordBankAnswerOrder", { answers: q.structuredContent.blanks.join(" — ") })}
                  </p>
                )}
                {eq.questionType === "SENTENCE_BUILDING" && q?.structuredContent?.chunks && (
                  <p className="text-[11px] text-emerald-700 font-bold bg-emerald-50 border border-emerald-200 rounded-lg p-2">
                    {t("exercisePreviewModal.sentenceBuildingOrder", { order: q.structuredContent.chunks.join(" ") })}
                  </p>
                )}

                {q?.explanation && (
                  <p className="text-[11px] text-slate-500 mt-2 italic">{t("exercisePreviewModal.explanationPrefix", { text: q.explanation })}</p>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Modal>
  );
}
