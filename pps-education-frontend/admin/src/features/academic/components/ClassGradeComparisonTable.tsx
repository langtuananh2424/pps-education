import React, { useEffect, useMemo, useState } from "react";
import { Minus, TrendingDown, TrendingUp } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  ClassEnrollmentResponse,
  GradeComponentResponse,
  GradeEntryResponse,
  GradePeriodResponse,
  GradePeriodResultResponse,
  listGradeComponents,
  listGradeEntries,
  listGradePeriods,
  listPeriodResults
} from "../api";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

interface ClassGradeComparisonTableProps {
  classId: number;
  curriculumId: number;
  enrollments: ClassEnrollmentResponse[];
  /**
   * true: bỏ qua lọc ACTIVE, hiện đúng danh sách `enrollments` truyền vào — dùng khi nhúng bảng này cho
   * 1 học sinh cụ thể (kể cả đã rút lớp) ở StudentInfoModal. Bảng tổng hợp cả lớp (ClassGradeSheetPanel)
   * KHÔNG truyền cờ này, vẫn giữ nguyên chỉ hiện học sinh ACTIVE như trước (2026-07-30).
   */
  includeAllStatuses?: boolean;
}

/**
 * UC-19/20 (bổ sung, đã xác nhận với người dùng 2026-07-29): tổng hợp điểm CẢ LỚP qua TẤT CẢ kỳ
 * điểm của khung chương trình, mỗi đầu điểm (gộp theo `code` — VD LISTENING/READING/... vì mỗi kỳ
 * tự tạo ra bản ghi GradeComponent riêng, không dùng chung id giữa các kỳ) tách thành nhiều cột con
 * theo từng kỳ để Giáo viên so sánh 1 học sinh có tiến bộ qua các kỳ không. CHỈ XEM — nhập/sửa điểm
 * vẫn làm ở GradeSheetTable (theo từng kỳ riêng lẻ) như cũ.
 */
