import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ArrowLeft, Download, Eye, ShieldAlert, XCircle } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import {
  ExerciseAssignmentQuestionRow,
  ExerciseAssignmentStudentStatsResponse,
  exportHomeworkBatchStats,
  getExerciseAssignmentQuestionStats,
  getHomeworkBatchStudentStats,
  updateHomeworkBatchLateSubmissionAllowed
} from "../api";
import { QuestionAnalysisChart, QuestionRow, StudentDetailModal } from "./AssignmentStatsDetailPage";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Tabs from "@/components/ui/Tabs";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import { formatDateTime } from "@/lib/i18nFormat";
import DatePicker from "@/components/ui/DatePicker";
import Time24Input from "@/components/ui/Time24Input";
import Modal from "@/components/ui/Modal";

const studentStatusVariants: Record<string, BadgeVariant> = {
  CHUA_LAM: "neutral",
  DANG_LAM: "info",
  DA_NOP: "success",
  TRE_HAN: "warning"
};

interface MemberQuestions {
  title: string;
  questions: ExerciseAssignmentQuestionRow[];
}

/**
 * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — trang chi tiết CỘNG DỒN cả
 * "Lô giao BTVN theo kỹ năng" (nhiều Bài cùng Lesson+kỹ năng giao chung 1 lần ở UC-21), mở từ nút
 * "Xem chi tiết" ở dòng tổng hợp trên HomeworkStatsPage — khác với mở rộng xem từng Bài con (vẫn dùng
 * AssignmentStatsDetailPage như cũ, không đổi). Tab "Kết quả học sinh" hiện điểm/Đạt CỘNG DỒN theo
 * đúng công thức đã chốt (tổng điểm/tổng điểm tối đa, ngưỡng 70%, xem ExerciseReportService#
 * getBatchStudentStats). Tab "Phân tích câu hỏi" ghép lại từ chính API phân tích câu hỏi của TỪNG Bài
 * con thật (không có endpoint cộng dồn riêng — mỗi Bài vẫn là 1 Exercise độc lập, không clone câu
 * hỏi), chia theo tiêu đề từng Bài.
 *
 * V165 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07) — thêm cột "Xem chi tiết" (icon
 * mắt) mỗi dòng học sinh, TRƯỚC ĐÂY chưa có vì 1 học sinh có N lượt làm (1/Bài) chứ không phải 1 lượt
 * duy nhất như bản giao lẻ. Tái dùng {@link StudentDetailModal} (đã tổng quát hoá để nhận
 * `members: StudentDetailModalMember[]` thay vì 1 `exerciseId`/`assignmentId` cố định) — truyền cả N
 * Bài trong Lô, modal tự thêm tab để Giáo viên chọn xem đúng Bài nào (điểm/lượt làm/đáp án riêng của
 * Bài đó — không phải số liệu cộng dồn cả Lô ở bảng ngoài).
 */
