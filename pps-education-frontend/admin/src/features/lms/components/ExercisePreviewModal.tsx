import React, { useEffect, useState } from "react";
import { CheckCircle2 } from "lucide-react";
import Modal from "@/components/ui/Modal";
import { ExerciseQuestionResponse, ExerciseResponse, QuestionResponse, getExamQuestion, listExerciseQuestions } from "../api";

const questionTypeLabels: Record<QuestionResponse["questionType"], string> = {
  MULTIPLE_CHOICE: "Trắc nghiệm 1 đáp án",
  MULTIPLE_ANSWER: "Trắc nghiệm nhiều đáp án",
  TRUE_FALSE: "Đúng — Sai",
  ESSAY: "Tự luận",
  SPEAKING: "Nói (ghi âm)",
  FILL_IN_BLANK: "Điền vào chỗ trống"
};

const choiceTypes: QuestionResponse["questionType"][] = ["MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"];

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
      title={`Xem trước đề — ${exercise.title}`}
      description="Bố cục như học viên sẽ thấy, có tô sáng đáp án đúng để bạn tự đối chiếu."
      size="lg"
    >
      <div className="space-y-3 text-xs text-slate-600 mb-4 pb-3 border-b border-slate-100">
        <div className="flex gap-4">
          <span>
            Tổng điểm: <strong>{exercise.totalPoints}</strong>
          </span>
          {exercise.timeLimitMinutes != null && (
            <span>
              Thời gian: <strong>{exercise.timeLimitMinutes} phút</strong>
            </span>
          )}
          <span>
            Hiện đáp án cho học viên sau nộp: <strong>{exercise.showCorrectAnswers ? "Có" : "Không"}</strong>
          </span>
        </div>
      </div>

      {loading ? (
        <p className="text-xs text-slate-500 text-center py-6">Đang tải đề...</p>
      ) : exerciseQuestions.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-6">Đề này chưa gắn câu hỏi nào.</p>
      ) : (
        <div className="space-y-5 max-h-[60vh] overflow-y-auto pr-1">
          {exerciseQuestions.map((eq, index) => {
            const q = fullQuestions[eq.questionId];
            return (
              <div key={eq.id} className="border border-slate-200 rounded-xl p-4">
                <div className="flex items-start justify-between gap-3 mb-2">
                  <p className="text-sm font-bold text-slate-800">
                    Câu {index + 1}. {q?.content ?? eq.questionContent}
                  </p>
                  <span className="text-[10px] font-bold uppercase text-slate-400 shrink-0">{eq.points} đ</span>
                </div>
                <p className="text-[10px] text-slate-400 uppercase font-bold mb-2">{questionTypeLabels[eq.questionType]}</p>

                {q?.imageUrl && <img src={q.imageUrl} alt="" className="max-h-40 rounded-lg mb-2" />}
                {q?.audioUrl && <audio controls src={q.audioUrl} className="mb-2 w-full" />}
                {q?.referencePassage && <p className="text-xs text-slate-500 bg-slate-50 p-2 rounded-lg mb-2">{q.referencePassage}</p>}

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
                        <span>{c.content}</span>
                      </div>
                    ))}
                  </div>
                )}

                {eq.questionType === "SPEAKING" && (
                  <p className="text-[11px] text-slate-400 italic">Học viên ghi âm trả lời — chấm tay ở Hàng chờ chấm bài.</p>
                )}
                {eq.questionType === "ESSAY" && (
                  <p className="text-[11px] text-slate-400 italic">Học viên trả lời tự luận — chấm tay ở Hàng chờ chấm bài.</p>
                )}

                {q?.explanation && <p className="text-[11px] text-slate-500 mt-2 italic">Giải thích: {q.explanation}</p>}
              </div>
            );
          })}
        </div>
      )}
    </Modal>
  );
}