export default function ClassGradeComparisonTable({ classId, curriculumId, enrollments, includeAllStatuses }: ClassGradeComparisonTableProps) {
  const [periods, setPeriods] = useState<GradePeriodResponse[]>([]);
  const [componentsByPeriod, setComponentsByPeriod] = useState<Map<number, GradeComponentResponse[]>>(new Map());
  const [entriesByComponent, setEntriesByComponent] = useState<Map<number, GradeEntryResponse[]>>(new Map());
  const [resultsByPeriod, setResultsByPeriod] = useState<Map<number, GradePeriodResultResponse[]>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listGradePeriods(curriculumId)
      .then(async (periodList) => {
        const sorted = [...periodList].sort((a, b) => a.displayOrder - b.displayOrder);
        setPeriods(sorted);
        const componentEntries = await Promise.all(sorted.map((p) => listGradeComponents(p.id).then((cs) => [p.id, cs] as const)));
        setComponentsByPeriod(new Map(componentEntries));
        const allComponents = componentEntries.flatMap(([, cs]) => cs);
        const [entryResults, periodResults] = await Promise.all([
          Promise.all(allComponents.map((c) => listGradeEntries(classId, c.id).then((es) => [c.id, es] as const))),
          Promise.all(sorted.map((p) => listPeriodResults(classId, p.id).then((rs) => [p.id, rs] as const)))
        ]);
        setEntriesByComponent(new Map(entryResults));
        setResultsByPeriod(new Map(periodResults));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được bảng tổng hợp điểm."))
      .finally(() => setLoading(false));
  }, [classId, curriculumId]);

  /** Gộp đầu điểm theo `code` xuyên suốt các kỳ — mỗi kỳ tự có bản ghi GradeComponent riêng (không chia sẻ id). */
  const codeGroups = useMemo(() => {
    const labelByCode = new Map<string, string>();
    const componentByCodeAndPeriod = new Map<string, Map<number, number>>(); // code -> periodId -> componentId
    componentsByPeriod.forEach((comps, periodId) => {
      comps.forEach((c) => {
        if (!labelByCode.has(c.code)) labelByCode.set(c.code, c.name);
        if (!componentByCodeAndPeriod.has(c.code)) componentByCodeAndPeriod.set(c.code, new Map());
        componentByCodeAndPeriod.get(c.code)!.set(periodId, c.id);
      });
    });
    return Array.from(labelByCode.entries()).map(([code, label]) => ({
      code,
      label,
      componentIdByPeriod: componentByCodeAndPeriod.get(code)!
    }));
  }, [componentsByPeriod]);

  const activeStudents = includeAllStatuses ? enrollments : enrollments.filter((en) => en.status === "ACTIVE");

  const scoreFor = (componentId: number | undefined, studentId: number): number | null => {
    if (componentId == null) return null;
    const entry = (entriesByComponent.get(componentId) ?? []).find((e) => e.studentId === studentId);
    return entry ? entry.score : null;
  };

  const overallFor = (periodId: number, studentId: number): number | null => {
    const result = (resultsByPeriod.get(periodId) ?? []).find((r) => r.studentId === studentId);
    return result?.overallScore ?? null;
  };

  const TrendIcon = ({ current, previous }: { current: number | null; previous: number | null }) => {
    if (current == null || previous == null) return null;
    if (current > previous) return <TrendingUp className="w-3 h-3 text-emerald-600 inline ml-1" aria-label="Tăng so với kỳ trước" />;
    if (current < previous) return <TrendingDown className="w-3 h-3 text-rose-600 inline ml-1" aria-label="Giảm so với kỳ trước" />;
    return <Minus className="w-3 h-3 text-slate-400 inline ml-1" aria-label="Không đổi so với kỳ trước" />;
  };

  if (loading) return <p className="text-xs text-slate-400 italic p-6 text-center">Đang tải bảng tổng hợp điểm...</p>;
  if (periods.length === 0) return <p className="text-xs text-slate-400 italic p-6 text-center">Khung chương trình này chưa có kỳ điểm nào.</p>;

  return (
    <div>
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 m-3 rounded-lg">{error}</div>}
      <div className="overflow-x-auto">
        <TableContainer className="rounded-none border-0">
          <thead>
            <tr>
              <Th rowSpan={2} className="align-bottom">
                Mã HS
              </Th>
              <Th rowSpan={2} className="align-bottom">
                Học sinh
              </Th>
              {codeGroups.map((g) => (
                <Th key={g.code} colSpan={periods.length} className="text-center whitespace-nowrap border-l border-slate-200">
                  {g.label.toUpperCase()}
                </Th>
              ))}
              <Th colSpan={periods.length} className="text-center whitespace-nowrap border-l border-slate-200">
                OVERALL
              </Th>
            </tr>
            <tr>
              {codeGroups.map((g) =>
                periods.map((p, i) => (
                  <Th key={`${g.code}-${p.id}`} className={`text-center text-[10px] font-semibold whitespace-nowrap ${i === 0 ? "border-l border-slate-200" : ""}`}>
                    {p.name}
                  </Th>
                ))
              )}
              {periods.map((p, i) => (
                <Th key={`overall-${p.id}`} className={`text-center text-[10px] font-semibold whitespace-nowrap ${i === 0 ? "border-l border-slate-200" : ""}`}>
                  {p.name}
                </Th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {activeStudents.length === 0 ? (
              <tr>
                <td colSpan={2 + (codeGroups.length + 1) * periods.length} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  Lớp chưa có học sinh nào đang ghi danh.
                </td>
              </tr>
            ) : (
              activeStudents.map((en) => (
                <tr key={en.studentId} className="hover:bg-slate-50/40 transition-colors">
                  <Td className="font-mono font-bold text-slate-500 whitespace-nowrap">{en.studentCode}</Td>
                  <Td className="font-bold text-slate-900 whitespace-nowrap">{en.studentFullName}</Td>
                  {codeGroups.map((g) => {
                    const scores = periods.map((p) => scoreFor(g.componentIdByPeriod.get(p.id), en.studentId));
                    return periods.map((p, i) => (
                      <Td key={`${g.code}-${p.id}`} className={`text-center whitespace-nowrap ${i === 0 ? "border-l border-slate-200" : ""}`}>
                        {scores[i] == null ? (
                          <span className="text-slate-300">—</span>
                        ) : (
                          <span className="text-xs font-semibold text-slate-700">
                            {scores[i]}
                            {i > 0 && <TrendIcon current={scores[i]} previous={scores[i - 1]} />}
                          </span>
                        )}
                      </Td>
                    ));
                  })}
                  {periods.map((p, i) => {
                    const overalls = periods.map((pp) => overallFor(pp.id, en.studentId));
                    return (
                      <Td key={`overall-${p.id}`} className={`text-center whitespace-nowrap ${i === 0 ? "border-l border-slate-200" : ""}`}>
                        {overalls[i] == null ? (
                          <span className="text-slate-300">—</span>
                        ) : (
                          <span className="text-xs font-bold text-brand-orange">
                            {overalls[i]}
                            {i > 0 && <TrendIcon current={overalls[i]} previous={overalls[i - 1]} />}
                          </span>
                        )}
                      </Td>
                    );
                  })}
                </tr>
              ))
            )}
          </tbody>
        </TableContainer>
      </div>
    </div>
  );
}