export default function BatchStatsDetailPage() {
  const { t, i18n } = useTranslation("academic-homework");
  const studentStatusLabels: Record<string, string> = {
    CHUA_LAM: t("shared.studentStatus.CHUA_LAM"),
    DANG_LAM: t("shared.studentStatus.DANG_LAM"),
    DA_NOP: t("shared.studentStatus.DA_NOP"),
    TRE_HAN: t("shared.studentStatus.TRE_HAN")
  };
  const { batchId } = useParams<{ batchId: string }>();
  const navigate = useNavigate();
  const [tab, setTab] = useState<"students" | "questions">("students");
  const [studentStats, setStudentStats] = useState<ExerciseAssignmentStudentStatsResponse | null>(null);
  const [questionsByMember, setQuestionsByMember] = useState<MemberQuestions[] | null>(null);
  const [expandedQuestionKey, setExpandedQuestionKey] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [togglingLateSubmission, setTogglingLateSubmission] = useState(false);
  const [detailStudentId, setDetailStudentId] = useState<number | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror AssignmentStatsDetailPage.tsx.
  const [deadlineDate, setDeadlineDate] = useState("");
  const [deadlineTime, setDeadlineTime] = useState("");
  const [confirmDeadlineOpen, setConfirmDeadlineOpen] = useState(false);

  const numBatchId = batchId ? parseInt(batchId, 10) : null;

  /**
   * V165 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07) — bật/tắt lại "Cho phép nộp
   * bài muộn" cho CẢ LÔ (N Bài) cùng lúc ngay tại đây, mirror AssignmentStatsDetailPage.tsx nhưng gọi
   * endpoint theo Lô — trả lời nhu cầu "lỡ ban đầu không cho nộp muộn mà học sinh chưa xong thì sao"
   * khi BTVN được giao theo Lô (mặc định từ V150), không phải 1 Bài lẻ.
   */
  const handleToggleLateSubmissionAllowed = async (checked: boolean) => {
    if (!numBatchId || !studentStats) return;
    setTogglingLateSubmission(true);
    setError(null);
    try {
      const deadline = checked && deadlineDate && deadlineTime ? `${deadlineDate}T${deadlineTime}` : null;
      await updateHomeworkBatchLateSubmissionAllowed(numBatchId, checked, deadline);
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
    if (!numBatchId || !studentStats) return;
    const deadline = date && time ? `${date}T${time}` : null;
    setTogglingLateSubmission(true);
    setError(null);
    try {
      await updateHomeworkBatchLateSubmissionAllowed(numBatchId, true, deadline);
      setStudentStats({ ...studentStats, assignment: { ...studentStats.assignment, lateSubmissionDeadline: deadline } });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("shared.errors.loadResultsFailed"));
    } finally {
      setTogglingLateSubmission(false);
    }
  };

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror AssignmentStatsDetailPage.tsx:
   * xác nhận qua popup TRƯỚC KHI lưu (ảnh hưởng CẢ LÔ — N học sinh cùng lúc), chỉ mở khi Giáo viên chủ
   * động bấm nút "Xác nhận hạn chót" (xem hasPendingDeadlineChange), không tự mở theo từng ký tự gõ.
   */
  const handleConfirmDeadlineChange = () => {
    saveLateSubmissionDeadline(deadlineDate, deadlineTime);
    setConfirmDeadlineOpen(false);
  };

  const handleCancelDeadlineChange = () => setConfirmDeadlineOpen(false);

  const handleClearLateSubmissionDeadline = () => {
    setDeadlineDate("");
    setDeadlineTime("");
    saveLateSubmissionDeadline("", "");
  };

  const handleExport = async () => {
    if (!numBatchId) return;
    setExporting(true);
    try {
      const blob = await exportHomeworkBatchStats(numBatchId);
      downloadBlob(blob, `thong-ke-btvn-lo-${numBatchId}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("exerciseDetail.exportFailed"));
    } finally {
      setExporting(false);
    }
  };

  useEffect(() => {
    if (!numBatchId) return;
    setLoading(true);
    setError(null);
    getHomeworkBatchStudentStats(numBatchId)
      .then((res) => {
        setStudentStats(res);
        const d = res.assignment.lateSubmissionDeadline;
        setDeadlineDate(d ? d.slice(0, 10) : "");
        setDeadlineTime(d ? d.slice(11, 16) : "");
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("shared.errors.loadResultsFailed")))
      .finally(() => setLoading(false));
  }, [numBatchId]);

  useEffect(() => {
    if (tab !== "questions" || questionsByMember || !studentStats?.assignment.batchMembers) return;
    Promise.all(
      studentStats.assignment.batchMembers.map((m) =>
        getExerciseAssignmentQuestionStats(m.assignmentId).then((res) => ({ title: m.exerciseTitle, questions: res.questions }))
      )
    )
      .then(setQuestionsByMember)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("shared.errors.loadQuestionAnalysisFailed")));
  }, [tab, studentStats, questionsByMember]);

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

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror AssignmentStatsDetailPage.tsx.
  const pendingDeadlineIso = deadlineDate && deadlineTime.length === 5 ? `${deadlineDate}T${deadlineTime}` : null;
  const savedDeadlineIso = studentStats.assignment.lateSubmissionDeadline;
  const hasPendingDeadlineChange = pendingDeadlineIso != null && pendingDeadlineIso !== savedDeadlineIso?.slice(0, 16);

  return (
    <div className="space-y-6">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900">
        <ArrowLeft className="w-4 h-4" />
        {t("shared.back")}
      </button>

      <div>
        <h1 className="text-2xl font-bold font-display text-slate-900">{studentStats.assignment.exerciseTitle}</h1>
        <p className="text-xs text-slate-500 mt-1">{studentStats.assignment.exerciseCode}</p>
        <div className="flex items-center gap-3 mt-2">
          {studentStats.assignment.dueAt && (
            <span className="text-xs text-slate-500">
              {t("exerciseDetail.dueAtLabel")}: {formatDateTime(studentStats.assignment.dueAt, i18n.language)}
            </span>
          )}
          <label className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
            <input
              type="checkbox"
              checked={studentStats.assignment.lateSubmissionAllowed}
              disabled={togglingLateSubmission}
              onChange={(e) => handleToggleLateSubmissionAllowed(e.target.checked)}
              className="rounded border-slate-300"
            />
            {t("exerciseDetail.lateSubmissionAllowedLabel")}
          </label>
        </div>
        {studentStats.assignment.lateSubmissionAllowed && (
          <div className="flex items-center gap-2 mt-2">
            <span className="text-xs font-semibold text-slate-500">{t("exerciseDetail.lateSubmissionDeadlineLabel")}</span>
            <DatePicker
              value={deadlineDate}
              min={studentStats.assignment.dueAt ? studentStats.assignment.dueAt.slice(0, 10) : undefined}
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
                {t("exerciseDetail.confirmDeadline.reviewButton")}
              </Button>
            )}
            {(deadlineDate || deadlineTime) && (
              <button
                type="button"
                onClick={handleClearLateSubmissionDeadline}
                disabled={togglingLateSubmission}
                title={t("exerciseDetail.lateSubmissionDeadlineClear")}
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
            ) : studentStats.students.length === 0 ? (
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
                      <Td className="text-center">{s.totalScore != null ? `${s.totalScore}/${s.totalPoints}` : "—"}</Td>
                      <Td className="text-center">{s.percentage != null ? `${s.percentage}%` : "—"}</Td>
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
            (!questionsByMember ? (
              <p className="text-sm text-slate-500">{t("shared.loading")}</p>
            ) : (
              <div className="space-y-8">
                {questionsByMember.map((member) => (
                  <div key={member.title}>
                    <h3 className="text-sm font-bold text-slate-900 mb-3">{member.title}</h3>
                    {member.questions.length === 0 ? (
                      <p className="text-sm text-slate-400 italic">{t("shared.noDataToShow")}</p>
                    ) : (
                      <div className="space-y-4">
                        <QuestionAnalysisChart questions={member.questions} />
                        <div className="space-y-2">
                          {[...member.questions]
                            .sort((a, b) => b.wrongRatePercent - a.wrongRatePercent)
                            .map((q) => {
                              const key = `${member.title}-${q.questionId}`;
                              return (
                                <QuestionRow
                                  key={key}
                                  question={q}
                                  expanded={expandedQuestionKey === key}
                                  onToggle={() => setExpandedQuestionKey(expandedQuestionKey === key ? null : key)}
                                />
                              );
                            })}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
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

      {detailStudentId && studentStats.assignment.batchMembers && (
        <StudentDetailModal
          studentId={detailStudentId}
          studentFullName={studentStats.students.find((s) => s.studentId === detailStudentId)?.studentFullName ?? ""}
          members={studentStats.assignment.batchMembers.map((m) => ({
            assignmentId: m.assignmentId,
            exerciseId: m.exerciseId,
            exerciseTitle: m.exerciseTitle
          }))}
          onClose={() => setDetailStudentId(null)}
          onRefreshStats={() => {
            if (numBatchId) getHomeworkBatchStudentStats(numBatchId).then(setStudentStats).catch(() => undefined);
          }}
        />
      )}

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — mirror AssignmentStatsDetailPage.tsx. */}
      <Modal
        open={confirmDeadlineOpen}
        onClose={handleCancelDeadlineChange}
        title={t("exerciseDetail.confirmDeadline.title")}
        footer={
          <>
            <button
              onClick={handleCancelDeadlineChange}
              className="bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 font-semibold text-xs px-4 py-2 rounded-lg transition-all"
            >
              {t("exerciseDetail.confirmDeadline.cancel")}
            </button>
            <button
              onClick={handleConfirmDeadlineChange}
              disabled={togglingLateSubmission}
              className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg transition-all disabled:opacity-50"
            >
              {t("exerciseDetail.confirmDeadline.confirmButton")}
            </button>
          </>
        }
      >
        <div className="flex items-start gap-3">
          <ShieldAlert className="w-8 h-8 text-amber-500 shrink-0" />
          <div className="text-xs text-slate-600 leading-relaxed">
            {pendingDeadlineIso &&
              t("exerciseDetail.confirmDeadline.description", { deadline: formatDateTime(pendingDeadlineIso, i18n.language) })}
          </div>
        </div>
      </Modal>
    </div>
  );
}
