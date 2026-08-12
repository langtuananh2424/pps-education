import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
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

const reviewVideoTypeLabels: Record<string, string> = {
  REFLEX: "Video phản xạ",
  CONNECTION: "Video từ kết nối"
};

/**
 * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — "Xem chi tiết" 1 BTVN Video
 * Ôn tập (REFLEX/CONNECTION), mirror AssignmentStatsDetailPage.tsx (Exercise). REFLEX chỉ có bảng
 * tổng hợp (đã nộp X/Y câu, điểm TB) — việc chấm bài vẫn làm ở ExamsPage như hiện tại. CONNECTION có
 * thêm tab "Phân tích câu hỏi" vì đã có sẵn dữ liệu đúng/sai thật.
 */
export default function ReviewVideoAssignmentStatsDetailPage() {
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được kết quả BTVN."))
      .finally(() => setLoading(false));
  }, [numAssignmentId]);

  useEffect(() => {
    if (tab !== "questions" || questionStats || !numAssignmentId || !isConnection) return;
    getReviewVideoAssignmentQuestionStats(numAssignmentId)
      .then(setQuestionStats)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được phân tích câu hỏi."));
  }, [tab, numAssignmentId, questionStats, isConnection]);

  if (!studentStats) {
    return (
      <div className="space-y-4">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900 mb-4">
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

  const { assignment, students } = studentStats;

  return (
    <div className="space-y-6">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900">
        <ArrowLeft className="w-4 h-4" />
        Quay lại
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
                { id: "students", label: "Kết quả học sinh" },
                { id: "questions", label: "Phân tích câu hỏi" }
              ]}
              activeId={tab}
              onChange={(id) => setTab(id as "students" | "questions")}
            />
          </div>
        )}

        <div className="p-5">
          {tab === "students" &&
            (loading ? (
              <p className="text-sm text-slate-500">Đang tải...</p>
            ) : students.length === 0 ? (
              <p className="text-sm text-slate-400 italic">Chưa có dữ liệu để hiển thị.</p>
            ) : (
              <TableContainer className="border-0 rounded-none">
                <thead>
                  <tr>
                    <Th>Học sinh</Th>
                    <Th className="text-center">Đã xem</Th>
                    <Th className="text-center">Hoàn thành</Th>
                    {isConnection ? (
                      <>
                        <Th className="text-center">Điểm trắc nghiệm</Th>
                        <Th className="text-center">Kết quả</Th>
                      </>
                    ) : (
                      <>
                        <Th className="text-center">Đã nộp câu hỏi</Th>
                        <Th className="text-center">Điểm TB</Th>
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
                        {s.viewCount}/{s.requiredViewCount} lượt
                      </Td>
                      <Td className="text-center">
                        <Badge variant={s.completed ? "success" : "neutral"}>{s.completed ? "Hoàn thành" : "Chưa hoàn thành"}</Badge>
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
                              <Badge variant={s.passed ? "success" : "danger"}>{s.passed ? "Đạt" : "Chưa đạt"}</Badge>
                            )}
                          </Td>
                        </>
                      ) : (
                        <>
                          <Td className="text-center">
                            {s.answeredQuestionCount ?? 0}/{s.totalReflexQuestions ?? 0}
                          </Td>
                          <Td className="text-center">
                            {s.averageScore != null && s.averageMaxScore != null ? `${s.averageScore}/${s.averageMaxScore}` : "Chưa chấm"}
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
  return (
    <div className="border border-slate-200 rounded-lg overflow-hidden">
      <button onClick={onToggle} className="w-full flex items-center justify-between gap-3 p-2.5 text-left hover:bg-slate-50">
        <div className="flex items-center gap-2 min-w-0">
          {expanded ? <ChevronDown className="w-3.5 h-3.5 shrink-0 text-slate-400" /> : <ChevronRight className="w-3.5 h-3.5 shrink-0 text-slate-400" />}
          <span className="text-xs text-slate-700 truncate">
            Câu {question.displayOrder}: {question.prompt}
            {question.reviewVideoTitle && <span className="text-slate-400"> — {question.reviewVideoTitle}</span>}
          </span>
        </div>
        <Badge
          variant={question.wrongRatePercent >= 50 ? "danger" : question.wrongRatePercent > 0 ? "warning" : "success"}
          className="shrink-0"
        >
          Sai {question.wrongCount}/{question.answeredCount} ({question.wrongRatePercent}%)
        </Badge>
      </button>
      {expanded && (
        <div className="px-4 pb-3 pl-11">
          {question.wrongStudents.length === 0 ? (
            <p className="text-[11px] text-slate-400 italic">Không có học sinh nào trả lời sai.</p>
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
