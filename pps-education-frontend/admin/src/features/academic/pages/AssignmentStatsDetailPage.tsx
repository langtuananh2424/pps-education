import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft, ChevronDown, ChevronRight, Download, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import {
  ExerciseAssignmentQuestionRow,
  ExerciseAssignmentQuestionStatsResponse,
  ExerciseAssignmentStudentStatsResponse,
  exportExerciseAssignmentStats,
  getExerciseAssignmentQuestionStats,
  getExerciseAssignmentStudentStats
} from "../api";
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
  const [expandedStudentId, setExpandedStudentId] = useState<number | null>(null);

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
        <p className="text-xs text-slate-500 mt-1">{studentStats.assignment.exerciseCode}</p>
      </div>

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-lg">{error}</div>}

      <Card>
        <Tabs
          items={[
            { id: "students", label: "Kết quả học sinh" },
            { id: "questions", label: "Phân tích câu hỏi" }
          ]}
          activeId={tab}
          onChange={(id) => setTab(id as "students" | "questions")}
        />

        <div className="mt-6">
          {tab === "students" &&
            (loading ? (
              <p className="text-sm text-slate-500">Đang tải...</p>
            ) : !studentStats || studentStats.students.length === 0 ? (
              <p className="text-sm text-slate-400 italic">Chưa có dữ liệu để hiển thị.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-xs border-collapse">
                  <thead>
                    <tr className="bg-slate-50">
                      <th className="text-left p-2 border border-slate-200 sticky left-0 bg-slate-50">Học sinh</th>
                      <th className="text-center p-2 border border-slate-200">Trạng thái</th>
                      <th className="text-center p-2 border border-slate-200">Điểm</th>
                      <th className="text-center p-2 border border-slate-200">%</th>
                      <th className="text-center p-2 border border-slate-200">Đã hoàn thành</th>
                      <th className="text-center p-2 border border-slate-200">Số lần làm</th>
                      <th className="text-center p-2 border border-slate-200"></th>
                    </tr>
                  </thead>
                  <tbody>
                    {studentStats.students.map((s) => (
                      <>
                      <tr key={s.studentId}>
                        <td className="p-2 border border-slate-200 font-semibold sticky left-0 bg-white whitespace-nowrap">
                          {s.studentFullName} <span className="text-slate-400 font-mono text-[10px]">({s.studentCode})</span>
                        </td>
                        <td className="text-center p-2 border border-slate-200">
                          <Badge variant={studentStatusVariants[s.status]}>{studentStatusLabels[s.status]}</Badge>
                        </td>
                        <td className="text-center p-2 border border-slate-200">
                          {s.totalScore != null ? `${s.totalScore}/${s.totalPoints}` : "—"}
                        </td>
                        <td className="text-center p-2 border border-slate-200">
                          {s.percentage != null ? `${s.percentage}%` : "—"}
                        </td>
                        <td className="text-center p-2 border border-slate-200">
                          {s.status === "CHUA_LAM" ? (
                            <Badge variant="neutral">Chưa làm</Badge>
                          ) : s.passed == null ? (
                            <Badge variant="neutral">—</Badge>
                          ) : (
                            <Badge variant={s.passed ? "success" : "danger"}>{s.passed ? "Đạt" : "Chưa đạt"}</Badge>
                          )}
                        </td>
                        <td className="text-center p-2 border border-slate-200">
                          {s.attemptNumber ?? "—"}
                        </td>
                        <td className="text-center p-2 border border-slate-200">
                          {s.status !== "CHUA_LAM" && (
                            <button
                              onClick={() => setExpandedStudentId(expandedStudentId === s.studentId ? null : s.studentId)}
                              className="text-xs text-blue-600 hover:underline"
                            >
                              {expandedStudentId === s.studentId ? "Ẩn" : "Xem"}
                            </button>
                          )}
                        </td>
                      </tr>
                      {expandedStudentId === s.studentId && (
                        <tr className="bg-slate-50">
                          <td colSpan={7} className="p-3 border border-slate-200">
                            <div className="space-y-2">
                              <h4 className="font-semibold text-sm">Chi tiết lịch sử trả lời</h4>
                              <div className="grid grid-cols-3 gap-4 text-xs">
                                <div>
                                  <p className="text-slate-500">Trạng thái:</p>
                                  <p className="font-semibold">{studentStatusLabels[s.status]}</p>
                                </div>
                                <div>
                                  <p className="text-slate-500">Điểm số:</p>
                                  <p className="font-semibold">{s.totalScore ?? "—"}/{s.totalPoints}</p>
                                </div>
                                <div>
                                  <p className="text-slate-500">Tỉ lệ:</p>
                                  <p className="font-semibold">{s.percentage ?? "—"}%</p>
                                </div>
                                <div>
                                  <p className="text-slate-500">Lần làm:</p>
                                  <p className="font-semibold">{s.attemptNumber ?? "—"}</p>
                                </div>
                                <div>
                                  <p className="text-slate-500">Nộp lúc:</p>
                                  <p className="font-semibold text-[11px]">{s.submittedAt ? new Date(s.submittedAt).toLocaleString("vi-VN") : "—"}</p>
                                </div>
                                <div>
                                  <p className="text-slate-500">Kết quả:</p>
                                  <p className="font-semibold">
                                    {s.passed == null ? "—" : s.passed ? "✓ Đạt" : "✗ Chưa đạt"}
                                  </p>
                                </div>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                      </>
                    ))}
                  </tbody>
                </table>
              </div>
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
        <div className="flex justify-end gap-2">
          <Button variant="primary" size="sm" onClick={handleExport} disabled={exporting}>
            <Download className="w-3.5 h-3.5" /> {exporting ? "Đang xuất..." : "Xuất Excel"}
          </Button>
        </div>
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
          <span className="text-xs text-slate-700 truncate">
            Câu {question.displayOrder}: {question.content}
          </span>
        </div>
        <Badge variant={question.wrongRatePercent >= 50 ? "danger" : question.wrongRatePercent > 0 ? "warning" : "success"} className="shrink-0">
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
