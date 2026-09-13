import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ArrowLeft, ChevronDown, ChevronRight, ShieldAlert, XCircle } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  ReviewVideoAssignmentQuestionRow,
  ReviewVideoAssignmentQuestionStatsResponse,
  ReviewVideoAssignmentStudentStatsResponse,
  getReviewVideoAssignmentQuestionStats,
  getReviewVideoAssignmentStudentStats,
  updateReviewVideoAssignmentLateSubmissionAllowed
} from "@/features/lms/api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Tabs from "@/components/ui/Tabs";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import { formatDateTime } from "@/lib/i18nFormat";
import DatePicker from "@/components/ui/DatePicker";
import Time24Input from "@/components/ui/Time24Input";
import Modal from "@/components/ui/Modal";

/**
 * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — "Xem chi tiết" 1 BTVN Video
 * Ôn tập (REFLEX/CONNECTION), mirror AssignmentStatsDetailPage.tsx (Exercise). REFLEX chỉ có bảng
 * tổng hợp (đã nộp X/Y câu, điểm TB) — việc chấm bài vẫn làm ở ExamsPage như hiện tại. CONNECTION có
 * thêm tab "Phân tích câu hỏi" vì đã có sẵn dữ liệu đúng/sai thật.
 */
export default function ReviewVideoAssignmentStatsDetailPage() {
  const { t, i18n } = useTranslation("academic-homework");
  const reviewVideoTypeLabels: Record<string, string> = {
    REFLEX: t("shared.reviewVideoType.REFLEX"),
    CONNECTION: t("shared.reviewVideoType.CONNECTION")
  };
  const { assignmentId } = useParams<{ assignmentId: string }>();
  const navigate = useNavigate();
  const [tab, setTab] = useState<"students" | "questions">("students");
  const [studentStats, setStudentStats] = useState<ReviewVideoAssignmentStudentStatsResponse | null>(null);
  const [questionStats, setQuestionStats] = useState<ReviewVideoAssignmentQuestionStatsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expandedQuestionId, setExpandedQuestionId] = useState<number | null>(null);
  const [togglingLateSubmission, setTogglingLateSubmission] = useState(false);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror AssignmentStatsDetailPage.tsx.
  const [deadlineDate, setDeadlineDate] = useState("");
  const [deadlineTime, setDeadlineTime] = useState("");
  const [confirmDeadlineOpen, setConfirmDeadlineOpen] = useState(false);

  const numAssignmentId = assignmentId ? parseInt(assignmentId, 10) : null;
  const isConnection = studentStats?.assignment.videoType === "CONNECTION";

  /**
   * V165 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07) — bật/tắt lại "Cho phép nộp
   * bài muộn" ngay tại đây, mirror AssignmentStatsDetailPage.tsx (Exercise) — trả lời nhu cầu "lỡ ban
   * đầu không cho nộp muộn mà học sinh chưa xong thì sao".
   */
  const handleToggleLateSubmissionAllowed = async (checked: boolean) => {
    if (!numAssignmentId || !studentStats) return;
    setTogglingLateSubmission(true);
    setError(null);
    try {
      const deadline = checked && deadlineDate && deadlineTime ? `${deadlineDate}T${deadlineTime}` : null;
      await updateReviewVideoAssignmentLateSubmissionAllowed(numAssignmentId, checked, deadline);
      setStudentStats({ ...studentStats, assignment: { ...studentStats.assignment, lateSubmissionAllowed: checked, lateSubmissionDeadline: deadline } });
      if (!checked) {
        setDeadlineDate("");
        setDeadlineTime("");
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("shared.errors.loadResultsFailed"));
    } finally {
      setTogglingLateSubmission(false);
    }
  };

  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror AssignmentStatsDetailPage.tsx. */
  const saveLateSubmissionDeadline = async (date: string, time: string) => {
    if (!numAssignmentId || !studentStats) return;
    const deadline = date && time ? `${date}T${time}` : null;
    setTogglingLateSubmission(true);
    setError(null);
    try {
      await updateReviewVideoAssignmentLateSubmissionAllowed(numAssignmentId, true, deadline);
      setStudentStats({ ...studentStats, assignment: { ...studentStats.assignment, lateSubmissionDeadline: deadline } });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("shared.errors.loadResultsFailed"));
    } finally {
      setTogglingLateSubmission(false);
    }
  };

  const handleClearLateSubmissionDeadline = () => {
    setDeadlineDate("");
    setDeadlineTime("");
    saveLateSubmissionDeadline("", "");
  };

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror AssignmentStatsDetailPage.tsx:
   * xác nhận qua popup TRƯỚC KHI lưu, chỉ mở khi Giáo viên chủ động bấm nút "Xác nhận hạn chót".
   */
  const handleConfirmDeadlineChange = () => {
    saveLateSubmissionDeadline(deadlineDate, deadlineTime);
    setConfirmDeadlineOpen(false);
  };

  const handleCancelDeadlineChange = () => {
    setConfirmDeadlineOpen(false);
  };

  useEffect(() => {
    if (!numAssignmentId) return;
    setLoading(true);
    setError(null);
    getReviewVideoAssignmentStudentStats(numAssignmentId)
      .then((res) => {
        setStudentStats(res);
        const d = res.assignment.lateSubmissionDeadline;
        setDeadlineDate(d ? d.slice(0, 10) : "");
        setDeadlineTime(d ? d.slice(11, 16) : "");
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("shared.errors.loadResultsFailed")))
      .finally(() => setLoading(false));
  }, [numAssignmentId]);

  useEffect(() => {
    if (tab !== "questions" || questionStats || !numAssignmentId || !isConnection) return;
    getReviewVideoAssignmentQuestionStats(numAssignmentId)
      .then(setQuestionStats)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("shared.errors.loadQuestionAnalysisFailed")));
  }, [tab, numAssignmentId, questionStats, isConnection]);

  if (!studentStats) {
    return (
      <div className="space-y-4">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900 mb-4">
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

  const { assignment, students } = studentStats;
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror AssignmentStatsDetailPage.tsx.
  const pendingDeadlineIso = deadlineDate && deadlineTime.length === 5 ? `${deadlineDate}T${deadlineTime}` : null;
  const hasPendingDeadlineChange = pendingDeadlineIso != null && pendingDeadlineIso !== assignment.lateSubmissionDeadline?.slice(0, 16);

  return (
    <div className="space-y-6">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900">
        <ArrowLeft className="w-4 h-4" />
        {t("shared.back")}
      </button>

      <div>
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-bold font-display text-slate-900">{assignment.reviewVideoSetTitle}</h1>
          <Badge variant="info">{reviewVideoTypeLabels[assignment.videoType]}</Badge>
        </div>
        <p className="text-xs text-slate-500 mt-1">{assignment.reviewVideoSetCode}</p>
        <div className="flex items-center gap-3 mt-2">
          {assignment.dueAt && (
            <span className="text-xs text-slate-500">
              {t("reviewVideoDetail.dueAtLabel")}: {formatDateTime(assignment.dueAt, i18n.language)}
            </span>
          )}
          <label className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
            <input
              type="checkbox"
              checked={assignment.lateSubmissionAllowed}
              disabled={togglingLateSubmission}
              onChange={(e) => handleToggleLateSubmissionAllowed(e.target.checked)}
              className="rounded border-slate-300"
            />
            {t("reviewVideoDetail.lateSubmissionAllowedLabel")}
          </label>
        </div>
        {assignment.lateSubmissionAllowed && (
          <div className="flex items-center gap-2 mt-2">
            <span className="text-xs font-semibold text-slate-500">{t("reviewVideoDetail.lateSubmissionDeadlineLabel")}</span>
            <DatePicker
              value={deadlineDate}
              min={assignment.dueAt ? assignment.dueAt.slice(0, 10) : undefined}
              onChange={(value) => {
                setDeadlineDate(value);
                if (!value) {
                  setDeadlineTime("");
                  saveLateSubmissionDeadline("", "");
                  return;
                }
                if (!deadlineTime) setDeadlineTime("23:59");
              }}
            />
            <Time24Input
              value={deadlineTime}
              disabled={!deadlineDate || togglingLateSubmission}
              onChange={setDeadlineTime}
              className="bg-white border border-slate-200 text-xs px-2 py-1.5 rounded-lg focus:outline-none disabled:opacity-40"
            />
            {hasPendingDeadlineChange && (
              <Button variant="primary" size="sm" onClick={() => setConfirmDeadlineOpen(true)} disabled={togglingLateSubmission}>
                {t("reviewVideoDetail.confirmDeadline.reviewButton")}
              </Button>
            )}
            {(deadlineDate || deadlineTime) && (
              <button
                type="button"
                onClick={handleClearLateSubmissionDeadline}
                disabled={togglingLateSubmission}
                title={t("reviewVideoDetail.lateSubmissionDeadlineClear")}
                className="text-slate-400 hover:text-rose-600 disabled:opacity-40"
              >
                <XCircle className="w-4 h-4" />
              </button>
            )}
          </div>
        )}
      </div>

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-lg">{error}</div>}

      <Card padded={false} className="overflow-hidden">
        {isConnection && (
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
        )}

        <div className="p-5">
          {tab === "students" &&
            (loading ? (
              <p className="text-sm text-slate-500">{t("shared.loading")}</p>
            ) : students.length === 0 ? (
              <p className="text-sm text-slate-400 italic">{t("shared.noDataToShow")}</p>
            ) : (
              <TableContainer className="border-0 rounded-none">
                <thead>
                  <tr>
                    <Th>{t("shared.table.student")}</Th>
                    <Th className="text-center">{t("reviewVideoDetail.table.viewed")}</Th>
                    <Th className="text-center">{t("reviewVideoDetail.table.completed")}</Th>
                    {isConnection ? (
                      <>
                        <Th className="text-center">{t("reviewVideoDetail.table.quizScore")}</Th>
                        <Th className="text-center">{t("reviewVideoDetail.table.result")}</Th>
                      </>
                    ) : (
                      <>
                        <Th className="text-center">{t("reviewVideoDetail.table.submittedQuestions")}</Th>
                        <Th className="text-center">{t("reviewVideoDetail.table.averageScore")}</Th>
                        <Th className="text-center">{t("reviewVideoDetail.table.lateSubmission")}</Th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {students.map((s) => (
                    <tr key={s.studentId}>
                      <Td className="font-semibold text-slate-900">
                        {s.studentFullName} <span className="text-slate-400 font-mono text-[10px]">({s.studentCode})</span>
                      </Td>
                      <Td className="text-center">
                        {t("reviewVideoDetail.table.viewCountSuffix", { viewed: s.viewCount, required: s.requiredViewCount })}
                      </Td>
                      <Td className="text-center">
                        <Badge variant={s.completed ? "success" : "neutral"}>
                          {s.completed ? t("reviewVideoDetail.table.completedBadge") : t("reviewVideoDetail.table.notCompletedBadge")}
                        </Badge>
                      </Td>
                      {isConnection ? (
                        <>
                          <Td className="text-center">
                            {s.totalQuestions != null && s.totalQuestions > 0 ? `${s.correctCount}/${s.totalQuestions}` : "—"}
                          </Td>
                          <Td className="text-center">
                            {s.passed == null ? (
                              <span className="text-slate-300">—</span>
                            ) : (
                              <Badge variant={s.passed ? "success" : "danger"}>{s.passed ? t("shared.passed") : t("shared.notPassed")}</Badge>
                            )}
                          </Td>
                        </>
                      ) : (
                        <>
                          <Td className="text-center">
                            {s.answeredQuestionCount ?? 0}/{s.totalReflexQuestions ?? 0}
                          </Td>
                          <Td className="text-center">
                            {s.averageScore != null && s.averageMaxScore != null
                              ? `${s.averageScore}/${s.averageMaxScore}`
                              : t("reviewVideoDetail.table.notGraded")}
                          </Td>
                          <Td className="text-center">
                            {s.lateSubmission && <Badge variant="warning">{t("reviewVideoDetail.table.lateSubmissionBadge")}</Badge>}
                          </Td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </TableContainer>
            ))}

          {tab === "questions" && isConnection && (
            !questionStats ? (
              <p className="text-sm text-slate-500">{t("shared.loading")}</p>
            ) : questionStats.questions.length === 0 ? (
              <p className="text-sm text-slate-400 italic">{t("shared.noDataToShow")}</p>
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
            )
          )}
        </div>
      </Card>

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror AssignmentStatsDetailPage.tsx. */}
      <Modal
        open={confirmDeadlineOpen}
        onClose={handleCancelDeadlineChange}
        title={t("reviewVideoDetail.confirmDeadline.title")}
        footer={
          <>
            <button
              onClick={handleCancelDeadlineChange}
              className="bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 font-semibold text-xs px-4 py-2 rounded-lg transition-all"
            >
              {t("reviewVideoDetail.confirmDeadline.cancel")}
            </button>
            <button
              onClick={handleConfirmDeadlineChange}
              disabled={togglingLateSubmission}
              className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg transition-all disabled:opacity-50"
            >
              {t("reviewVideoDetail.confirmDeadline.confirmButton")}
            </button>
          </>
        }
      >
        <div className="flex items-start gap-3">
          <ShieldAlert className="w-8 h-8 text-amber-500 shrink-0" />
          <div className="text-xs text-slate-600 leading-relaxed">
            {pendingDeadlineIso &&
              t("reviewVideoDetail.confirmDeadline.description", { deadline: formatDateTime(pendingDeadlineIso, i18n.language) })}
          </div>
        </div>
      </Modal>
    </div>
  );
}

function QuestionRow({
  question,
  expanded,
  onToggle
}: {
  question: ReviewVideoAssignmentQuestionRow;
  expanded: boolean;
  onToggle: () => void;
}) {
  const { t } = useTranslation("academic-homework");
  return (
    <div className="border border-slate-200 rounded-lg overflow-hidden">
      <button onClick={onToggle} className="w-full flex items-center justify-between gap-3 p-2.5 text-left hover:bg-slate-50">
        <div className="flex items-center gap-2 min-w-0">
          {expanded ? <ChevronDown className="w-3.5 h-3.5 shrink-0 text-slate-400" /> : <ChevronRight className="w-3.5 h-3.5 shrink-0 text-slate-400" />}
          <span className="text-xs text-slate-700 truncate">
            {t("shared.questionRow.questionLabel", { order: question.displayOrder, text: question.prompt })}
            {question.reviewVideoTitle && (
              <span className="text-slate-400">
                {t("reviewVideoDetail.questionRow.titleSuffix", { title: question.reviewVideoTitle })}
              </span>
            )}
          </span>
        </div>
        <Badge
          variant={question.wrongRatePercent >= 50 ? "danger" : question.wrongRatePercent > 0 ? "warning" : "success"}
          className="shrink-0"
        >
          {t("shared.questionRow.wrongBadge", {
            wrongCount: question.wrongCount,
            answeredCount: question.answeredCount,
            percent: question.wrongRatePercent
          })}
        </Badge>
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
