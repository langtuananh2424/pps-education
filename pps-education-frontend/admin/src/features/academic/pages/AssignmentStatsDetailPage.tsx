import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft, CheckCircle2, ChevronDown, ChevronRight, Download, Eye, HelpCircle, ShieldAlert, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import {
  ExerciseAssignmentQuestionRow,
  ExerciseAssignmentQuestionStatsResponse,
  ExerciseAssignmentStudentStatsResponse,
  ExerciseAssignmentStudentRow,
  ExerciseAttemptRow,
  IntegritySummaryRow,
  StudentAnswerRow,
  exportExerciseAssignmentStats,
  getAttemptAnswers,
  getAttemptIntegritySummary,
  getExerciseAssignmentQuestionStats,
  getExerciseAssignmentStudentStats,
  listStudentExerciseAttempts,
  selectAttemptForGrading
} from "../api";
import { ExerciseQuestionChoiceResponse, ExerciseQuestionResponse, listExerciseQuestions } from "@/features/lms/api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Tabs from "@/components/ui/Tabs";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import EmptyState from "@/components/ui/EmptyState";

const studentStatusLabels: Record<string, string> = {
  CHUA_LAM: "Chưa làm",
  DANG_LAM: "Đang làm",
  DA_NOP: "Đã nộp",
  TRE_HAN: "Trễ hạn"
};

const studentStatusVariants: Record<string, any> = {
  CHUA_LAM: "neutral",
  DANG_LAM: "info",
  DA_NOP: "success",
  TRE_HAN: "warning"
};

/** Tra ID đáp án đã chọn → nhãn+nội dung dễ đọc ("A. Sat on the mat") — fallback về "#id" nếu chưa tải xong danh sách câu hỏi. */
function formatChoiceIds(ids: number[] | null | undefined, choices: ExerciseQuestionChoiceResponse[] | undefined): string | null {
  if (!ids || ids.length === 0) return null;
  return ids.map((id) => {
    const choice = choices?.find((c) => c.id === id);
    return choice ? `${choice.choiceLabel}. ${choice.content}` : `#${id}`;
  }).join("; ");
}

