import React, { useEffect, useState } from "react";
import { ApiError } from "@/lib/apiClient";
import { StudentAnswerRow, getAttemptAnswers } from "@/features/academic/api";
import { ExerciseQuestionChoiceResponse, ExerciseQuestionResponse, listExerciseQuestions } from "@/features/lms/api";

function formatChoiceIds(ids: number[] | null | undefined, choices: ExerciseQuestionChoiceResponse[] | undefined): string | null {
  if (!ids || ids.length === 0) return null;
  return ids
    .map((id) => {
      const choice = choices?.find((c) => c.id === id);
      return choice ? `${choice.choiceLabel}. ${choice.content}` : `#${id}`;
    })
    .join("; ");
}

/**
 * Chi tiết câu trả lời của 1 lượt làm BTVN — dùng chung dữ liệu với màn chấm điểm của Giáo viên
 * (AssignmentStatsDetailPage) qua cùng 2 API: getAttemptAnswers (/answers/for-grading) + danh sách
 * câu hỏi của Bài (kèm nội dung/đáp án) để tra theo questionId. Mặc định chỉ hiện câu SAI (đúng mục
 * tiêu "xem được các câu sai" ở tab Bài tập về nhà), có nút bật xem toàn bộ.
 */
export default function HomeworkAttemptDetail({ attemptId, exerciseId }: { attemptId: number; exerciseId: number }) {
  const [answers, setAnswers] = useState<StudentAnswerRow[]>([]);
  const [questionsById, setQuestionsById] = useState<Map<number, ExerciseQuestionResponse>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([getAttemptAnswers(attemptId), listExerciseQuestions(exerciseId)])
      .then(([answerRows, questions]) => {
        if (cancelled) return;
        setAnswers(answerRows);
        setQuestionsById(new Map(questions.map((q) => [q.questionId, q])));
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Không tải được chi tiết bài làm.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [attemptId, exerciseId]);

  const wrongCount = answers.filter((a) => a.isCorrect === false).length;
  const visibleAnswers = showAll ? answers : answers.filter((a) => a.isCorrect === false);

  if (loading) return <div className="text-center py-4 text-xs text-slate-400">Đang tải bài làm...</div>;
  if (error) return <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>;
  if (answers.length === 0) return <div className="text-center py-4 text-xs text-slate-400">Chưa có dữ liệu câu trả lời.</div>;

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs text-slate-500">
          Sai <span className="font-semibold text-rose-600">{wrongCount}</span>/{answers.length} câu
        </span>
        <button type="button" onClick={() => setShowAll((v) => !v)} className="text-xs text-brand-orange font-semibold hover:underline">
          {showAll ? "Chỉ xem câu sai" : "Xem toàn bộ câu trả lời"}
        </button>
      </div>
      {visibleAnswers.length === 0 ? (
        <div className="text-center py-4 text-xs text-emerald-600">Không có câu nào làm sai 🎉</div>
      ) : (
        <div className="border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-xs">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left p-2 border-b border-slate-200">Câu hỏi</th>
                <th className="text-left p-2 border-b border-slate-200">Học sinh trả lời</th>
                <th className="text-left p-2 border-b border-slate-200">Đáp án đúng</th>
                <th className="text-center p-2 border-b border-slate-200 w-14">Kết quả</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {visibleAnswers.map((answer) => {
                const question = questionsById.get(answer.questionId);
                const choices = question?.choices;
                const selectedLabel = formatChoiceIds(answer.selectedChoiceIds, choices);
                const correctLabel = formatChoiceIds(answer.correctChoiceIds, choices);
                return (
                  <tr key={answer.id}>
                    <td className="p-2 text-slate-700 break-words max-w-[240px]">{question?.questionContent ?? "—"}</td>
                    <td className="p-2 text-slate-600 break-words max-w-[200px]">
                      {answer.answerText || selectedLabel || <span className="text-slate-400 italic">Không trả lời</span>}
                    </td>
                    <td className="p-2 text-slate-600 break-words max-w-[200px]">
                      {answer.correctAnswerText || correctLabel || <span className="text-slate-400">—</span>}
                      {answer.explanation && <p className="text-[10px] text-slate-500 mt-1 italic">{answer.explanation}</p>}
                    </td>
                    <td className="text-center p-2">
                      {answer.isCorrect == null ? (
                        <span className="text-slate-400">—</span>
                      ) : answer.isCorrect ? (
                        <span className="text-emerald-600 font-bold">✓</span>
                      ) : (
                        <span className="text-rose-600 font-bold">✗</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
