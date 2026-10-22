import React, { useEffect, useState } from "react";
import { useApp } from "@/context/AppContext";
import { useParams, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ArrowLeft, ChevronDown, ChevronRight, Download, Eye, Flag, Pencil, Save, ShieldAlert, Star, UserCheck, XCircle } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import {
  ConnectionHistorySession,
  ReflexQuestionProgressHistoryEntry,
  ReviewVideoAssignmentQuestionRow,
  ReviewVideoAssignmentQuestionStatsResponse,
  ReviewVideoAssignmentStudentStatsResponse,
  exportReflexAssignmentData,
  getConnectionStudentHistory,
  getReflexStudentHistory,
  getReviewVideoAssignmentQuestionStats,
  getReviewVideoAssignmentStudentStats,
  reviewReflexStep,
  selectReflexAttempt,
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
import FloatingError from "@/components/ui/FloatingError";

/**
 * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — "Xem chi tiết" 1 BTVN Video
 * Ôn tập (REFLEX/CONNECTION), mirror AssignmentStatsDetailPage.tsx (Exercise). REFLEX chỉ có bảng
 * tổng hợp (đã nộp X/Y câu, điểm TB) — việc chấm bài vẫn làm ở ExamsPage như hiện tại. CONNECTION có
 * thêm tab "Phân tích câu hỏi" vì đã có sẵn dữ liệu đúng/sai thật.
 */
export default function ReviewVideoAssignmentStatsDetailPage() {
  // V202 — bật nộp muộn/xác nhận hạn chót và xuất thống kê đi theo quyền riêng.
  const { hasPermission } = useApp();
  const canConfirmDeadline = hasPermission("lms.exercise.deadline.confirm");
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
  // V191 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21) — nghe lại audio + xem AI chấm
  // theo từng lần làm (REFLEX), và xuất toàn bộ dữ liệu (audio + kết quả AI chấm) để train AI.
  const [viewingStudent, setViewingStudent] = useState<{ id: number; name: string } | null>(null);
  const [exportingReflexData, setExportingReflexData] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  // Tăng khi giáo viên chấm lại điểm trong popup lịch sử — nạp lại bảng thống kê để Điểm TB/Kết quả cập nhật ngay.
  const [statsReloadKey, setStatsReloadKey] = useState(0);

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

  /** V191 — xuất toàn bộ audio + kết quả AI chấm (mọi học sinh, mọi lần làm) của lần giao này thành ZIP. */
  const handleExportReflexData = async () => {
    if (!numAssignmentId) return;
    setExportingReflexData(true);
    setExportError(null);
    try {
      const blob = await exportReflexAssignmentData(numAssignmentId);
      downloadBlob(blob, `video-phan-xa-${numAssignmentId}.zip`);
    } catch (err) {
      setExportError(err instanceof ApiError ? err.message : t("reviewVideoDetail.exportReflexData.failed"));
    } finally {
      setExportingReflexData(false);
    }
  };

  useEffect(() => {
    if (!numAssignmentId) return;
    // Nạp lại sau khi chấm lại điểm (statsReloadKey > 0) thì không bật spinner để bảng/popup không nháy.
    if (statsReloadKey === 0) setLoading(true);
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
  }, [numAssignmentId, statsReloadKey]);

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
            <FloatingError message={error} />
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
        <p className="text-sm text-slate-500 mt-1">{assignment.reviewVideoSetCode}</p>
        <div className="flex items-center gap-3 mt-2">
          {assignment.dueAt && (
            <span className="text-sm text-slate-500">
              {t("reviewVideoDetail.dueAtLabel")}: {formatDateTime(assignment.dueAt, i18n.language)}
            </span>
          )}
          <label className="flex items-center gap-1.5 text-sm font-semibold text-slate-600">
            <input
              type="checkbox"
              checked={assignment.lateSubmissionAllowed}
              disabled={togglingLateSubmission || !canConfirmDeadline}
              onChange={(e) => handleToggleLateSubmissionAllowed(e.target.checked)}
              className="rounded border-slate-300"
            />
            {t("reviewVideoDetail.lateSubmissionAllowedLabel")}
          </label>
        </div>
        {assignment.lateSubmissionAllowed && canConfirmDeadline && (
          <div className="flex items-center gap-2 mt-2">
            <span className="text-sm font-semibold text-slate-500">{t("reviewVideoDetail.lateSubmissionDeadlineLabel")}</span>
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
              className="bg-white border border-slate-200 text-sm px-2 py-1.5 rounded-lg focus:outline-none disabled:opacity-40"
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

      <FloatingError message={error} onClose={() => setError(null)} />
      <FloatingError message={exportError} onClose={() => setExportError(null)} />

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

        {!isConnection && (
          <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex justify-end">
            <Button variant="secondary" size="sm" onClick={handleExportReflexData} disabled={exportingReflexData}>
              <Download className="w-3.5 h-3.5" />
              {exportingReflexData ? t("reviewVideoDetail.exportReflexData.exporting") : t("reviewVideoDetail.exportReflexData.button")}
            </Button>
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
                    {isConnection && <Th className="text-center">{t("reviewVideoDetail.table.viewed")}</Th>}
                    <Th className="text-center">{t("reviewVideoDetail.table.completed")}</Th>
                    {isConnection ? (
                      <>
                        <Th className="text-center">{t("reviewVideoDetail.table.quizScore")}</Th>
                        <Th className="text-center">{t("reviewVideoDetail.table.result")}</Th>
                        <Th className="text-center">{t("reviewVideoDetail.table.viewDetail")}</Th>
                      </>
                    ) : (
                      <>
                        <Th className="text-center">{t("reviewVideoDetail.table.submittedQuestions")}</Th>
                        <Th className="text-center">{t("reviewVideoDetail.table.result")}</Th>
                        <Th className="text-center">{t("reviewVideoDetail.table.averageScore")}</Th>
                        <Th className="text-center">{t("reviewVideoDetail.table.lateSubmission")}</Th>
                        <Th className="text-center">{t("reviewVideoDetail.table.viewDetail")}</Th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {students.map((s) => (
                    <tr key={s.studentId}>
                      <Td className="font-semibold text-slate-900">
                        {s.studentFullName} <span className="text-slate-400 font-mono text-[12px]">({s.studentCode})</span>
                      </Td>
                      {isConnection && (
                        <Td className="text-center">
                          {t("reviewVideoDetail.table.viewCountSuffix", { viewed: s.viewCount, required: s.requiredViewCount })}
                        </Td>
                      )}
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
                          <Td className="text-center">
                            <button
                              type="button"
                              onClick={() => setViewingStudent({ id: s.studentId, name: s.studentFullName })}
                              className="text-slate-400 hover:text-brand-orange"
                              title={t("reviewVideoDetail.table.viewDetail")}
                            >
                              <Eye className="w-4 h-4 inline" />
                            </button>
                          </Td>
                        </>
                      ) : (
                        <>
                          <Td className="text-center">
                            {s.answeredQuestionCount ?? 0}/{s.totalReflexQuestions ?? 0}
                          </Td>
                          <Td className="text-center">
                            {s.passed == null ? (
                              <span className="text-slate-300">—</span>
                            ) : (
                              <Badge variant={s.passed ? "success" : "danger"}>{s.passed ? t("shared.passed") : t("shared.notPassed")}</Badge>
                            )}
                          </Td>
                          <Td className="text-center">
                            {s.averageScore != null && s.averageMaxScore != null
                              ? `${s.averageScore}/${s.averageMaxScore}`
                              : t("reviewVideoDetail.table.notGraded")}
                          </Td>
                          <Td className="text-center">
                            {s.lateSubmission && <Badge variant="warning">{t("reviewVideoDetail.table.lateSubmissionBadge")}</Badge>}
                          </Td>
                          <Td className="text-center whitespace-nowrap">
                            {/* V198 — lần ghi âm cần giáo viên soát điểm Ngữ pháp (AI chỉ phiên âm 1 lượt, có thể nghe nhầm) */}
                            {s.grammarReviewRequired && (
                              <Badge variant="danger" className="mr-1.5">
                                <ShieldAlert className="w-3 h-3 inline mr-0.5" />
                                {t("reviewVideoDetail.table.grammarReviewBadge")}
                              </Badge>
                            )}
                            <button
                              type="button"
                              onClick={() => setViewingStudent({ id: s.studentId, name: s.studentFullName })}
                              className="text-slate-400 hover:text-brand-orange"
                              title={t("reviewVideoDetail.table.viewDetail")}
                            >
                              <Eye className="w-4 h-4 inline" />
                            </button>
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
              className="bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 font-semibold text-sm px-4 py-2 rounded-lg transition-all"
            >
              {t("reviewVideoDetail.confirmDeadline.cancel")}
            </button>
            <button
              onClick={handleConfirmDeadlineChange}
              disabled={togglingLateSubmission}
              className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-sm px-4 py-2 rounded-lg transition-all disabled:opacity-50"
            >
              {t("reviewVideoDetail.confirmDeadline.confirmButton")}
            </button>
          </>
        }
      >
        <div className="flex items-start gap-3">
          <ShieldAlert className="w-8 h-8 text-amber-500 shrink-0" />
          <div className="text-sm text-slate-600 leading-relaxed">
            {pendingDeadlineIso &&
              t("reviewVideoDetail.confirmDeadline.description", { deadline: formatDateTime(pendingDeadlineIso, i18n.language) })}
          </div>
        </div>
      </Modal>

      {viewingStudent && numAssignmentId && isConnection && (
        <ConnectionStudentHistoryModal
          assignmentId={numAssignmentId}
          studentId={viewingStudent.id}
          studentName={viewingStudent.name}
          onClose={() => setViewingStudent(null)}
        />
      )}
      {viewingStudent && numAssignmentId && !isConnection && (
        <ReflexStudentHistoryModal
          assignmentId={numAssignmentId}
          studentId={viewingStudent.id}
          studentName={viewingStudent.name}
          onClose={() => setViewingStudent(null)}
          onChanged={() => setStatsReloadKey((k) => k + 1)}
        />
      )}
    </div>
  );
}

/**
 * 2026-10-17 (đã xác nhận với người dùng) — Video kết nối: chi tiết TỪNG lượt làm của 1 học sinh (thời gian
 * xem, số lần nộp trắc nghiệm, từng câu chọn gì/đúng sai), kể cả các lượt đã lưu trữ khi học sinh
 * "Làm lại từ đầu". Mirror ReflexStudentHistoryModal.
 */
function ConnectionStudentHistoryModal({
  assignmentId,
  studentId,
  studentName,
  onClose
}: {
  assignmentId: number;
  studentId: number;
  studentName: string;
  onClose: () => void;
}) {
  const { t, i18n } = useTranslation("academic-homework");
  const [sessions, setSessions] = useState<ConnectionHistorySession[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setLoadError(null);
    getConnectionStudentHistory(assignmentId, studentId)
      .then((res) => setSessions(res.sessions))
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : t("reviewVideoDetail.connectionHistoryModal.loadFailed")))
      .finally(() => setLoading(false));
  }, [assignmentId, studentId]);

  return (
    <Modal open onClose={onClose} title={t("reviewVideoDetail.connectionHistoryModal.title", { studentName })}>
      <div className="max-h-[70vh] overflow-y-auto space-y-3">
        {loading ? (
          <p className="text-sm text-slate-500">{t("reviewVideoDetail.connectionHistoryModal.loading")}</p>
        ) : loadError ? (
          <FloatingError message={loadError} />
        ) : sessions.length === 0 ? (
          <p className="text-sm text-slate-400 italic">{t("reviewVideoDetail.connectionHistoryModal.empty")}</p>
        ) : (
          sessions.map((session) => (
            <div key={session.watchSessionId} className={`border rounded-lg p-3 space-y-2 ${session.archived ? "border-slate-200 bg-slate-50" : "border-slate-300"}`}>
              <div className="flex items-center justify-between gap-2 flex-wrap">
                <span className="text-sm font-semibold text-slate-700">
                  {t("reviewVideoDetail.connectionHistoryModal.sessionLabel", { number: session.sessionNumber })} · {session.videoTitle}
                </span>
                <span className="flex items-center gap-1.5">
                  <Badge variant={session.archived ? "neutral" : "success"}>
                    {session.archived ? t("reviewVideoDetail.connectionHistoryModal.archivedBadge") : t("reviewVideoDetail.connectionHistoryModal.currentBadge")}
                  </Badge>
                  {session.quizCompletedAt && (
                    <Badge variant={session.quizPassed ? "success" : "danger"}>
                      {session.quizPassed ? t("reviewVideoDetail.connectionHistoryModal.passedBadge") : t("reviewVideoDetail.connectionHistoryModal.failedBadge")}
                    </Badge>
                  )}
                </span>
              </div>
              <p className="text-[12px] text-slate-500">
                {t("reviewVideoDetail.connectionHistoryModal.startedAt", { time: formatDateTime(session.startedAt, i18n.language) })} ·{" "}
                {t("reviewVideoDetail.connectionHistoryModal.watched", { watched: session.watchedSeconds, duration: session.durationSeconds })}
                {!session.qualified && ` · ${t("reviewVideoDetail.connectionHistoryModal.notWatchedFully")}`}
                {session.qualified && !session.quizCompletedAt && ` · ${t("reviewVideoDetail.connectionHistoryModal.quizNotSubmitted")}`}
                {session.quizAttemptCount > 0 && ` · ${t("reviewVideoDetail.connectionHistoryModal.quizAttempts", { count: session.quizAttemptCount })}`}
              </p>
              {session.answers.length > 0 && (
                <ul className="space-y-1.5">
                  {session.answers.map((a) => (
                    <li key={a.questionOrder} className="text-[13px] bg-white border border-slate-100 rounded-md p-2">
                      <p className="font-medium text-slate-700">
                        {a.questionOrder}. {a.prompt}
                      </p>
                      <p className={a.correct ? "text-emerald-700" : "text-rose-700"}>
                        {t("reviewVideoDetail.connectionHistoryModal.selected", { answer: `${a.selectedLabel}. ${a.selectedContent}` })}
                      </p>
                      {!a.correct && a.correctLabel && (
                        <p className="text-slate-500">
                          {t("reviewVideoDetail.connectionHistoryModal.correctAnswer", { answer: `${a.correctLabel}. ${a.correctContent ?? ""}` })}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))
        )}
      </div>
    </Modal>
  );
}

/**
 * V191 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21) — UC-23b (Video phản xạ): nghe lại
 * audio + xem kết quả AI chấm theo TỪNG lần làm của 1 học sinh, gom theo câu hỏi, mỗi câu 2 cột Viết | Ghi âm
 * (mới nhất lên trên).
 *
 * Chấm lần 2 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-19): điểm CHÍNH THỨC của mỗi bước luôn là
 * lần nộp mới nhất (cờ "Điểm chính thức") — giáo viên chỉnh điểm + nhận xét ngay trên lần đó nếu thấy AI chấm
 * chưa đúng; điểm AI gốc vẫn được hiển thị để đối chiếu. Các lần cũ chỉ để xem lại, không tính điểm.
 */
function ReflexStudentHistoryModal({
  assignmentId,
  studentId,
  studentName,
  onClose,
  onChanged
}: {
  assignmentId: number;
  studentId: number;
  studentName: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { t, i18n } = useTranslation("academic-homework");
  const [entries, setEntries] = useState<ReflexQuestionProgressHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = (showSpinner: boolean) => {
    if (showSpinner) setLoading(true);
    setLoadError(null);
    getReflexStudentHistory(assignmentId, studentId)
      .then(setEntries)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : t("reviewVideoDetail.reflexHistoryModal.loadFailed")))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [assignmentId, studentId]);

  const questionGroups = React.useMemo(() => {
    const byQuestion = new Map<
      number,
      { order: number; prompt: string; writing: ReflexQuestionProgressHistoryEntry[]; speaking: ReflexQuestionProgressHistoryEntry[] }
    >();
    for (const e of entries) {
      const group = byQuestion.get(e.questionId) ?? { order: e.questionDisplayOrder, prompt: e.questionPrompt, writing: [], speaking: [] };
      (e.attemptType === "SPEAKING" ? group.speaking : group.writing).push(e);
      byQuestion.set(e.questionId, group);
    }
    const newestFirst = (a: ReflexQuestionProgressHistoryEntry, b: ReflexQuestionProgressHistoryEntry) => b.attemptNumber - a.attemptNumber;
    return [...byQuestion.values()]
      .map((g) => ({ ...g, writing: g.writing.sort(newestFirst), speaking: g.speaking.sort(newestFirst) }))
      .sort((a, b) => a.order - b.order);
  }, [entries]);

  const handleSaved = () => {
    load(false);
    onChanged();
  };

  return (
    <Modal open onClose={onClose} size="xl" title={t("reviewVideoDetail.reflexHistoryModal.title", { studentName })}>
      <div className="max-h-[78vh] overflow-y-auto space-y-4 pr-1">
        <p className="text-[13px] text-slate-500 flex items-center gap-1.5">
          <Flag className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
          {t("reviewVideoDetail.reflexHistoryModal.officialHint")}
        </p>
        {loading ? (
          <p className="text-sm text-slate-500">{t("reviewVideoDetail.reflexHistoryModal.loading")}</p>
        ) : loadError ? (
          <FloatingError message={loadError} />
        ) : questionGroups.length === 0 ? (
          <p className="text-sm text-slate-400 italic">{t("reviewVideoDetail.reflexHistoryModal.empty")}</p>
        ) : (
          questionGroups.map((group) => (
            <div key={group.order} className="border border-slate-200 rounded-lg p-3 space-y-3">
              <p className="text-sm font-semibold text-slate-700">
                {t("reviewVideoDetail.reflexHistoryModal.questionLabel", { order: group.order, prompt: group.prompt })}
              </p>
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
                {([
                  ["writing", group.writing],
                  ["speaking", group.speaking]
                ] as const).map(([kind, list]) => (
                  <div key={kind} className="space-y-2 min-w-0">
                    <p className="text-[12px] font-bold uppercase tracking-wider text-slate-400">
                      {t(`reviewVideoDetail.reflexHistoryModal.column.${kind}`)}
                    </p>
                    {list.length === 0 ? (
                      <p className="text-[13px] text-slate-400 italic">{t("reviewVideoDetail.reflexHistoryModal.noAttemptYet")}</p>
                    ) : (
                      list.map((entry) => (
                        <ReflexHistoryEntryRow
                          key={`${entry.attemptType}-${entry.attemptNumber}`}
                          entry={entry}
                          language={i18n.language}
                          onSaved={handleSaved}
                        />
                      ))
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))
        )}
      </div>
    </Modal>
  );
}

/** Hiển thị văn bản AI đã đánh dấu lỗi bằng {{err}}...{{/err}} — đoạn lỗi được bôi nền đỏ nhạt + gạch chân. */
function MarkedText({ text }: { text: string }) {
  const parts: React.ReactNode[] = [];
  const pattern = /\{\{err\}\}([\s\S]*?)\{\{\/err\}\}/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) parts.push(text.slice(last, match.index));
    parts.push(
      <mark key={match.index} className="bg-rose-100 text-rose-700 rounded px-0.5 underline decoration-rose-400">
        {match[1]}
      </mark>
    );
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return <span className="whitespace-pre-wrap">{parts}</span>;
}

function ReflexHistoryEntryRow({
  entry,
  language,
  onSaved
}: {
  entry: ReflexQuestionProgressHistoryEntry;
  language: string;
  onSaved: () => void;
}) {
  const { t } = useTranslation("academic-homework");
  const isSpeaking = entry.attemptType === "SPEAKING";
  const canEdit = entry.official && entry.progressId != null && entry.score != null;
  const [editing, setEditing] = useState(false);
  const [score, setScore] = useState("");
  const [feedback, setFeedback] = useState("");
  // Điểm từng band (2026-10-22): lần có band thì mặc định sửa theo band, điểm tổng do server tính lại.
  const hasBands = (entry.criteriaScores?.length ?? 0) > 0;
  const [bandMode, setBandMode] = useState(hasBands);
  const [bandInputs, setBandInputs] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const startEditing = () => {
    setScore(entry.score != null ? String(entry.score) : "");
    setFeedback(entry.feedback ?? "");
    setBandMode(hasBands);
    setBandInputs((entry.criteriaScores ?? []).map((c) => String(c.percent)));
    setSaveError(null);
    setEditing(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (entry.progressId == null) return;
    setSaving(true);
    setSaveError(null);
    try {
      const bands = bandMode
        ? (entry.criteriaScores ?? []).map((c, i) => ({ criterion: c.criterion, percent: Math.round(parseFloat(bandInputs[i])) }))
        : undefined;
      await reviewReflexStep(entry.progressId, entry.attemptType, {
        ...(bands ? { criteriaScores: bands } : { score: parseFloat(score) }),
        feedback: feedback.trim() ? feedback.trim() : undefined
      });
      setEditing(false);
      onSaved();
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : t("reviewVideoDetail.reflexHistoryModal.review.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  const markedText = isSpeaking ? entry.transcript : entry.markedAnswer ?? entry.answerText;

  // V217 — chọn lần cũ (chưa là điểm chính thức) làm kết quả chính thức của bước.
  const canSelect = !entry.official && entry.progressId != null && entry.score != null;
  const [selecting, setSelecting] = useState(false);
  const [selectError, setSelectError] = useState<string | null>(null);
  const handleSelect = async () => {
    if (entry.progressId == null) return;
    setSelecting(true);
    setSelectError(null);
    try {
      await selectReflexAttempt(entry.progressId, entry.attemptType, entry.attemptNumber);
      onSaved();
    } catch (err) {
      setSelectError(err instanceof ApiError ? err.message : t("reviewVideoDetail.reflexHistoryModal.select.failed"));
    } finally {
      setSelecting(false);
    }
  };

  return (
    <div
      className={`rounded-lg p-2.5 space-y-1.5 border ${
        entry.official ? "bg-emerald-50/40 border-emerald-200" : "bg-slate-50 border-slate-100 opacity-90"
      }`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-slate-600 flex items-center gap-1.5">
          {isSpeaking
            ? t("reviewVideoDetail.reflexHistoryModal.speakingAttempt", { attempt: entry.attemptNumber })
            : t("reviewVideoDetail.reflexHistoryModal.writingAttempt", { attempt: entry.attemptNumber })}
          {entry.official ? (
            <span className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-700 bg-emerald-100 rounded-full px-2 py-0.5">
              <Flag className="w-3 h-3" /> {t("reviewVideoDetail.reflexHistoryModal.officialBadge")}
            </span>
          ) : (
            <span className="text-[11px] font-semibold text-slate-400 bg-slate-100 rounded-full px-2 py-0.5">
              {t("reviewVideoDetail.reflexHistoryModal.notCountedBadge")}
            </span>
          )}
          {entry.official && entry.selectedByTeacher && (
            <span className="inline-flex items-center gap-1 text-[11px] font-bold text-amber-700 bg-amber-100 rounded-full px-2 py-0.5">
              <Star className="w-3 h-3" /> {t("reviewVideoDetail.reflexHistoryModal.select.chosenBadge")}
            </span>
          )}
          {entry.reviewed && (
            <span className="inline-flex items-center gap-1 text-[11px] font-bold text-violet-700 bg-violet-100 rounded-full px-2 py-0.5">
              <UserCheck className="w-3 h-3" /> {t("reviewVideoDetail.reflexHistoryModal.reviewedBadge")}
            </span>
          )}
        </span>
        <span className="text-[13px] font-semibold text-slate-600">
          {entry.score != null && entry.maxScore != null
            ? t("reviewVideoDetail.reflexHistoryModal.score", { score: entry.score, maxScore: entry.maxScore })
            : t("reviewVideoDetail.reflexHistoryModal.notGradedYet")}
          {entry.reviewed && entry.aiScore != null && (
            <span className="ml-1.5 font-normal text-slate-400">
              {t("reviewVideoDetail.reflexHistoryModal.aiOriginalScore", { score: entry.aiScore })}
            </span>
          )}
        </span>
      </div>
      {entry.gradedAt && <p className="text-[12px] text-slate-400">{t("reviewVideoDetail.reflexHistoryModal.gradedAt", { time: formatDateTime(entry.gradedAt, language) })}</p>}
      {entry.reviewed && (
        <p className="text-[12px] text-violet-600">
          {t("reviewVideoDetail.reflexHistoryModal.reviewedBy", {
            name: entry.reviewerName ?? "—",
            time: entry.reviewedAt ? formatDateTime(entry.reviewedAt, language) : ""
          })}
        </p>
      )}

      {isSpeaking && entry.grammarReviewRequired && (
        <div className="text-[13px] text-rose-700 bg-rose-50 border border-rose-100 rounded-md p-2 space-y-1">
          <p className="font-semibold">
            <ShieldAlert className="w-3.5 h-3.5 inline mr-1" />
            {t("reviewVideoDetail.reflexHistoryModal.grammarReviewTitle")}
          </p>
          <p>{t("reviewVideoDetail.reflexHistoryModal.grammarReviewBody")}</p>
          {entry.grammarReviewQuotes.length > 0 && (
            <ul className="list-disc list-inside font-mono">
              {entry.grammarReviewQuotes.map((q, i) => (
                <li key={`${q}-${i}`}>{q}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {isSpeaking && entry.recordingFilter != null && (
        <p className="text-[12px] text-slate-400">
          {entry.recordingFilter
            ? t("reviewVideoDetail.reflexHistoryModal.recordingFiltered")
            : t("reviewVideoDetail.reflexHistoryModal.recordingRaw")}
        </p>
      )}

      {isSpeaking &&
        (entry.audioUrl ? (
          <audio controls src={entry.audioUrl} className="w-full h-8" />
        ) : (
          <p className="text-[13px] text-slate-400 italic">{t("reviewVideoDetail.reflexHistoryModal.audioNotAvailable")}</p>
        ))}

      {markedText && (
        <p className="text-sm text-slate-700">
          <span className="font-semibold">
            {isSpeaking ? t("reviewVideoDetail.reflexHistoryModal.transcriptLabel") : t("reviewVideoDetail.reflexHistoryModal.answerTextLabel")}:{" "}
          </span>
          <MarkedText text={markedText} />
        </p>
      )}

      {entry.feedback && (
        <p className="text-sm text-slate-600">
          <span className="font-semibold">{t("reviewVideoDetail.reflexHistoryModal.feedbackLabel")}: </span>
          {entry.feedback}
        </p>
      )}
      {entry.reviewed && entry.aiFeedback && (
        <p className="text-[13px] text-slate-400">
          <span className="font-semibold">{t("reviewVideoDetail.reflexHistoryModal.aiOriginalFeedbackLabel")}: </span>
          {entry.aiFeedback}
        </p>
      )}

      {/* V204 (bổ sung ngoài SDD gốc, bản bàn giao 30/9, §D.5) — "cách luyện" đã hiện cho học sinh ở lần
          chấm này, tách khỏi feedback, để giáo viên đối chiếu khi cần. */}
      {entry.hint && (
        <p className="text-sm text-teal-700">
          <span className="font-semibold">{t("reviewVideoDetail.reflexHistoryModal.hintLabel")}: </span>
          {entry.hint}
        </p>
      )}

      {hasBands && entry.criteriaScores && (
        <div className="text-[13px] text-slate-600">
          <span className="font-semibold">{t("reviewVideoDetail.reflexHistoryModal.criteriaLabel")}: </span>
          <ul className="list-disc list-inside">
            {entry.criteriaScores.map((c) => {
              const aiPercent = entry.aiCriteriaScores?.find((a) => a.criterion === c.criterion)?.percent;
              return (
                <li key={c.criterion}>
                  {c.criterion}: {c.percent}%
                  {aiPercent != null && aiPercent !== c.percent && (
                    <span className="ml-1.5 text-slate-400">
                      {t("reviewVideoDetail.reflexHistoryModal.review.aiBand", { percent: aiPercent })}
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {canSelect && (
        <div className="pt-1" data-testid="reflex-select-attempt">
          <button
            type="button"
            disabled={selecting}
            onClick={handleSelect}
            className="inline-flex items-center gap-1 text-[13px] font-bold text-amber-700 hover:underline cursor-pointer disabled:opacity-60"
          >
            <Star className="w-3.5 h-3.5" /> {selecting ? t("reviewVideoDetail.reflexHistoryModal.select.selecting") : t("reviewVideoDetail.reflexHistoryModal.select.button")}
          </button>
          {selectError && <p className="text-[13px] text-rose-600">{selectError}</p>}
        </div>
      )}

      {canEdit &&
        (editing ? (
          <form onSubmit={handleSave} className="space-y-2 pt-2 border-t border-emerald-100">
            {bandMode ? (
              <div className="space-y-1.5" data-testid="reflex-band-editor">
                <p className="text-[12px] font-bold uppercase text-slate-500">{t("reviewVideoDetail.reflexHistoryModal.review.bandsLabel")}</p>
                {(entry.criteriaScores ?? []).map((c, i) => (
                  <div key={c.criterion} className="flex items-center gap-2">
                    <label className="text-sm text-slate-600 flex-1 min-w-0">{c.criterion}</label>
                    <input
                      type="number"
                      required
                      min={0}
                      max={100}
                      step={1}
                      value={bandInputs[i] ?? ""}
                      onChange={(ev) => setBandInputs((prev) => prev.map((v, k) => (k === i ? ev.target.value : v)))}
                      className="w-20 bg-white border border-slate-200 text-sm px-2 py-1.5 rounded-lg focus:outline-none"
                    />
                    <span className="text-sm text-slate-400">%</span>
                  </div>
                ))}
                <p className="text-[12px] text-slate-400">{t("reviewVideoDetail.reflexHistoryModal.review.totalAutoHint")}</p>
                <button
                  type="button"
                  onClick={() => setBandMode(false)}
                  className="text-[12px] font-semibold text-slate-500 hover:underline cursor-pointer"
                >
                  {t("reviewVideoDetail.reflexHistoryModal.review.totalOnly")}
                </button>
              </div>
            ) : (
              <div className="space-y-1.5">
                <div className="flex items-center gap-2">
                  <label className="text-[12px] font-bold uppercase text-slate-500 shrink-0">
                    {t("reviewVideoDetail.reflexHistoryModal.review.scoreLabel", { max: entry.maxScore ?? 100 })}
                  </label>
                  <input
                    type="number"
                    required
                    min={0}
                    max={entry.maxScore ?? 100}
                    step={0.1}
                    value={score}
                    onChange={(ev) => setScore(ev.target.value)}
                    className="w-24 bg-white border border-slate-200 text-sm px-2 py-1.5 rounded-lg focus:outline-none"
                  />
                </div>
                {hasBands && (
                  <button
                    type="button"
                    onClick={() => setBandMode(true)}
                    className="text-[12px] font-semibold text-slate-500 hover:underline cursor-pointer"
                  >
                    {t("reviewVideoDetail.reflexHistoryModal.review.editBands")}
                  </button>
                )}
              </div>
            )}
            <textarea
              rows={2}
              value={feedback}
              onChange={(ev) => setFeedback(ev.target.value)}
              placeholder={t("reviewVideoDetail.reflexHistoryModal.review.feedbackPlaceholder")}
              className="w-full bg-white border border-slate-200 text-sm px-2 py-1.5 rounded-lg focus:outline-none"
            />
            {saveError && <p className="text-[13px] text-rose-600">{saveError}</p>}
            <div className="flex gap-2">
              <Button type="submit" variant="primary" disabled={saving} className="!py-1.5">
                <Save className="w-4 h-4" />
                {saving ? t("reviewVideoDetail.reflexHistoryModal.review.saving") : t("reviewVideoDetail.reflexHistoryModal.review.save")}
              </Button>
              <Button type="button" variant="secondary" disabled={saving} onClick={() => setEditing(false)} className="!py-1.5">
                {t("reviewVideoDetail.reflexHistoryModal.review.cancel")}
              </Button>
            </div>
          </form>
        ) : (
          <div className="pt-1">
            <button
              type="button"
              onClick={startEditing}
              className="inline-flex items-center gap-1 text-[13px] font-bold text-brand-orange hover:underline cursor-pointer"
            >
              <Pencil className="w-3.5 h-3.5" /> {t("reviewVideoDetail.reflexHistoryModal.review.edit")}
            </button>
          </div>
        ))}
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
          <span className="text-sm text-slate-700 truncate">
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
            <p className="text-[13px] text-slate-400 italic">{t("shared.questionRow.noWrongStudents")}</p>
          ) : (
            <ul className="text-[13px] text-slate-600 space-y-1">
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
