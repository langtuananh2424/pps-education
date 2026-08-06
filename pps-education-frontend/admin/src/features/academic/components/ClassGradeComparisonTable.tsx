import React, { useEffect, useMemo, useState } from "react";
import { Minus, TrendingDown, TrendingUp } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  ClassEnrollmentResponse,
  GradeComponentSetupResponse,
  GradeEntryResponse,
  GradeEvaluationComponentResponse,
  GradeEvaluationResultResponse,
  listEvaluationResults,
  listGradeComponentSetups,
  listGradeEntries,
  listGradeEvaluationComponents
} from "../api";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

interface ClassGradeComparisonTableProps {
  classId: number;
  enrollments: ClassEnrollmentResponse[];
  /**
   * true: bỏ qua lọc ACTIVE, hiện đúng danh sách `enrollments` truyền vào — dùng khi nhúng bảng này cho
   * 1 học sinh cụ thể (kể cả đã rút lớp) ở StudentInfoModal. Bảng tổng hợp cả lớp (ClassGradeSheetPanel)
   * KHÔNG truyền cờ này, vẫn giữ nguyên chỉ hiện học sinh ACTIVE như trước (2026-07-30).
   */
  includeAllStatuses?: boolean;
}

function setupLabel(s: GradeComponentSetupResponse): string {
  return `${s.academicTermName} — ${s.evaluationType === "MID_TERM" ? "GK" : "CK"}`;
}

/**
 * UC-19/20 (bổ sung, đã xác nhận với người dùng 2026-07-29): tổng hợp điểm CẢ LỚP qua TẤT CẢ setup sổ
 * điểm của lớp (V94 — trước đây là "kỳ điểm của khung chương trình"), mỗi đầu điểm (gộp theo `code` —
 * VD LISTENING/READING/... vì mỗi setup tự tạo ra bản ghi GradeEvaluationComponent riêng, không dùng
 * chung id giữa các setup) tách thành nhiều cột con theo từng setup để Giáo viên so sánh 1 học sinh có
 * tiến bộ qua các kỳ không. CHỈ XEM — nhập/sửa điểm vẫn làm ở GradeSheetTable (theo từng setup riêng lẻ).
 */
