import React, { useEffect, useState } from "react";
import { BarChart3, ChevronDown, ChevronRight, Download } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { useApp } from "@/context/AppContext";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import {
  ExerciseAssignmentQuestionRow,
  ExerciseAssignmentQuestionStatsResponse,
  ExerciseAssignmentStatsResponse,
  ExerciseAssignmentStudentStatsResponse,
  exportExerciseAssignmentStats,
  getExerciseAssignmentQuestionStats,
  getExerciseAssignmentStudentStats,
  listExerciseAssignmentStats
} from "../api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Modal from "@/components/ui/Modal";
import Tabs from "@/components/ui/Tabs";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import EmptyState from "@/components/ui/EmptyState";

const exerciseTypeLabels: Record<ExerciseAssignmentStatsResponse["exerciseType"], string> = {
  SELF_PRACTICE: "Tự luyện",
  ASSIGNED: "Có hạn nộp",
  MOCK_TEST: "Thi thử",
  SKILL_PRACTICE: "Luyện kỹ năng"
};

const studentStatusLabels: Record<string, string> = {
  CHUA_LAM: "Chưa làm",
  DANG_LAM: "Đang làm",
  DA_NOP: "Đã nộp",
  TRE_HAN: "Trễ hạn"
};

const studentStatusVariants: Record<string, BadgeVariant> = {
  CHUA_LAM: "neutral",
  DANG_LAM: "info",
  DA_NOP: "success",
  TRE_HAN: "warning"
};

function formatDate(value: string | null): string {
  if (!value) return "Không có hạn";
  return new Date(value).toLocaleDateString("vi-VN");
}

