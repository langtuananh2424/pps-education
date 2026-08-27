import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ArrowLeft, CheckCircle2, ChevronDown, ChevronRight, Download, Eye, HelpCircle, ShieldAlert, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { formatDateTime } from "@/lib/i18nFormat";
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
  const { t } = useTranslation("academic-homework");
  const studentStatusLabels: Record<string, string> = {
    CHUA_LAM: t("shared.studentStatus.CHUA_LAM"),
    DANG_LAM: t("shared.studentStatus.DANG_LAM"),
    DA_NOP: t("shared.studentStatus.DA_NOP"),
    TRE_HAN: t("shared.studentStatus.TRE_HAN")
  };
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("shared.errors.loadResultsFailed")))
      .finally(() => setLoading(false));
  }, [numAssignmentId]);

  useEffect(() => {
    if (tab !== "questions" || questionStats || !numAssignmentId) return;
    getExerciseAssignmentQuestionStats(numAssignmentId)
      .then(setQuestionStats)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("shared.errors.loadQuestionAnalysisFailed")));
  }, [tab, numAssignmentId, questionStats]);

  const handleExport = async () => {
    if (!numAssignmentId) return;
    setExporting(true);
    try {
      const blob = await exportExerciseAssignmentStats(numAssignmentId);
      downloadBlob(blob, `thong-ke-btvn-${numAssignmentId}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("exerciseDetail.exportFailed"));
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
          {t("shared.back")}
        </button>
        <Card>
          {error ? (
            <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-lg">{error}</div>
          ) : loading ? (
            <p className="text-sm text-slate-500">{t("shared.loadingDetail")}</p>
          ) : (
            <p className="text-sm text-slate-500">{t("shared.notFound")}</p>
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
        {t("shared.back")}
      </button>

      <div>
        <h1 className="text-2xl font-bold font-display text-slate-900">{studentStats.assignment.exerciseTitle}</h1>
        <p className="text-xs text-slate-500 mt-1">{studentStats.assignment.exerciseCode}</p>
      </div>

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-lg">{error}</div>}

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
          <Tabs
            items={[
              { id: "students", label: t("shared.tabs.students") },
              { id: "questions", label: t("shared.tabs.questions") }
            ]}
            activeId={tab}
            onChange={(id) => setTab(id as "students" | "questions")}
          />
        </div>

        <div className="p-5">
          {tab === "students" &&
            (loading ? (
              <p className="text-sm text-slate-500">{t("shared.loading")}</p>
            ) : !studentStats || studentStats.students.length === 0 ? (
              <p className="text-sm text-slate-400 italic">{t("shared.noDataToShow")}</p>
            ) : (
              <TableContainer className="border-0 rounded-none">
                <thead>
                  <tr>
                    <Th>{t("shared.table.student")}</Th>
                    <Th>{t("exerciseDetail.table.status")}</Th>
                    <Th className="text-center">{t("exerciseDetail.table.score")}</Th>
                    <Th className="text-center">{t("exerciseDetail.table.percent")}</Th>
                    <Th className="text-center">{t("exerciseDetail.table.completed")}</Th>
                    <Th className="text-center">{t("exerciseDetail.table.attemptCount")}</Th>
                    <Th className="text-center">{t("exerciseDetail.table.violation")}</Th>
                    <Th />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {studentStats.students.map((s) => (
                    <tr key={s.studentId}>
                      <Td className="font-semibold text-slate-900">
                        {s.studentFullName} <span className="text-slate-400 font-mono text-[10px]">({s.studentCode})</span>
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
                          <Badge variant="neutral">{t("shared.studentStatus.CHUA_LAM")}</Badge>
                        ) : s.passed == null ? (
                          <Badge variant="neutral">—</Badge>
                        ) : (
                          <Badge variant={s.passed ? "success" : "danger"}>{s.passed ? t("shared.passed") : t("shared.notPassed")}</Badge>
                        )}
                      </Td>
                      <Td className="text-center">{s.numberOfAttempts ?? "—"}</Td>
                      <Td className="text-center text-xs">
                        {s.selectedAttemptStoppedByViolation ? (
                          <span className="inline-flex items-center gap-1 font-bold text-amber-700 bg-amber-50 px-2 py-1 rounded">
                            <ShieldAlert className="w-3 h-3" /> {t("exerciseDetail.table.violation")}
                          </span>
                        ) : s.latestAttemptViolationCount && s.latestAttemptViolationCount > 0 ? (
                          <span className="font-bold text-amber-700 bg-amber-50 px-2 py-1 rounded">
                            {t("exerciseDetail.violationCount", { count: s.latestAttemptViolationCount })}
                          </span>
                        ) : (
                          <span className="text-slate-300">—</span>
                        )}
                      </Td>
                      <Td className="text-center">
                        {s.status !== "CHUA_LAM" && (
                          <button
                            onClick={() => setDetailStudentId(s.studentId)}
                            className="text-slate-600 hover:text-slate-900 inline-flex items-center justify-center"
                            title={t("exerciseDetail.table.viewDetailTitle")}
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
              <p className="text-sm text-slate-500">{t("shared.loading")}</p>
            ) : questionStats.questions.length === 0 ? (
              <p className="text-sm text-slate-400 italic">{t("shared.noDataToShow")}</p>
            ) : (
              <div className="space-y-6">
                <QuestionAnalysisChart questions={questionStats.questions} />
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
              </div>
            ))}
        </div>
      </Card>

      {tab === "students" && (
        <div className="flex justify-end">
          <Button variant="primary" size="sm" onClick={handleExport} disabled={exporting}>
            <Download className="w-3.5 h-3.5" /> {exporting ? t("exerciseDetail.exporting") : t("exerciseDetail.exportExcel")}
          </Button>
        </div>
      )}

      {detailStudent && (
        <StudentDetailModal
          student={detailStudent}
          exerciseId={studentStats.assignment.exerciseId}
          assignmentId={numAssignmentId}
          onClose={() => setDetailStudentId(null)}
          onRefreshStats={() => {
            if (numAssignmentId) {
              getExerciseAssignmentStudentStats(numAssignmentId).then(setStudentStats).catch(() => undefined);
            }
          }}
        />
      )}
    </div>
  );
}

export function QuestionRow({
  question,
  expanded,
  onToggle
}: {
  question: ExerciseAssignmentQuestionRow;
  expanded: boolean;
  onToggle: () => void;
}) {
  const { t } = useTranslation("academic-homework");
  return (
    <div className="border border-slate-200 rounded-lg overflow-hidden">
      <button
        onClick={onToggle}
        className="w-full flex items-center justify-between gap-3 p-2.5 text-left hover:bg-slate-50"
      >
        <div className="flex items-center gap-2 min-w-0">
          {expanded ? <ChevronDown className="w-3.5 h-3.5 shrink-0 text-slate-400" /> : <ChevronRight className="w-3.5 h-3.5 shrink-0 text-slate-400" />}
          <span className="text-xs text-slate-700 truncate">
            {t("shared.questionRow.questionLabel", { order: question.displayOrder, text: question.content })}
          </span>
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — thống kê số lượt học sinh
              bấm "Gợi ý" tapescript của câu Nghe (skill=LISTENING), phục vụ GV biết câu nào học sinh
              hay cần trợ giúp. Chỉ hiện khi có ít nhất 1 lượt dùng, tránh rối màn với câu khác. */}
          {question.skill === "LISTENING" && question.hintUsedCount > 0 && (
            <Badge variant="info" className="flex items-center gap-1">
              <HelpCircle className="w-3 h-3" />
              {t("exerciseDetail.questionRow.hintUsed", { count: question.hintUsedCount, studentCount: question.hintUsedStudentCount })}
            </Badge>
          )}
          <Badge variant={question.wrongRatePercent >= 50 ? "danger" : question.wrongRatePercent > 0 ? "warning" : "success"}>
            {t("shared.questionRow.wrongBadge", {
              wrongCount: question.wrongCount,
              answeredCount: question.answeredCount,
              percent: question.wrongRatePercent
            })}
          </Badge>
        </div>
      </button>
      {expanded && (
        <div className="px-4 pb-3 pl-11">
          {question.wrongStudents.length === 0 ? (
            <p className="text-[11px] text-slate-400 italic">{t("shared.questionRow.noWrongStudents")}</p>
          ) : (
            <ul className="text-[11px] text-slate-600 space-y-1">
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
  assignmentId,
  onClose,
  onRefreshStats
}: {
  student: ExerciseAssignmentStudentRow;
  exerciseId: number;
  assignmentId: number | null;
  onClose: () => void;
  onRefreshStats?: () => void;
}) {
  const { t, i18n } = useTranslation("academic-homework");
  const studentStatusLabels: Record<string, string> = {
    CHUA_LAM: t("shared.studentStatus.CHUA_LAM"),
    DANG_LAM: t("shared.studentStatus.DANG_LAM"),
    DA_NOP: t("shared.studentStatus.DA_NOP"),
    TRE_HAN: t("shared.studentStatus.TRE_HAN")
  };
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
      .catch((err) => setAnswerError(err instanceof ApiError ? err.message : t("exerciseDetail.studentModal.answerHistory.loadFailed")))
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
      .catch((err) => setAttemptsError(err instanceof ApiError ? err.message : t("exerciseDetail.studentModal.history.loadFailed")))
      .finally(() => setLoadingAttempts(false));
  }, [exerciseId, student.studentId]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto mx-4">
        <div className="sticky top-0 bg-white border-b border-slate-200 p-4 flex items-center justify-between">
          <h2 className="font-semibold text-slate-900">{t("exerciseDetail.studentModal.title", { studentName: student.studentFullName })}</h2>
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
            <h3 className="font-semibold text-sm mb-3 text-slate-900">{t("exerciseDetail.studentModal.attemptInfo.title")}</h3>
            <div className="grid grid-cols-2 gap-4">
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">{t("exerciseDetail.studentModal.attemptInfo.status")}</p>
                <p className="font-semibold text-sm mt-1">{studentStatusLabels[student.status]}</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">{t("exerciseDetail.studentModal.attemptInfo.score")}</p>
                <p className="font-semibold text-sm mt-1">{student.totalScore ?? "—"}/{student.totalPoints}</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">{t("exerciseDetail.studentModal.attemptInfo.percentage")}</p>
                <p className="font-semibold text-sm mt-1">{student.percentage ?? "—"}%</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">{t("exerciseDetail.studentModal.attemptInfo.attemptNumber")}</p>
                <p className="font-semibold text-sm mt-1">{student.attemptNumber ?? "—"}</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">{t("exerciseDetail.studentModal.attemptInfo.submittedAt")}</p>
                <p className="font-semibold text-sm mt-1 text-[12px]">
                  {student.submittedAt ? formatDateTime(student.submittedAt, i18n.language) : "—"}
                </p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">{t("exerciseDetail.studentModal.attemptInfo.result")}</p>
                <p className="font-semibold text-sm mt-1">
                  {student.passed == null ? "—" : student.passed ? t("shared.passed") : t("shared.notPassed")}
                </p>
              </div>
            </div>
          </div>

          {/* Lịch sử làm bài — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06.
              Luôn hiển thị để giáo viên có thể chọn lượt làm chính thức, kể cả khi chỉ có 1 lượt. */}
          {attempts.length > 0 && (
            <div>
              <h3 className="font-semibold text-sm mb-3 text-slate-900">
                {attempts.length > 1
                  ? t("exerciseDetail.studentModal.history.titleMultiple")
                  : t("exerciseDetail.studentModal.history.titleSingle")}
              </h3>
              {attemptsError && (
                <div className="mb-3 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{attemptsError}</div>
              )}
              {loadingAttempts ? (
                <p className="text-sm text-slate-500">{t("shared.loading")}</p>
              ) : (
                <div className="border border-slate-200 rounded-lg overflow-hidden">
                  <table className="w-full text-xs">
                    <thead className="bg-slate-50">
                      <tr>
                        <th className="text-center p-2 border-b border-slate-200 w-14">{t("exerciseDetail.studentModal.history.columns.attempt")}</th>
                        <th className="text-left p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.history.columns.startedAt")}</th>
                        <th className="text-left p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.history.columns.submittedAt")}</th>
                        <th className="text-center p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.history.columns.score")}</th>
                        <th className="text-center p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.history.columns.result")}</th>
                        <th className="text-left p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.history.columns.note")}</th>
                        <th className="text-center p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.history.columns.officialScore")}</th>
                        <th className="text-center p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.history.columns.viewAnswers")}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {attempts.map((a) => (
                        <AttemptHistoryRow
                          key={a.id}
                          attempt={a}
                          onSelected={(updated) => setAttempts(updated)}
                          onRefreshStats={onRefreshStats}
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
              {t("exerciseDetail.studentModal.answerHistory.title")}
              {viewingAttemptId != null && (
                <span className="font-normal text-slate-500">
                  {t("exerciseDetail.studentModal.answerHistory.attemptSuffix", {
                    number: attempts.find((a) => a.id === viewingAttemptId)?.attemptNumber ?? "?"
                  })}
                </span>
              )}
            </h3>
            {answerError && (
              <div className="mb-3 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{answerError}</div>
            )}
            <div className="border border-slate-200 rounded-lg overflow-hidden">
              {loadingAnswers ? (
                <div className="text-center p-4 text-sm text-slate-500">{t("shared.loading")}</div>
              ) : answers.length === 0 ? (
                <div className="text-center p-4 text-sm text-slate-500">{t("exerciseDetail.studentModal.answerHistory.noData")}</div>
              ) : (
                <table className="w-full text-xs">
                  <thead className="bg-slate-50">
                    <tr>
                      <th className="text-center p-2 border-b border-slate-200 w-12">{t("exerciseDetail.studentModal.answerHistory.columns.questionNumber")}</th>
                      <th className="text-left p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.answerHistory.columns.question")}</th>
                      <th className="text-left p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.answerHistory.columns.studentAnswer")}</th>
                      <th className="text-left p-2 border-b border-slate-200">{t("exerciseDetail.studentModal.answerHistory.columns.correctAnswer")}</th>
                      <th className="text-center p-2 border-b border-slate-200 w-16">{t("exerciseDetail.studentModal.answerHistory.columns.result")}</th>
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
                            <span className="text-slate-400 italic">{t("exerciseDetail.studentModal.answerHistory.noAnswer")}</span>
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
                            <p className="text-[10px] text-slate-500 mt-1 italic">
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
            {t("shared.close")}
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
  onRefreshStats,
  viewing,
  onView
}: {
  attempt: ExerciseAttemptRow;
  onSelected: (updated: ExerciseAttemptRow[]) => void;
  onRefreshStats?: () => void;
  viewing: boolean;
  onView: () => void;
}) {
  const { t, i18n } = useTranslation("academic-homework");
  const [summary, setSummary] = React.useState<IntegritySummaryRow | null>(null);
  const [showSummary, setShowSummary] = React.useState(false);
  const [loadingSummary, setLoadingSummary] = React.useState(false);
  const [selecting, setSelecting] = React.useState(false);
  const [selectError, setSelectError] = React.useState<string | null>(null);

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
    setSelectError(null);
    selectAttemptForGrading(attempt.id)
      .then((updated) => {
        onSelected(updated);
        onRefreshStats?.();
      })
      .catch((err) => {
        setSelectError(err instanceof Error ? err.message : t("exerciseDetail.studentModal.history.selectFailed"));
      })
      .finally(() => setSelecting(false));
  };

  return (
    <React.Fragment>
      <tr className={viewing ? "bg-blue-50/60" : undefined}>
        <td className="text-center p-2 font-semibold text-slate-900">#{attempt.attemptNumber}</td>
        <td className="p-2 text-slate-600 text-[11px]">{formatDateTime(attempt.startedAt, i18n.language)}</td>
        <td className="p-2 text-slate-600 text-[11px]">
          {attempt.submittedAt ? formatDateTime(attempt.submittedAt, i18n.language) : "—"}
        </td>
        <td className="text-center p-2">
          {attempt.totalScore != null ? `${attempt.totalScore}${attempt.percentage != null ? ` (${attempt.percentage}%)` : ""}` : "—"}
        </td>
        <td className="text-center p-2">
          {attempt.passed == null ? (
            <span className="text-slate-400">—</span>
          ) : attempt.passed ? (
            <span className="text-green-600 font-bold">{t("shared.passed")}</span>
          ) : (
            <span className="text-red-600 font-bold">{t("shared.notPassed")}</span>
          )}
        </td>
        <td className="p-2">
          {attempt.stoppedByIntegrityViolation ? (
            <button
              onClick={toggleSummary}
              className="inline-flex items-center gap-1 text-[10px] font-bold text-amber-700 bg-amber-50 border border-amber-200 px-2 py-1 rounded-md hover:bg-amber-100"
            >
              <ShieldAlert className="w-3 h-3" /> {t("exerciseDetail.studentModal.history.stoppedByViolation")}
            </button>
          ) : (
            <span className="text-slate-300">—</span>
          )}
        </td>
        <td className="text-center p-2">
          <div className="flex flex-col items-center gap-1">
            {attempt.selectedForGrading ? (
              <span className="inline-flex items-center gap-1 text-[10px] font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-1 rounded-md">
                <CheckCircle2 className="w-3 h-3" /> {t("exerciseDetail.studentModal.history.selected")}
              </span>
            ) : (
              <button
                onClick={handleSelectForGrading}
                disabled={selecting}
                className="text-[10px] font-medium text-blue-700 bg-blue-50 border border-blue-200 px-2 py-1 rounded-md hover:bg-blue-100 disabled:opacity-50"
              >
                {selecting ? t("exerciseDetail.studentModal.history.selecting") : t("exerciseDetail.studentModal.history.selectForGrading")}
              </button>
            )}
            {selectError && (
              <span className="text-[9px] text-red-600 font-medium">{selectError}</span>
            )}
          </div>
        </td>
        <td className="text-center p-2">
          {viewing ? (
            <span className="inline-flex items-center gap-1 text-[10px] font-bold text-blue-700">
              <Eye className="w-3 h-3" /> {t("exerciseDetail.studentModal.history.viewing")}
            </span>
          ) : (
            <button
              onClick={onView}
              className="inline-flex items-center gap-1 text-[10px] font-medium text-slate-600 bg-slate-50 border border-slate-200 px-2 py-1 rounded-md hover:bg-slate-100"
            >
              <Eye className="w-3 h-3" /> {t("exerciseDetail.studentModal.history.viewAnswersAction")}
            </button>
          )}
        </td>
      </tr>
      {showSummary && (
        <tr>
          <td colSpan={8} className="p-2 bg-amber-50/60 text-[11px] text-amber-900">
            {loadingSummary ? (
              t("shared.loading")
            ) : summary ? (
              <>
                {t("exerciseDetail.studentModal.history.integritySummary.text", {
                  count: summary.violationCount,
                  seconds: summary.violationTotalDurationSeconds
                })}{" "}
                {summary.parentAndTeacherNotified
                  ? t("exerciseDetail.studentModal.history.integritySummary.notified")
                  : t("exerciseDetail.studentModal.history.integritySummary.notNotified")}
              </>
            ) : (
              t("exerciseDetail.studentModal.history.integritySummary.loadFailed")
            )}
          </td>
        </tr>
      )}
    </React.Fragment>
  );
}

/** Biểu đồ phân tích câu hỏi - hiển thị % câu sai của từng câu dưới dạng cột. */
export function QuestionAnalysisChart({ questions }: { questions: ExerciseAssignmentQuestionRow[] }) {
  const { t } = useTranslation("academic-homework");
  const maxRate = Math.max(...questions.map(q => q.wrongRatePercent), 0) || 100;

  return (
    <div className="border border-slate-200 rounded-lg p-4 bg-slate-50">
      <h4 className="text-sm font-semibold text-slate-900 mb-4">{t("exerciseDetail.chart.title")}</h4>
      <div className="flex items-end justify-center gap-2 h-80 pt-8 pb-4 px-4 bg-white border border-slate-200 rounded-lg overflow-x-auto">
        {questions.map((q) => (
          <div key={q.questionId} className="flex flex-col items-center gap-2 shrink-0" style={{ minWidth: "60px" }}>
            {/* Tỉ lệ % ở trên cùng */}
            <div className="text-xs font-bold text-slate-700 h-6 flex items-center">
              {q.wrongRatePercent}%
            </div>

            {/* Cột */}
            <div
              className={`w-10 rounded-t-lg border border-slate-300 transition-all hover:shadow-md ${
                q.wrongRatePercent >= 50
                  ? "bg-red-500 hover:bg-red-600"
                  : q.wrongRatePercent > 0
                  ? "bg-amber-400 hover:bg-amber-500"
                  : "bg-green-500 hover:bg-green-600"
              }`}
              style={{ height: `${(q.wrongRatePercent / maxRate) * 240}px` }}
              title={t("exerciseDetail.chart.tooltip", {
                order: q.displayOrder,
                wrongCount: q.wrongCount,
                answeredCount: q.answeredCount
              })}
            />

            {/* Nhãn câu hỏi */}
            <div className="text-xs font-semibold text-slate-700 text-center">
              {t("exerciseDetail.chart.questionLabel", { order: q.displayOrder })}
            </div>

            {/* Chi tiết số lượng */}
            <div className="text-[10px] text-slate-500 text-center whitespace-nowrap">
              {q.wrongCount}/{q.answeredCount}
            </div>
          </div>
        ))}
      </div>

      {/* Chú thích màu sắc */}
      <div className="mt-3 flex items-center justify-center gap-4 text-xs text-slate-600">
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-3 rounded bg-red-500" />
          <span>{t("exerciseDetail.chart.legend.hard")}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-3 rounded bg-amber-400" />
          <span>{t("exerciseDetail.chart.legend.medium")}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-3 rounded bg-green-500" />
          <span>{t("exerciseDetail.chart.legend.easy")}</span>
        </div>
      </div>
    </div>
  );
}
