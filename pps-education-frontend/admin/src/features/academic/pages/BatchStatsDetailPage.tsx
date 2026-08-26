import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ArrowLeft, Download } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import {
  ExerciseAssignmentQuestionRow,
  ExerciseAssignmentStudentStatsResponse,
  exportHomeworkBatchStats,
  getExerciseAssignmentQuestionStats,
  getHomeworkBatchStudentStats
} from "../api";
import { QuestionAnalysisChart, QuestionRow } from "./AssignmentStatsDetailPage";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Tabs from "@/components/ui/Tabs";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";

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
 * getBatchStudentStats) — không có cột "Xem chi tiết" từng học sinh vì 1 học sinh có N lượt làm (1
 * lượt/Bài), không phải 1 lượt duy nhất để mở modal như bản giao lẻ. Tab "Phân tích câu hỏi" ghép lại
 * từ chính API phân tích câu hỏi của TỪNG Bài con thật (không có endpoint cộng dồn riêng — mỗi Bài vẫn
 * là 1 Exercise độc lập, không clone câu hỏi), chia theo tiêu đề từng Bài.
 */
export default function BatchStatsDetailPage() {
  const { t } = useTranslation("academic-homework");
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

  const numBatchId = batchId ? parseInt(batchId, 10) : null;

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
      .then(setStudentStats)
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

  return (
    <div className="space-y-6">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900">
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
    </div>
  );
}