/** UC-66: Thống kê BTVN theo lớp (FR-ACA-07) — Giáo viên/Quản lý điểm trường xem tiến độ và tỷ lệ đạt BTVN của 1 lớp. */
export default function HomeworkStatsPage() {
  const { selectedClassId } = useApp();
  const { classes } = useEligibleClasses();
  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;

  const [assignments, setAssignments] = useState<ExerciseAssignmentStatsResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detailAssignmentId, setDetailAssignmentId] = useState<number | null>(null);

  useEffect(() => {
    if (!selectedClassId) {
      setAssignments([]);
      return;
    }
    setLoading(true);
    setError(null);
    listExerciseAssignmentStats(selectedClassId)
      .then(setAssignments)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được thống kê BTVN."))
      .finally(() => setLoading(false));
  }, [selectedClassId]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Thống kê BTVN theo lớp</h1>
        <p className="text-xs text-slate-500 mt-1">
          Xem tiến độ hoàn thành và tỷ lệ đạt của từng BTVN đã giao cho lớp, kết quả từng học sinh, và phân tích câu hỏi hay bị sai.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {!selectedClassId ? (
        <Card>
          <EmptyState icon={BarChart3} title="Chưa chọn lớp" description="Chọn 1 lớp ở góc trên bên phải để xem thống kê BTVN." />
        </Card>
      ) : (
        <Card padded={false} className="overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display">
              {selectedClass ? `${selectedClass.classCode} — ${selectedClass.name}` : "Lớp đang chọn"} ({assignments.length} BTVN)
            </span>
          </div>
          {loading ? (
            <p className="text-xs text-slate-500 p-5">Đang tải...</p>
          ) : assignments.length === 0 ? (
            <EmptyState icon={BarChart3} title="Chưa có BTVN nào" description="Lớp này chưa được giao BTVN nào." />
          ) : (
            <TableContainer className="border-0 rounded-none">
              <thead>
                <tr>
                  <Th>Tiêu đề</Th>
                  <Th>Loại</Th>
                  <Th>Ngày giao</Th>
                  <Th>Hạn nộp</Th>
                  <Th className="text-center">% hoàn thành</Th>
                  <Th />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {assignments.map((a) => (
                  <tr key={a.assignmentId}>
                    <Td className="font-semibold text-slate-900">
                      {a.exerciseTitle} <span className="text-slate-400 font-mono text-[10px]">({a.exerciseCode})</span>
                    </Td>
                    <Td>
                      <Badge variant="neutral">{exerciseTypeLabels[a.exerciseType]}</Badge>
                    </Td>
                    <Td>{formatDate(a.availableFrom)}</Td>
                    <Td>{formatDate(a.dueAt)}</Td>
                    <Td className="text-center">
                      {a.completedCount}/{a.totalStudents} ({a.completionPercent}%)
                    </Td>
                    <Td className="text-right">
                      <Button size="sm" onClick={() => setDetailAssignmentId(a.assignmentId)}>
                        Xem chi tiết
                      </Button>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </TableContainer>
          )}
        </Card>
      )}

      {detailAssignmentId && (
        <AssignmentDetailModal assignmentId={detailAssignmentId} onClose={() => setDetailAssignmentId(null)} />
      )}
    </div>
  );
}

function AssignmentDetailModal({ assignmentId, onClose }: { assignmentId: number; onClose: () => void }) {
  const [tab, setTab] = useState<"students" | "questions">("students");
  const [studentStats, setStudentStats] = useState<ExerciseAssignmentStudentStatsResponse | null>(null);
  const [questionStats, setQuestionStats] = useState<ExerciseAssignmentQuestionStatsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [expandedQuestionId, setExpandedQuestionId] = useState<number | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    getExerciseAssignmentStudentStats(assignmentId)
      .then(setStudentStats)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được kết quả BTVN."))
      .finally(() => setLoading(false));
  }, [assignmentId]);

  useEffect(() => {
    if (tab !== "questions" || questionStats) return;
    getExerciseAssignmentQuestionStats(assignmentId)
      .then(setQuestionStats)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được phân tích câu hỏi."));
  }, [tab, assignmentId, questionStats]);

  const handleExport = async () => {
    setExporting(true);
    try {
      const blob = await exportExerciseAssignmentStats(assignmentId);
      downloadBlob(blob, `thong-ke-btvn-${assignmentId}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không xuất được file Excel.");
    } finally {
      setExporting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={studentStats ? studentStats.assignment.exerciseTitle : "Chi tiết BTVN"}
      size="lg"
      footer={
        <Button variant="primary" size="sm" onClick={handleExport} disabled={exporting || !studentStats}>
          <Download className="w-3.5 h-3.5" /> {exporting ? "Đang xuất..." : "Xuất Excel"}
        </Button>
      }
    >
      <div className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <Tabs
          items={[
            { id: "students", label: "Kết quả học sinh" },
            { id: "questions", label: "Phân tích câu hỏi" }
          ]}
          activeId={tab}
          onChange={(id) => setTab(id as "students" | "questions")}
        />

        {tab === "students" &&
          (loading ? (
            <p className="text-xs text-slate-500">Đang tải...</p>
          ) : !studentStats || studentStats.students.length === 0 ? (
            <p className="text-xs text-slate-400 italic">Chưa có dữ liệu để hiển thị.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs border-collapse">
                <thead>
                  <tr className="bg-slate-50">
                    <th className="text-left p-2 border border-slate-200 sticky left-0 bg-slate-50">Học sinh</th>
                    <th className="text-center p-2 border border-slate-200">Trạng thái</th>
                    <th className="text-center p-2 border border-slate-200">Điểm</th>
                    <th className="text-center p-2 border border-slate-200">Đã hoàn thành</th>
                    <th className="text-center p-2 border border-slate-200">Số lần làm</th>
                  </tr>
                </thead>
                <tbody>
                  {studentStats.students.map((s) => (
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
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}

        {tab === "questions" &&
          (!questionStats ? (
            <p className="text-xs text-slate-500">Đang tải...</p>
          ) : questionStats.questions.length === 0 ? (
            <p className="text-xs text-slate-400 italic">Chưa có dữ liệu để hiển thị.</p>
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
    </Modal>
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