export default function ClassGradeComparisonTable({ classId, enrollments, includeAllStatuses }: ClassGradeComparisonTableProps) {
  const [setups, setSetups] = useState<GradeComponentSetupResponse[]>([]);
  const [componentsBySetup, setComponentsBySetup] = useState<Map<number, GradeEvaluationComponentResponse[]>>(new Map());
  const [entriesByComponent, setEntriesByComponent] = useState<Map<number, GradeEntryResponse[]>>(new Map());
  const [resultsBySetup, setResultsBySetup] = useState<Map<number, GradeEvaluationResultResponse[]>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listGradeComponentSetups(classId)
      .then(async (setupList) => {
        const sorted = [...setupList].sort((a, b) => a.academicTermId - b.academicTermId || a.evaluationType.localeCompare(b.evaluationType));
        setSetups(sorted);
        const componentEntries = await Promise.all(sorted.map((s) => listGradeEvaluationComponents(s.id).then((cs) => [s.id, cs] as const)));
        setComponentsBySetup(new Map(componentEntries));
        const allComponents = componentEntries.flatMap(([, cs]) => cs);
        const [entryResults, setupResults] = await Promise.all([
          Promise.all(allComponents.map((c) => listGradeEntries(classId, c.id).then((es) => [c.id, es] as const))),
          Promise.all(sorted.map((s) => listEvaluationResults(classId, s.id).then((rs) => [s.id, rs] as const)))
        ]);
        setEntriesByComponent(new Map(entryResults));
        setResultsBySetup(new Map(setupResults));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được bảng tổng hợp điểm."))
      .finally(() => setLoading(false));
  }, [classId]);

  /** Gộp đầu điểm theo `code` xuyên suốt các setup — mỗi setup tự có bản ghi GradeEvaluationComponent riêng (không chia sẻ id). */
  const codeGroups = useMemo(() => {
    const labelByCode = new Map<string, string>();
    const componentByCodeAndSetup = new Map<string, Map<number, number>>(); // code -> setupId -> componentId
    componentsBySetup.forEach((comps, setupId) => {
      comps.forEach((c) => {
        if (!labelByCode.has(c.code)) labelByCode.set(c.code, c.name);
        if (!componentByCodeAndSetup.has(c.code)) componentByCodeAndSetup.set(c.code, new Map());
        componentByCodeAndSetup.get(c.code)!.set(setupId, c.id);
      });
    });
    return Array.from(labelByCode.entries()).map(([code, label]) => ({
      code,
      label,
      componentIdBySetup: componentByCodeAndSetup.get(code)!
    }));
  }, [componentsBySetup]);

  const activeStudents = includeAllStatuses ? enrollments : enrollments.filter((en) => en.status === "ACTIVE");

  const scoreFor = (componentId: number | undefined, studentId: number): number | null => {
    if (componentId == null) return null;
    const entry = (entriesByComponent.get(componentId) ?? []).find((e) => e.studentId === studentId);
    return entry ? entry.score : null;
  };

  const overallFor = (setupId: number, studentId: number): number | null => {
    const result = (resultsBySetup.get(setupId) ?? []).find((r) => r.studentId === studentId);
    return result?.overallScore ?? null;
  };

  const TrendIcon = ({ current, previous }: { current: number | null; previous: number | null }) => {
    if (current == null || previous == null) return null;
    if (current > previous) return <TrendingUp className="w-3 h-3 text-emerald-600 inline ml-1" aria-label="Tăng so với kỳ trước" />;
    if (current < previous) return <TrendingDown className="w-3 h-3 text-rose-600 inline ml-1" aria-label="Giảm so với kỳ trước" />;
    return <Minus className="w-3 h-3 text-slate-400 inline ml-1" aria-label="Không đổi so với kỳ trước" />;
  };

  if (loading) return <p className="text-xs text-slate-400 italic p-6 text-center">Đang tải bảng tổng hợp điểm...</p>;
  if (setups.length === 0) return <p className="text-xs text-slate-400 italic p-6 text-center">Lớp này chưa có setup sổ điểm nào.</p>;

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
                <Th key={g.code} colSpan={setups.length} className="text-center whitespace-nowrap border-l border-slate-200">
                  {g.label.toUpperCase()}
                </Th>
              ))}
              <Th colSpan={setups.length} className="text-center whitespace-nowrap border-l border-slate-200">
                OVERALL
              </Th>
            </tr>
            <tr>
              {codeGroups.map((g) =>
                setups.map((s, i) => (
                  <Th key={`${g.code}-${s.id}`} className={`text-center text-[10px] font-semibold whitespace-nowrap ${i === 0 ? "border-l border-slate-200" : ""}`}>
                    {setupLabel(s)}
                  </Th>
                ))
              )}
              {setups.map((s, i) => (
                <Th key={`overall-${s.id}`} className={`text-center text-[10px] font-semibold whitespace-nowrap ${i === 0 ? "border-l border-slate-200" : ""}`}>
                  {setupLabel(s)}
                </Th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {activeStudents.length === 0 ? (
              <tr>
                <td colSpan={2 + (codeGroups.length + 1) * setups.length} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  Lớp chưa có học sinh nào đang ghi danh.
                </td>
              </tr>
            ) : (
              activeStudents.map((en) => (
                <tr key={en.studentId} className="hover:bg-slate-50/40 transition-colors">
                  <Td className="font-mono font-bold text-slate-500 whitespace-nowrap">{en.studentCode}</Td>
                  <Td className="font-bold text-slate-900 whitespace-nowrap">{en.studentFullName}</Td>
                  {codeGroups.map((g) => {
                    const scores = setups.map((s) => scoreFor(g.componentIdBySetup.get(s.id), en.studentId));
                    return setups.map((s, i) => (
                      <Td key={`${g.code}-${s.id}`} className={`text-center whitespace-nowrap ${i === 0 ? "border-l border-slate-200" : ""}`}>
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
                  {setups.map((s, i) => {
                    const overalls = setups.map((ss) => overallFor(ss.id, en.studentId));
                    return (
                      <Td key={`overall-${s.id}`} className={`text-center whitespace-nowrap ${i === 0 ? "border-l border-slate-200" : ""}`}>
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
