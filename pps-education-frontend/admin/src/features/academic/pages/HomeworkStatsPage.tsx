import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { BarChart3, Search, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import { ExerciseAssignmentStatsResponse, listExerciseAssignmentStats } from "../api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import EmptyState from "@/components/ui/EmptyState";
import DatePicker from "@/components/ui/DatePicker";
import Pagination from "@/components/ui/Pagination";

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

/** "YYYY-MM-DD" theo giờ local — dùng để so khớp với DatePicker (cùng định dạng value của nó). */
function toLocalIsoDate(value: string): string {
  const d = new Date(value);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
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
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — search theo tiêu đề/mã BTVN + lọc
  // theo ngày giao (availableFrom), lọc client-side vì danh sách theo 1 lớp thường không lớn.
  const [searchQuery, setSearchQuery] = useState("");
  const [dateFilter, setDateFilter] = useState("");

  useEffect(() => {
    if (!selectedClassId) {
      setAssignments([]);
      return;
    }
    setLoading(true);
    setError(null);
    setSearchQuery("");
    setDateFilter("");
    listExerciseAssignmentStats(selectedClassId)
      .then(setAssignments)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được thống kê BTVN."))
      .finally(() => setLoading(false));
  }, [selectedClassId]);

  const filteredAssignments = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return assignments.filter((a) => {
      const matchesQuery = !q || a.exerciseTitle.toLowerCase().includes(q) || a.exerciseCode.toLowerCase().includes(q);
      const matchesDate = !dateFilter || toLocalIsoDate(a.availableFrom) === dateFilter;
      return matchesQuery && matchesDate;
    });
  }, [assignments, searchQuery, dateFilter]);

  const hasActiveFilters = searchQuery.trim() !== "" || dateFilter !== "";

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — phân trang phía client (mirror
  // ExerciseAssignPage.tsx — backend GET .../stats chưa hỗ trợ phân trang server-side). Về trang 1 mỗi
  // khi đổi bộ lọc để không kẹt ở 1 trang rỗng sau khi lọc ra ít kết quả hơn.
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  useEffect(() => setPage(0), [searchQuery, dateFilter, assignments]);
  const pageAssignments = filteredAssignments.slice(page * pageSize, (page + 1) * pageSize);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Thống kê BTVN theo lớp</h1>
        <p className="text-sm text-slate-500 mt-1">
          Xem tiến độ hoàn thành và tỷ lệ đạt của từng BTVN đã giao cho lớp, kết quả từng học sinh, và phân tích câu hỏi hay bị sai.
        </p>
      </div>

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {!selectedClassId ? (
        <Card>
          <EmptyState icon={BarChart3} title="Chưa chọn lớp" description="Chọn 1 lớp ở góc trên bên phải để xem thống kê BTVN." />
        </Card>
      ) : (
        <Card padded={false} className="overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-3">
            <span className="text-sm font-bold text-slate-700 font-display block">
              {selectedClass ? `${selectedClass.classCode} — ${selectedClass.name}` : "Lớp đang chọn"} (
              {hasActiveFilters ? `${filteredAssignments.length}/${assignments.length}` : assignments.length} BTVN)
            </span>
            {assignments.length > 0 && (
              <div className="flex flex-col sm:flex-row gap-2 sm:items-center">
                <div className="relative flex-1 sm:max-w-xs">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
                  <input
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Tìm theo tiêu đề / mã BTVN..."
                    className="w-full bg-white border border-slate-200 text-sm pl-8 pr-3 py-2 rounded-lg focus:outline-none"
                  />
                </div>
                <div className="sm:w-52">
                  <DatePicker value={dateFilter} onChange={setDateFilter} placeholder="Lọc theo ngày giao..." />
                </div>
                {hasActiveFilters && (
                  <button
                    type="button"
                    onClick={() => {
                      setSearchQuery("");
                      setDateFilter("");
                    }}
                    className="flex items-center gap-1 text-sm font-bold text-slate-500 hover:text-slate-700 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" /> Xoá lọc
                  </button>
                )}
              </div>
            )}
          </div>
          {loading ? (
            <p className="text-sm text-slate-500 p-5">Đang tải...</p>
          ) : assignments.length === 0 ? (
            <EmptyState icon={BarChart3} title="Chưa có BTVN nào" description="Lớp này chưa được giao BTVN nào." />
          ) : filteredAssignments.length === 0 ? (
            <EmptyState icon={Search} title="Không tìm thấy BTVN phù hợp" description="Thử đổi từ khoá tìm kiếm hoặc bỏ lọc theo ngày giao." />
          ) : (
            <>
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
                {pageAssignments.map((a) => (
                  <tr key={a.assignmentId}>
                    <Td className="font-semibold text-slate-900">
                      {a.exerciseTitle} <span className="text-slate-400 font-mono text-sm">({a.exerciseCode})</span>
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
            <Pagination
              page={page}
              pageSize={pageSize}
              totalElements={filteredAssignments.length}
              itemLabel="BTVN"
              onPageChange={setPage}
              onPageSizeChange={(size) => {
                setPageSize(size);
                setPage(0);
              }}
            />
            </>
          )}
        </Card>
      )}
    </div>
  );
}
