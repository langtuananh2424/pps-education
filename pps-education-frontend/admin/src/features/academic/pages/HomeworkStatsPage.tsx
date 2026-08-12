import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { BarChart3, Search, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import { ExerciseAssignmentStatsResponse, listExerciseAssignmentStats } from "../api";
import { ReviewVideoAssignmentStatsResponse, ReviewVideoType, listReviewVideoAssignmentStatsForClass } from "@/features/lms/api";
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

const reviewVideoTypeLabels: Record<ReviewVideoType, string> = {
  REFLEX: "Video phản xạ",
  CONNECTION: "Video từ kết nối"
};

/** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — lọc theo GV Việt Nam/nước ngoài, dùng chung cho cả 2 nguồn (Exercise lấy qua exam.teacherType, review-video lấy trực tiếp từ set.teacherType). */
type TeacherTypeFilter = "VIETNAMESE" | "FOREIGN";
const teacherTypeLabels: Record<TeacherTypeFilter, string> = {
  VIETNAMESE: "Giáo viên Việt Nam",
  FOREIGN: "Giáo viên nước ngoài"
};

/**
 * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — gộp BTVN Video Ôn tập
 * (REFLEX/CONNECTION) vào cùng bảng với Exercise. 2 nguồn có ID độc lập (namespace riêng) nên phân biệt
 * bằng `kind`, KHÔNG trộn field trực tiếp — review-video vẫn KHÔNG có cột "%HS vi phạm" (video không có
 * khái niệm giám sát chống gian lận như Exercise). Nút "Xem chi tiết" của review-video (đã xác nhận với
 * người dùng 2026-08-12) điều hướng sang route riêng `/academic/homework-stats/review-video/:assignmentId`
 * (không dùng chung route `:assignmentId` của Exercise — 2 bảng ID độc lập, trùng số nhưng khác nguồn).
 */
type HomeworkRow =
  | { kind: "exercise"; data: ExerciseAssignmentStatsResponse }
  | { kind: "review-video"; data: ReviewVideoAssignmentStatsResponse };

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

  const [rows, setRows] = useState<HomeworkRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — search theo tiêu đề/mã BTVN + lọc
  // theo ngày giao (availableFrom), lọc client-side vì danh sách theo 1 lớp thường không lớn.
  const [searchQuery, setSearchQuery] = useState("");
  const [dateFilter, setDateFilter] = useState("");
  const [teacherTypeFilter, setTeacherTypeFilter] = useState<TeacherTypeFilter | null>(null);

  useEffect(() => {
    if (!selectedClassId) {
      setRows([]);
      return;
    }
    setLoading(true);
    setError(null);
    setSearchQuery("");
    setDateFilter("");
    setTeacherTypeFilter(null);
    Promise.all([listExerciseAssignmentStats(selectedClassId), listReviewVideoAssignmentStatsForClass(selectedClassId)])
      .then(([exerciseRows, reviewVideoRows]) => {
        const merged: HomeworkRow[] = [
          ...exerciseRows.map((data): HomeworkRow => ({ kind: "exercise", data })),
          ...reviewVideoRows.map((data): HomeworkRow => ({ kind: "review-video", data }))
        ];
        merged.sort((a, b) => new Date(b.data.availableFrom).getTime() - new Date(a.data.availableFrom).getTime());
        setRows(merged);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được thống kê BTVN."))
      .finally(() => setLoading(false));
  }, [selectedClassId]);

  const filteredRows = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return rows.filter((r) => {
      const title = r.kind === "exercise" ? r.data.exerciseTitle : r.data.reviewVideoSetTitle;
      const code = r.kind === "exercise" ? r.data.exerciseCode : r.data.reviewVideoSetCode;
      const matchesQuery = !q || title.toLowerCase().includes(q) || code.toLowerCase().includes(q);
      const matchesDate = !dateFilter || toLocalIsoDate(r.data.availableFrom) === dateFilter;
      const matchesTeacherType = !teacherTypeFilter || r.data.teacherType === teacherTypeFilter;
      return matchesQuery && matchesDate && matchesTeacherType;
    });
  }, [rows, searchQuery, dateFilter, teacherTypeFilter]);

  const hasActiveFilters = searchQuery.trim() !== "" || dateFilter !== "" || teacherTypeFilter !== null;

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — phân trang phía client (mirror
  // ExerciseAssignPage.tsx — backend GET .../stats chưa hỗ trợ phân trang server-side). Về trang 1 mỗi
  // khi đổi bộ lọc để không kẹt ở 1 trang rỗng sau khi lọc ra ít kết quả hơn.
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  useEffect(() => setPage(0), [searchQuery, dateFilter, teacherTypeFilter, rows]);
  const pageRows = filteredRows.slice(page * pageSize, (page + 1) * pageSize);

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
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-3">
            <div className="flex items-center justify-between gap-3 flex-wrap">
              <span className="text-xs font-bold text-slate-700 font-display block">
                {selectedClass ? `${selectedClass.classCode} — ${selectedClass.name}` : "Lớp đang chọn"} (
                {hasActiveFilters ? `${filteredRows.length}/${rows.length}` : rows.length} BTVN)
              </span>
              {rows.length > 0 && (
                <div className="inline-flex rounded-lg border border-slate-200 bg-white p-0.5 gap-0.5 shrink-0">
                  {(
                    [
                      { value: null, label: "Tất cả" },
                      { value: "VIETNAMESE" as TeacherTypeFilter, label: "GVVN" },
                      { value: "FOREIGN" as TeacherTypeFilter, label: "GVNN" }
                    ] satisfies { value: TeacherTypeFilter | null; label: string }[]
                  ).map((opt) => (
                    <button
                      key={opt.label}
                      type="button"
                      onClick={() => setTeacherTypeFilter(opt.value)}
                      title={opt.value ? teacherTypeLabels[opt.value] : "Tất cả loại giáo viên"}
                      className={`px-3 py-1.5 rounded-md text-[11px] font-bold transition-colors ${
                        teacherTypeFilter === opt.value ? "bg-brand-gradient text-white" : "text-slate-500 hover:bg-slate-50"
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
            {rows.length > 0 && (
              <div className="flex flex-col sm:flex-row gap-2 sm:items-center">
                <div className="relative flex-1 sm:max-w-xs">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
                  <input
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Tìm theo tiêu đề / mã BTVN..."
                    className="w-full bg-white border border-slate-200 text-xs pl-8 pr-3 py-2 rounded-lg focus:outline-none"
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
                      setTeacherTypeFilter(null);
                    }}
                    className="flex items-center gap-1 text-[11px] font-bold text-slate-500 hover:text-slate-700 shrink-0"
                  >
                    <X className="w-3.5 h-3.5" /> Xoá lọc
                  </button>
                )}
              </div>
            )}
          </div>
          {loading ? (
            <p className="text-xs text-slate-500 p-5">Đang tải...</p>
          ) : rows.length === 0 ? (
            <EmptyState icon={BarChart3} title="Chưa có BTVN nào" description="Lớp này chưa được giao BTVN nào." />
          ) : filteredRows.length === 0 ? (
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
                  <Th className="text-center">% Làm bài</Th>
                  <Th className="text-center">% đạt</Th>
                  <Th className="text-center">%HS vi phạm</Th>
                  <Th />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {pageRows.map((r) =>
                  r.kind === "exercise" ? (
                    <tr key={`exercise-${r.data.assignmentId}`}>
                      <Td className="font-semibold text-slate-900">
                        {r.data.exerciseTitle} <span className="text-slate-400 font-mono text-[10px]">({r.data.exerciseCode})</span>
                      </Td>
                      <Td>
                        <Badge variant="neutral">{exerciseTypeLabels[r.data.exerciseType]}</Badge>
                      </Td>
                      <Td>{formatDate(r.data.availableFrom)}</Td>
                      <Td>{formatDate(r.data.dueAt)}</Td>
                      <Td className="text-center">
                        {r.data.completedCount}/{r.data.totalStudents} ({r.data.completionPercent}%)
                      </Td>
                      <Td className="text-center">
                        {r.data.passedCount}/{r.data.totalStudents} ({r.data.passRatePercent}%)
                      </Td>
                      <Td className="text-center">
                        {r.data.violatedStudentCount != null ? (
                          <span className={r.data.violatedStudentCount > 0 ? "font-bold text-amber-700" : "text-slate-400"}>
                            {Math.round((r.data.violatedStudentCount / r.data.totalStudents) * 100)}%
                          </span>
                        ) : (
                          <span className="text-slate-300">—</span>
                        )}
                      </Td>
                      <Td className="text-right">
                        <Button size="sm" onClick={() => navigate(`/academic/homework-stats/${r.data.assignmentId}`)}>
                          Xem chi tiết
                        </Button>
                      </Td>
                    </tr>
                  ) : (
                    <tr key={`review-video-${r.data.assignmentId}`}>
                      <Td className="font-semibold text-slate-900">
                        {r.data.reviewVideoSetTitle} <span className="text-slate-400 font-mono text-[10px]">({r.data.reviewVideoSetCode})</span>
                      </Td>
                      <Td>
                        <Badge variant="info">{reviewVideoTypeLabels[r.data.videoType]}</Badge>
                      </Td>
                      <Td>{formatDate(r.data.availableFrom)}</Td>
                      <Td>{formatDate(r.data.dueAt)}</Td>
                      <Td className="text-center">
                        {r.data.completedCount}/{r.data.totalStudents} ({r.data.completionPercent}%)
                      </Td>
                      <Td className="text-center">
                        {r.data.passedCount != null && r.data.passRatePercent != null ? (
                          `${r.data.passedCount}/${r.data.totalStudents} (${r.data.passRatePercent}%)`
                        ) : (
                          <span className="text-slate-300">—</span>
                        )}
                      </Td>
                      <Td className="text-center">
                        <span className="text-slate-300">—</span>
                      </Td>
                      <Td className="text-right">
                        <Button size="sm" onClick={() => navigate(`/academic/homework-stats/review-video/${r.data.assignmentId}`)}>
                          Xem chi tiết
                        </Button>
                      </Td>
                    </tr>
                  )
                )}
              </tbody>
            </TableContainer>
            <Pagination
              page={page}
              pageSize={pageSize}
              totalElements={filteredRows.length}
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
