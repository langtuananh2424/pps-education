import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { BarChart3, ChevronDown, ChevronRight, Search, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { formatDateLong } from "@/lib/i18nFormat";
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

/** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — lọc theo GV Việt Nam/nước ngoài, dùng chung cho cả 2 nguồn (Exercise lấy qua exam.teacherType, review-video lấy trực tiếp từ set.teacherType). */
type TeacherTypeFilter = "VIETNAMESE" | "FOREIGN";

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

function formatDate(value: string | null, language: string, noDateLabel: string): string {
  if (!value) return noDateLabel;
  return formatDateLong(new Date(value), language);
}

/** "YYYY-MM-DD" theo giờ local — dùng để so khớp với DatePicker (cùng định dạng value của nó). */
function toLocalIsoDate(value: string): string {
  const d = new Date(value);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — 1 dòng tổng hợp cho 1
 * "Lô giao BTVN theo kỹ năng" (nhiều Bài cùng Lesson+kỹ năng giao chung 1 lần ở UC-21), có thể mở
 * rộng ra xem từng Bài con bên dưới. Số liệu dòng tổng hợp đã cộng dồn đúng theo học sinh hoàn
 * thành/đạt TẤT CẢ Bài trong lô (xem ExerciseReportService#toBatchGroupStats) — không phải tổng
 * cộng đơn giản của các dòng con. Nút "Xem chi tiết" chỉ có ở từng dòng con (trỏ đúng
 * assignmentId thật của Bài đó) — dòng tổng hợp dùng để mở/thu gọn, không có trang chi tiết riêng.
 */
function BatchGroupRows({
  data,
  expanded,
  onToggle,
  exerciseTypeLabels,
  i18n,
  t,
  navigate
}: {
  data: ExerciseAssignmentStatsResponse;
  expanded: boolean;
  onToggle: () => void;
  exerciseTypeLabels: Record<ExerciseAssignmentStatsResponse["exerciseType"], string>;
  i18n: { language: string };
  t: (key: string) => string;
  navigate: (path: string) => void;
}) {
  const members = data.batchMembers ?? [];
  return (
    <>
      <tr className="bg-slate-50/70 cursor-pointer hover:bg-slate-100" onClick={onToggle}>
        <Td className="font-semibold text-slate-900">
          <span className="inline-flex items-center gap-1.5">
            {expanded ? <ChevronDown className="w-3.5 h-3.5 text-slate-400" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400" />}
            {data.exerciseTitle}
          </span>
        </Td>
        <Td>
          <Badge variant="neutral">{exerciseTypeLabels[data.exerciseType]}</Badge>
        </Td>
        <Td>{formatDate(data.availableFrom, i18n.language, t("list.noDueDate"))}</Td>
        <Td>{formatDate(data.dueAt, i18n.language, t("list.noDueDate"))}</Td>
        <Td className="text-center">
          {data.completedCount}/{data.totalStudents} ({data.completionPercent}%)
        </Td>
        <Td className="text-center">
          {data.passedCount}/{data.totalStudents} ({data.passRatePercent}%)
        </Td>
        <Td className="text-center">
          <span className="text-slate-300">—</span>
        </Td>
        <Td className="text-right">
          <Button
            size="sm"
            onClick={(e) => {
              e.stopPropagation();
              navigate(`/academic/homework-stats/batch/${data.homeworkBatchId}`);
            }}
          >
            {t("shared.viewDetail")}
          </Button>
        </Td>
      </tr>
      {expanded &&
        members.map((m) => (
          <tr key={`exercise-${m.assignmentId}`} className="bg-white">
            <Td className="pl-8 text-slate-700">
              {m.exerciseTitle} <span className="text-slate-400 font-mono text-[10px]">({m.exerciseCode})</span>
            </Td>
            <Td>
              <Badge variant="neutral">{exerciseTypeLabels[m.exerciseType]}</Badge>
            </Td>
            <Td>{formatDate(m.availableFrom, i18n.language, t("list.noDueDate"))}</Td>
            <Td>{formatDate(m.dueAt, i18n.language, t("list.noDueDate"))}</Td>
            <Td className="text-center">
              {m.completedCount}/{m.totalStudents} ({m.completionPercent}%)
            </Td>
            <Td className="text-center">
              {m.passedCount}/{m.totalStudents} ({m.passRatePercent}%)
            </Td>
            <Td className="text-center">
              {m.violatedStudentCount != null ? (
                <span className={m.violatedStudentCount > 0 ? "font-bold text-amber-700" : "text-slate-400"}>
                  {Math.round((m.violatedStudentCount / m.totalStudents) * 100)}%
                </span>
              ) : (
                <span className="text-slate-300">—</span>
              )}
            </Td>
            <Td className="text-right">
              <Button size="sm" onClick={() => navigate(`/academic/homework-stats/${m.assignmentId}`)}>
                {t("shared.viewDetail")}
              </Button>
            </Td>
          </tr>
        ))}
    </>
  );
}

/** UC-66: Thống kê BTVN theo lớp (FR-ACA-07) — Giáo viên/Quản lý điểm trường xem tiến độ BTVN của 1 lớp. */
export default function HomeworkStatsPage() {
  const { t, i18n } = useTranslation("academic-homework");
  const exerciseTypeLabels: Record<ExerciseAssignmentStatsResponse["exerciseType"], string> = {
    SELF_PRACTICE: t("list.exerciseType.SELF_PRACTICE"),
    ASSIGNED: t("list.exerciseType.ASSIGNED"),
    MOCK_TEST: t("list.exerciseType.MOCK_TEST"),
    SKILL_PRACTICE: t("list.exerciseType.SKILL_PRACTICE")
  };
  const reviewVideoTypeLabels: Record<ReviewVideoType, string> = {
    REFLEX: t("shared.reviewVideoType.REFLEX"),
    CONNECTION: t("shared.reviewVideoType.CONNECTION")
  };
  const teacherTypeLabels: Record<TeacherTypeFilter, string> = {
    VIETNAMESE: t("list.teacherType.VIETNAMESE"),
    FOREIGN: t("list.teacherType.FOREIGN")
  };
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
  /** V150 — dòng tổng hợp 1 Lô (homeworkBatchId khác null) mở rộng xem từng Bài con khi cần, thu gọn mặc định. */
  const [expandedBatchIds, setExpandedBatchIds] = useState<Set<number>>(new Set());

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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("list.loadFailed")))
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
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("list.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("list.subtitle")}</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {!selectedClassId ? (
        <Card>
          <EmptyState icon={BarChart3} title={t("list.noClassSelected.title")} description={t("list.noClassSelected.description")} />
        </Card>
      ) : (
        <Card padded={false} className="overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-3">
            <div className="flex items-center justify-between gap-3 flex-wrap">
              <span className="text-xs font-bold text-slate-700 font-display block">
                {selectedClass ? `${selectedClass.classCode} — ${selectedClass.name}` : t("list.selectedClassFallback")} (
                {hasActiveFilters ? `${filteredRows.length}/${rows.length}` : rows.length} {t("shared.assignmentUnit")})
              </span>
              {rows.length > 0 && (
                <div className="inline-flex rounded-lg border border-slate-200 bg-white p-0.5 gap-0.5 shrink-0">
                  {(
                    [
                      { value: null, label: t("list.teacherType.allShort") },
                      { value: "VIETNAMESE" as TeacherTypeFilter, label: t("list.teacherType.vnShort") },
                      { value: "FOREIGN" as TeacherTypeFilter, label: t("list.teacherType.foreignShort") }
                    ] satisfies { value: TeacherTypeFilter | null; label: string }[]
                  ).map((opt) => (
                    <button
                      key={opt.label}
                      type="button"
                      onClick={() => setTeacherTypeFilter(opt.value)}
                      title={opt.value ? teacherTypeLabels[opt.value] : t("list.teacherType.all")}
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
                    placeholder={t("list.searchPlaceholder")}
                    className="w-full bg-white border border-slate-200 text-xs pl-8 pr-3 py-2 rounded-lg focus:outline-none"
                  />
                </div>
                <div className="sm:w-52">
                  <DatePicker value={dateFilter} onChange={setDateFilter} placeholder={t("list.dateFilterPlaceholder")} />
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
                    <X className="w-3.5 h-3.5" /> {t("list.clearFilters")}
                  </button>
                )}
              </div>
            )}
          </div>
          {loading ? (
            <p className="text-xs text-slate-500 p-5">{t("shared.loading")}</p>
          ) : rows.length === 0 ? (
            <EmptyState icon={BarChart3} title={t("list.emptyNoRows.title")} description={t("list.emptyNoRows.description")} />
          ) : filteredRows.length === 0 ? (
            <EmptyState icon={Search} title={t("list.emptyNoResults.title")} description={t("list.emptyNoResults.description")} />
          ) : (
            <>
            <TableContainer className="border-0 rounded-none">
              <thead>
                <tr>
                  <Th>{t("list.table.title")}</Th>
                  <Th>{t("list.table.type")}</Th>
                  <Th>{t("list.table.assignedDate")}</Th>
                  <Th>{t("list.table.dueDate")}</Th>
                  <Th className="text-center">{t("list.table.completionPercent")}</Th>
                  <Th className="text-center">{t("list.table.passPercent")}</Th>
                  <Th className="text-center">{t("list.table.violationPercent")}</Th>
                  <Th />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {pageRows.map((r) =>
                  r.kind === "exercise" ? (
                    r.data.batchMembers != null ? (
                      <BatchGroupRows
                        key={`batch-${r.data.homeworkBatchId}`}
                        data={r.data}
                        expanded={expandedBatchIds.has(r.data.homeworkBatchId as number)}
                        onToggle={() =>
                          setExpandedBatchIds((prev) => {
                            const next = new Set(prev);
                            const id = r.data.homeworkBatchId as number;
                            if (next.has(id)) next.delete(id);
                            else next.add(id);
                            return next;
                          })
                        }
                        exerciseTypeLabels={exerciseTypeLabels}
                        i18n={i18n}
                        t={t}
                        navigate={navigate}
                      />
                    ) : (
                    <tr key={`exercise-${r.data.assignmentId}`}>
                      <Td className="font-semibold text-slate-900">
                        {r.data.exerciseTitle} <span className="text-slate-400 font-mono text-[10px]">({r.data.exerciseCode})</span>
                      </Td>
                      <Td>
                        <Badge variant="neutral">{exerciseTypeLabels[r.data.exerciseType]}</Badge>
                      </Td>
                      <Td>{formatDate(r.data.availableFrom, i18n.language, t("list.noDueDate"))}</Td>
                      <Td>{formatDate(r.data.dueAt, i18n.language, t("list.noDueDate"))}</Td>
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
                          {t("shared.viewDetail")}
                        </Button>
                      </Td>
                    </tr>
                    )
                  ) : (
                    <tr key={`review-video-${r.data.assignmentId}`}>
                      <Td className="font-semibold text-slate-900">
                        {r.data.reviewVideoSetTitle} <span className="text-slate-400 font-mono text-[10px]">({r.data.reviewVideoSetCode})</span>
                      </Td>
                      <Td>
                        <Badge variant="info">{reviewVideoTypeLabels[r.data.videoType]}</Badge>
                      </Td>
                      <Td>{formatDate(r.data.availableFrom, i18n.language, t("list.noDueDate"))}</Td>
                      <Td>{formatDate(r.data.dueAt, i18n.language, t("list.noDueDate"))}</Td>
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
                          {t("shared.viewDetail")}
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
              itemLabel={t("shared.assignmentUnit")}
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
