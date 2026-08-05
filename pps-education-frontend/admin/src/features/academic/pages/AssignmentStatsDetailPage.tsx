import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft, ChevronDown, ChevronRight, Download, Eye, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import {
  ExerciseAssignmentQuestionRow,
  ExerciseAssignmentQuestionStatsResponse,
  ExerciseAssignmentStudentStatsResponse,
  ExerciseAssignmentStudentRow,
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
        <p className="text-xs text-slate-500 mt-1">{studentStats.assignment.exerciseCode}</p>
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
        <StudentDetailModal student={detailStudent} onClose={() => setDetailStudentId(null)} />
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

function StudentDetailModal({ student, onClose }: { student: ExerciseAssignmentStudentRow; onClose: () => void }) {
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
                <p className="text-xs text-slate-500">Trạng thái</p>
                <p className="font-semibold text-sm mt-1">{studentStatusLabels[student.status]}</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">Điểm số</p>
                <p className="font-semibold text-sm mt-1">{student.totalScore ?? "—"}/{student.totalPoints}</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">Tỉ lệ</p>
                <p className="font-semibold text-sm mt-1">{student.percentage ?? "—"}%</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">Lần làm</p>
                <p className="font-semibold text-sm mt-1">{student.attemptNumber ?? "—"}</p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">Nộp lúc</p>
                <p className="font-semibold text-sm mt-1 text-[12px]">
                  {student.submittedAt ? new Date(student.submittedAt).toLocaleString("vi-VN") : "—"}
                </p>
              </div>
              <div className="border border-slate-200 rounded-lg p-3">
                <p className="text-xs text-slate-500">Kết quả</p>
                <p className="font-semibold text-sm mt-1">
                  {student.passed == null ? "—" : student.passed ? "✓ Đạt" : "✗ Chưa đạt"}
                </p>
              </div>
            </div>
          </div>

          {/* Lịch sử trả lời câu hỏi */}
          <div>
            <h3 className="font-semibold text-sm mb-3 text-slate-900">Lịch sử trả lời câu hỏi</h3>
            <div className="border border-slate-200 rounded-lg overflow-hidden">
              <table className="w-full text-xs">
                <thead className="bg-slate-50">
                  <tr>
                    <th className="text-left p-2 border-b border-slate-200">Câu</th>
                    <th className="text-left p-2 border-b border-slate-200">Câu hỏi</th>
                    <th className="text-center p-2 border-b border-slate-200">Trả lời</th>
                    <th className="text-center p-2 border-b border-slate-200">Kết quả</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  <tr>
                    <td colSpan={4} className="text-center p-3 text-slate-500 text-[11px]">
                      Dữ liệu lịch sử trả lời sẽ được hiển thị tại đây (cần API backend)
                    </td>
                  </tr>
                </tbody>
              </table>
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
