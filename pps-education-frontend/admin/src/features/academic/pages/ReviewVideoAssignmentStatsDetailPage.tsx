import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ArrowLeft, ChevronDown, ChevronRight } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  ReviewVideoAssignmentQuestionRow,
  ReviewVideoAssignmentQuestionStatsResponse,
  ReviewVideoAssignmentStudentStatsResponse,
  getReviewVideoAssignmentQuestionStats,
  getReviewVideoAssignmentStudentStats
} from "@/features/lms/api";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Tabs from "@/components/ui/Tabs";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";

/**
 * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — "Xem chi tiết" 1 BTVN Video
 * Ôn tập (REFLEX/CONNECTION), mirror AssignmentStatsDetailPage.tsx (Exercise). REFLEX chỉ có bảng
 * tổng hợp (đã nộp X/Y câu, điểm TB) — việc chấm bài vẫn làm ở ExamsPage như hiện tại. CONNECTION có
 * thêm tab "Phân tích câu hỏi" vì đã có sẵn dữ liệu đúng/sai thật.
 */
export default function ReviewVideoAssignmentStatsDetailPage() {
  const { t } = useTranslation("academic-homework");
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

  const numAssignmentId = assignmentId ? parseInt(assignmentId, 10) : null;
  const isConnection = studentStats?.assignment.videoType === "CONNECTION";

  useEffect(() => {
    if (!numAssignmentId) return;
    setLoading(true);
    setError(null);
    getReviewVideoAssignmentStudentStats(numAssignmentId)
      .then(setStudentStats)
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
