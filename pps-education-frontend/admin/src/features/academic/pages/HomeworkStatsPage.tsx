import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { BarChart3 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import { ExerciseAssignmentStatsResponse, listExerciseAssignmentStats } from "../api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
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

/** UC-66: Thống kê BTVN theo lớp (FR-ACA-07) — Giáo viên/Quản lý điểm trường xem tiến độ BTVN của 1 lớp. */
export default function HomeworkStatsPage() {
  const navigate = useNavigate();
  const { selectedClassId } = useApp();
  const { classes } = useEligibleClasses();
  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;

  const [assignments, setAssignments] = useState<ExerciseAssignmentStatsResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
                  <Th className="text-center">% đạt</Th>
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
                    <Td className="text-center">
                      {a.passedCount}/{a.totalStudents} ({a.passRatePercent}%)
                    </Td>
                    <Td className="text-right">
                      <Button size="sm" onClick={() => navigate(`/academic/homework-stats/${a.assignmentId}`)}>
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
    </div>
  );
}