export default function AssignmentStatsDetailPage() {
  const { assignmentId } = useParams<{ assignmentId: string }>();
  const navigate = useNavigate();
  const [tab, setTab] = useState<"students" | "questions">("students");
  const [studentStats, setStudentStats] = useState<ExerciseAssignmentStudentStatsResponse | null>(null);
  const [questionStats, setQuestionStats] = useState<ExerciseAssignmentQuestionStatsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [expandedQuestionId, setExpandedQuestionId] = useState<number | null>(null);
  const [detailStudentId, setDetailStudentId] = useState<number | null>(null);

  const numAssignmentId = assignmentId ? parseInt(assignmentId, 10) : null;

  useEffect(() => {
    if (!numAssignmentId) return;
    setLoading(true);
    setError(null);
    getExerciseAssignmentStudentStats(numAssignmentId)
      .then(setStudentStats)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được kết quả BTVN."))
      .finally(() => setLoading(false));
  }, [numAssignmentId]);

  useEffect(() => {
    if (tab !== "questions" || questionStats || !numAssignmentId) return;
    getExerciseAssignmentQuestionStats(numAssignmentId)
      .then(setQuestionStats)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được phân tích câu hỏi."));
  }, [tab, numAssignmentId, questionStats]);

  const handleExport = async () => {
    if (!numAssignmentId) return;
    setExporting(true);
    try {
      const blob = await exportExerciseAssignmentStats(numAssignmentId);
      downloadBlob(blob, `thong-ke-btvn-${numAssignmentId}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không xuất được file Excel.");
    } finally {
      setExporting(false);
    }
  };

  const detailStudent = detailStudentId && studentStats ? studentStats.students.find(s => s.studentId === detailStudentId) : null;

  if (!studentStats) {
    return (
      <div className="space-y-4">
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900 mb-4"
        >
          <ArrowLeft className="w-4 h-4" />
          Quay lại
        </button>
        <Card>
          {error ? (
            <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-lg">{error}</div>
          ) : loading ? (
            <p className="text-sm text-slate-500">Đang tải chi tiết BTVN...</p>
          ) : (
            <p className="text-sm text-slate-500">Không tìm thấy dữ liệu.</p>
          )}
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900"
      >
        <ArrowLeft className="w-4 h-4" />
        Quay lại
      </button>

      <div>
        <h1 className="text-2xl font-bold font-display text-slate-900">{studentStats.assignment.exerciseTitle}</h1>
        <p className="text-sm text-slate-500 mt-1">{studentStats.assignment.exerciseCode}</p>
      </div>

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-lg">{error}</div>}

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
          <Tabs
            items={[
              { id: "students", label: "Kết quả học sinh" },
              { id: "questions", label: "Phân tích câu hỏi" }
            ]}
            activeId={tab}
            onChange={(id) => setTab(id as "students" | "questions")}
          />
        </div>

        <div className="p-5">
          {tab === "students" &&
            (loading ? (
              <p className="text-sm text-slate-500">Đang tải...</p>
            ) : !studentStats || studentStats.students.length === 0 ? (
              <p className="text-sm text-slate-400 italic">Chưa có dữ liệu để hiển thị.</p>
            ) : (
              <TableContainer className="border-0 rounded-none">
                <thead>
                  <tr>
                    <Th>Học sinh</Th>
                    <Th>Trạng thái</Th>
                    <Th className="text-center">Điểm</Th>
                    <Th className="text-center">%</Th>
                    <Th className="text-center">Đã hoàn thành</Th>
                    <Th className="text-center">Số lần làm</Th>
                    <Th />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {studentStats.students.map((s) => (
                    <tr key={s.studentId}>
                      <Td className="font-semibold text-slate-900">
                        {s.studentFullName} <span className="text-slate-400 font-mono text-sm">({s.studentCode})</span>
                      </Td>
                      <Td>
                        <Badge variant={studentStatusVariants[s.status]}>{studentStatusLabels[s.status]}</Badge>
                      </Td>
                      <Td className="text-center">
                        {s.totalScore != null ? `${s.totalScore}/${s.totalPoints}` : "—"}
                      </Td>
                      <Td className="text-center">
                        {s.percentage != null ? `${s.percentage}%` : "—"}
                      </Td>
                      <Td className="text-center">
                        {s.status === "CHUA_LAM" ? (
                          <Badge variant="neutral">Chưa làm</Badge>
                        ) : s.passed == null ? (
                          <Badge variant="neutral">—</Badge>
                        ) : (
                          <Badge variant={s.passed ? "success" : "danger"}>{s.passed ? "Đạt" : "Chưa đạt"}</Badge>
                        )}
                      </Td>
                      <Td className="text-center">{s.attemptNumber ?? "—"}</Td>
                      <Td className="text-center">
                        {s.status !== "CHUA_LAM" && (
                          <button
                            onClick={() => setDetailStudentId(s.studentId)}
                            className="text-slate-600 hover:text-slate-900 inline-flex items-center justify-center"
                            title="Xem chi tiết"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                        )}
                      </Td>
                    </tr>
                  ))}
                </tbody>
              </TableContainer>
            ))}

          {tab === "questions" &&
            (!questionStats ? (
              <p className="text-sm text-slate-500">Đang tải...</p>
            ) : questionStats.questions.length === 0 ? (
              <p className="text-sm text-slate-400 italic">Chưa có dữ liệu để hiển thị.</p>
            ) : (
              <div className="space-y-2">
                {[...questionStats.questions]
                  .sort((a, b) => b.wrongRatePercent - a.wrongRatePercent)
                  .map((q) => (
                    <QuestionRow
                      key={q.questionId}
                      question={q}
                      expanded={expandedQuestionId === q.questionId}
                      onToggle={() => setExpandedQuestionId(expandedQuestionId === q.questionId ? null : q.questionId)}
                    />
                  ))}
              </div>
            ))}
        </div>
      </Card>

      {tab === "students" && (
        <div className="flex justify-end">
          <Button variant="primary" size="sm" onClick={handleExport} disabled={exporting}>
            <Download className="w-3.5 h-3.5" /> {exporting ? "Đang xuất..." : "Xuất Excel"}
          </Button>
        </div>
      )}

      {detailStudent && (
        <StudentDetailModal student={detailStudent} exerciseId={studentStats.assignment.exerciseId} onClose={() => setDetailStudentId(null)} />
      )}
    </div>
  );
}

function QuestionRow({
  question,
  expanded,
  onToggle
}: {
  question: ExerciseAssignmentQuestionRow;
  expanded: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="border border-slate-200 rounded-lg overflow-hidden">
      <button
        onClick={onToggle}
        className="w-full flex items-center justify-between gap-3 p-2.5 text-left hover:bg-slate-50"
      >
        <div className="flex items-center gap-2 min-w-0">
          {expanded ? <ChevronDown className="w-3.5 h-3.5 shrink-0 text-slate-400" /> : <ChevronRight className="w-3.5 h-3.5 shrink-0 text-slate-400" />}
          <span className="text-sm text-slate-700 truncate">
            Câu {question.displayOrder}: {question.content}
          </span>
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — thống kê số lượt học sinh
              bấm "Gợi ý" tapescript của câu Nghe (skill=LISTENING), phục vụ GV biết câu nào học sinh
              hay cần trợ giúp. Chỉ hiện khi có ít nhất 1 lượt dùng, tránh rối màn với câu khác. */}
          {question.skill === "LISTENING" && question.hintUsedCount > 0 && (
            <Badge variant="info" className="flex items-center gap-1">
              <HelpCircle className="w-3 h-3" />
              Gợi ý {question.hintUsedCount} lượt ({question.hintUsedStudentCount} học sinh)
            </Badge>
          )}
          <Badge variant={question.wrongRatePercent >= 50 ? "danger" : question.wrongRatePercent > 0 ? "warning" : "success"}>
            Sai {question.wrongCount}/{question.answeredCount} ({question.wrongRatePercent}%)
          </Badge>
        </div>
      </button>
      {expanded && (
        <div className="px-4 pb-3 pl-11">
          {question.wrongStudents.length === 0 ? (
            <p className="text-sm text-slate-400 italic">Không có học sinh nào trả lời sai.</p>
          ) : (
            <ul className="text-sm text-slate-600 space-y-1">
              {question.wrongStudents.map((s) => (
                <li key={s.studentId}>
                  {s.studentFullName} <span className="text-slate-400 font-mono">({s.studentCode})</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function StudentDetailModal({
  student,
  exerciseId,
  onClose
}: {
  student: ExerciseAssignmentStudentRow;
  exerciseId: number;
  onClose: () => void;
}) {
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — trước đây LUÔN tải câu trả lời
  // của lượt MỚI NHẤT (student.attemptId), dù học sinh có nhiều lượt làm — GV không xem lại được câu
  // trả lời của các lượt CŨ (dữ liệu vẫn còn trong DB, chỉ là FE chưa cho chọn). Giờ mặc định vẫn hiện
  // lượt mới nhất, nhưng GV bấm "Xem trả lời" ở bảng "Lịch sử nhiều lượt làm bài" bên dưới để đổi.
  const [viewingAttemptId, setViewingAttemptId] = React.useState<number | null>(student.attemptId);
  const [answers, setAnswers] = React.useState<StudentAnswerRow[]>([]);
  const [loadingAnswers, setLoadingAnswers] = React.useState(false);
  const [answerError, setAnswerError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!viewingAttemptId) return;
    setLoadingAnswers(true);
    setAnswerError(null);
    getAttemptAnswers(viewingAttemptId)
      .then(setAnswers)
      .catch((err) => setAnswerError(err instanceof ApiError ? err.message : "Không tải được lịch sử trả lời."))
      .finally(() => setLoadingAnswers(false));
  }, [viewingAttemptId]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — StudentAnswerRow (từ
  // /answers/for-grading) chỉ trả selectedChoiceIds/correctChoiceIds thô (ID) + questionId, không
  // kèm nội dung câu hỏi lẫn nhãn/nội dung đáp án — trước đây hiện thẳng ID ("Chọn: 23") khiến GV
  // không đọc được câu hỏi/học sinh chọn gì. Tải thêm danh sách câu hỏi của Bài (đã có sẵn
  // questionContent + choices đầy đủ) để tra theo questionId.
  const [questionsById, setQuestionsById] = React.useState<Map<number, ExerciseQuestionResponse>>(new Map());

  React.useEffect(() => {
    listExerciseQuestions(exerciseId)
      .then((qs) => setQuestionsById(new Map(qs.map((q) => [q.questionId, q]))))
      .catch(() => undefined);
  }, [exerciseId]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — xem toàn bộ lịch sử nhiều lượt
  // làm bài (kể cả lượt bị hệ thống dừng ép do vi phạm giám sát) để Giáo viên tự chọn lượt phù hợp.
  const [attempts, setAttempts] = React.useState<ExerciseAttemptRow[]>([]);
  const [loadingAttempts, setLoadingAttempts] = React.useState(false);
  const [attemptsError, setAttemptsError] = React.useState<string | null>(null);

  React.useEffect(() => {
    setLoadingAttempts(true);
    setAttemptsError(null);
    listStudentExerciseAttempts(exerciseId, student.studentId)
      .then(setAttempts)
      .catch((err) => setAttemptsError(err instanceof ApiError ? err.message : "Không tải được lịch sử làm bài."))
      .finally(() => setLoadingAttempts(false));
  }, [exerciseId, student.studentId]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto mx-4">
        <div className="sticky top-0 bg-white border-b border-slate-200 p-4 flex items-center justify-between">
          <h2 className="font-semibold text-slate-900">Chi tiết kết quả - {student.studentFullName}</h2>
          <button
            onClick={onClose}
            className="text-slate-500 hover:text-slate-700"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-6">
          {/* Thông tin attempt */}
          <div>
            <h3 className="font-semibold text-sm mb-3 text-slate-900">Thông tin lần làm</h3>
            <div className="grid grid-cols-2 gap-4">
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-sm text-slate-500">Trạng thái</p>
                <p className="font-semibold text-sm mt-1">{studentStatusLabels[student.status]}</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-sm text-slate-500">Điểm số</p>
                <p className="font-semibold text-sm mt-1">{student.totalScore ?? "—"}/{student.totalPoints}</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-sm text-slate-500">Tỉ lệ</p>
                <p className="font-semibold text-sm mt-1">{student.percentage ?? "—"}%</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-sm text-slate-500">Lần làm</p>
                <p className="font-semibold text-sm mt-1">{student.attemptNumber ?? "—"}</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-sm text-slate-500">Nộp lúc</p>
                <p className="font-semibold text-sm mt-1 text-[12px]">
                  {student.submittedAt ? new Date(student.submittedAt).toLocaleString("vi-VN") : "—"}
                </p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-sm text-slate-500">Kết quả</p>
                <p className="font-semibold text-sm mt-1">
                  {student.passed == null ? "—" : student.passed ? "Đạt" : "Chưa đạt"}
                </p>
              </div>
            </div>
          </div>

          {/* Lịch sử nhiều lượt làm bài — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
              2026-08-06, chỉ hiện khi có >1 lượt hoặc có lượt bị dừng ép, tránh rối màn với đề
              chỉ cho làm 1 lần (trường hợp phổ biến nhất). */}
          {(attempts.length > 1 || attempts.some((a) => a.stoppedByIntegrityViolation)) && (
            <div>
              <h3 className="font-semibold text-sm mb-3 text-slate-900">Lịch sử nhiều lượt làm bài</h3>
              {attemptsError && (
                <div className="mb-3 text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{attemptsError}</div>
              )}
              {loadingAttempts ? (
                <p className="text-sm text-slate-500">Đang tải...</p>
              ) : (
                <div className="border border-slate-200 rounded-lg overflow-hidden">
                  <table className="w-full text-sm">
                    <thead className="bg-slate-50">
                      <tr>
                        <th className="text-center p-2 border-b border-slate-200 w-14">Lượt</th>
                        <th className="text-left p-2 border-b border-slate-200">Bắt đầu</th>
                        <th className="text-left p-2 border-b border-slate-200">Nộp lúc</th>
                        <th className="text-center p-2 border-b border-slate-200">Điểm</th>
                        <th className="text-center p-2 border-b border-slate-200">Kết quả</th>
                        <th className="text-left p-2 border-b border-slate-200">Ghi chú</th>
                        <th className="text-center p-2 border-b border-slate-200">Điểm chính thức</th>
                        <th className="text-center p-2 border-b border-slate-200">Xem trả lời</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {attempts.map((a) => (
                        <AttemptHistoryRow
                          key={a.id}
                          attempt={a}
                          onSelected={(updated) => setAttempts(updated)}
                          viewing={viewingAttemptId === a.id}
                          onView={() => setViewingAttemptId(a.id)}
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* Lịch sử trả lời câu hỏi */}
          <div>
            <h3 className="font-semibold text-sm mb-3 text-slate-900">
              Lịch sử trả lời câu hỏi
              {attempts.length > 1 && viewingAttemptId != null && (
                <span className="font-normal text-slate-500">
                  {" — Lượt #"}
                  {attempts.find((a) => a.id === viewingAttemptId)?.attemptNumber ?? "?"}
                </span>
              )}
            </h3>
            {answerError && (
              <div className="mb-3 text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{answerError}</div>
            )}
            <div className="border border-slate-200 rounded-lg overflow-hidden">
              {loadingAnswers ? (
                <div className="text-center p-4 text-sm text-slate-500">Đang tải...</div>
              ) : answers.length === 0 ? (
                <div className="text-center p-4 text-sm text-slate-500">Không có dữ liệu.</div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-slate-50">
                    <tr>
                      <th className="text-center p-2 border-b border-slate-200 w-12">Câu</th>
                      <th className="text-left p-2 border-b border-slate-200">Câu hỏi</th>
                      <th className="text-left p-2 border-b border-slate-200">Trả lời của học sinh</th>
                      <th className="text-left p-2 border-b border-slate-200">Đáp án đúng</th>
                      <th className="text-center p-2 border-b border-slate-200 w-16">Kết quả</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {answers.map((answer, idx) => {
                      const question = questionsById.get(answer.questionId);
                      const choices = question?.choices;
                      const selectedLabel = formatChoiceIds(answer.selectedChoiceIds, choices);
                      const correctLabel = formatChoiceIds(answer.correctChoiceIds, choices);
                      return (
                      <tr key={answer.id}>
                        <td className="text-center p-2 font-semibold text-slate-900">{idx + 1}</td>
                        <td className="p-2 text-slate-700 break-words max-w-[220px]">{question?.questionContent ?? "—"}</td>
                        <td className="p-2 text-slate-600">
                          {answer.answerText ? (
                            <span className="break-words">{answer.answerText}</span>
                          ) : selectedLabel ? (
                            <span className="break-words">{selectedLabel}</span>
                          ) : (
                            <span className="text-slate-400 italic">Không trả lời</span>
                          )}
                        </td>
                        <td className="p-2 text-slate-600">
                          {answer.correctAnswerText ? (
                            <span className="break-words">{answer.correctAnswerText}</span>
                          ) : correctLabel ? (
                            <span className="break-words">{correctLabel}</span>
                          ) : (
                            <span className="text-slate-400">—</span>
                          )}
                          {answer.explanation && (
                            <p className="text-sm text-slate-500 mt-1 italic">
                              {answer.explanation}
                            </p>
                          )}
                        </td>
                        <td className="text-center p-2">
                          {answer.isCorrect == null ? (
                            <span className="text-slate-400">—</span>
                          ) : answer.isCorrect ? (
                            <span className="text-green-600 font-bold">✓</span>
                          ) : (
                            <span className="text-red-600 font-bold">✗</span>
                          )}
                        </td>
                      </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>

        <div className="border-t border-slate-200 bg-slate-50 p-4 flex justify-end gap-2">
          <Button size="sm" onClick={onClose}>
            Đóng
          </Button>
        </div>
      </div>
    </div>
  );
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — 1 dòng lịch sử làm bài; lượt bị
 * dừng ép do vi phạm giám sát hiện thêm badge + bấm để xem tổng hợp vi phạm (integrity-summary).
 */
function AttemptHistoryRow({
  attempt,
  onSelected,
  viewing,
  onView
}: {
  attempt: ExerciseAttemptRow;
  onSelected: (updated: ExerciseAttemptRow[]) => void;
  viewing: boolean;
  onView: () => void;
}) {
  const [summary, setSummary] = React.useState<IntegritySummaryRow | null>(null);
  const [showSummary, setShowSummary] = React.useState(false);
  const [loadingSummary, setLoadingSummary] = React.useState(false);
  const [selecting, setSelecting] = React.useState(false);

  const toggleSummary = () => {
    if (showSummary) {
      setShowSummary(false);
      return;
    }
    setShowSummary(true);
    if (summary || loadingSummary) return;
    setLoadingSummary(true);
    getAttemptIntegritySummary(attempt.id)
      .then(setSummary)
      .catch(() => undefined)
      .finally(() => setLoadingSummary(false));
  };

  const handleSelectForGrading = () => {
    if (selecting || attempt.selectedForGrading) return;
    setSelecting(true);
    selectAttemptForGrading(attempt.id)
      .then(onSelected)
      .catch(() => undefined)
      .finally(() => setSelecting(false));
  };

  return (
    <React.Fragment>
      <tr className={viewing ? "bg-blue-50/60" : undefined}>
        <td className="text-center p-2 font-semibold text-slate-900">#{attempt.attemptNumber}</td>
        <td className="p-2 text-slate-600 text-sm">{new Date(attempt.startedAt).toLocaleString("vi-VN")}</td>
        <td className="p-2 text-slate-600 text-sm">
          {attempt.submittedAt ? new Date(attempt.submittedAt).toLocaleString("vi-VN") : "—"}
        </td>
        <td className="text-center p-2">
          {attempt.totalScore != null ? `${attempt.totalScore}${attempt.percentage != null ? ` (${attempt.percentage}%)` : ""}` : "—"}
        </td>
        <td className="text-center p-2">
          {attempt.passed == null ? (
            <span className="text-slate-400">—</span>
          ) : attempt.passed ? (
            <span className="text-green-600 font-bold">Đạt</span>
          ) : (
            <span className="text-red-600 font-bold">Chưa đạt</span>
          )}
        </td>
        <td className="p-2">
          {attempt.stoppedByIntegrityViolation ? (
            <button
              onClick={toggleSummary}
              className="inline-flex items-center gap-1 text-sm font-bold text-amber-700 bg-amber-50 border border-amber-200 px-2 py-1 rounded-md hover:bg-amber-100"
            >
              <ShieldAlert className="w-3 h-3" /> Bị dừng do vi phạm giám sát
            </button>
          ) : (
            <span className="text-slate-300">—</span>
          )}
        </td>
        <td className="text-center p-2">
          {attempt.selectedForGrading ? (
            <span className="inline-flex items-center gap-1 text-sm font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-1 rounded-md">
              <CheckCircle2 className="w-3 h-3" /> Đã chọn
            </span>
          ) : (
            <button
              onClick={handleSelectForGrading}
              disabled={selecting}
              className="text-sm font-medium text-blue-700 bg-blue-50 border border-blue-200 px-2 py-1 rounded-md hover:bg-blue-100 disabled:opacity-50"
            >
              {selecting ? "Đang chọn..." : "Chọn làm điểm"}
            </button>
          )}
        </td>
        <td className="text-center p-2">
          {viewing ? (
            <span className="inline-flex items-center gap-1 text-sm font-bold text-blue-700">
              <Eye className="w-3 h-3" /> Đang xem
            </span>
          ) : (
            <button
              onClick={onView}
              className="inline-flex items-center gap-1 text-sm font-medium text-slate-600 bg-slate-50 border border-slate-200 px-2 py-1 rounded-md hover:bg-slate-100"
            >
              <Eye className="w-3 h-3" /> Xem trả lời
            </button>
          )}
        </td>
      </tr>
      {showSummary && (
        <tr>
          <td colSpan={8} className="p-2 bg-amber-50/60 text-sm text-amber-900">
            {loadingSummary ? (
              "Đang tải..."
            ) : summary ? (
              <>
                Thoát ra {summary.violationCount} lần, tổng {summary.violationTotalDurationSeconds} giây.{" "}
                {summary.parentAndTeacherNotified ? "Đã báo Phụ huynh + Giáo viên phụ trách lớp." : "Chưa vượt ngưỡng báo."}
              </>
            ) : (
              "Không tải được tổng hợp vi phạm."
            )}
          </td>
        </tr>
      )}
    </React.Fragment>
  );
}
