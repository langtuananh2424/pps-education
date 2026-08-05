import React, { useEffect, useMemo, useState } from "react";
import { Award, Minus, TrendingDown, TrendingUp } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  GradeComponentResponse,
  GradeEntryResponse,
  GradePeriodResponse,
  GradePeriodResultResponse,
  GradeStatus,
  getMyPeriodResult,
  getPeriodResult,
  getPortalClass,
  listGradeComponents,
  listGradePeriods,
  listGrades,
  listMyGrades
} from "../api";
import ComingSoon from "./ComingSoon";

interface GradesTabProps {
  /** Không truyền = tự xem điểm của chính mình (UC-61, Học sinh). Có truyền = Phụ huynh xem theo con cụ thể (UC-25). */
  studentId?: number;
  classId: number;
}

const scaleLabels: Record<GradePeriodResultResponse["scaleType"], string> = {
  NUMERIC: "Số (0–10)",
  PERCENTAGE: "Phần trăm (%)",
  BAND: "Thang chữ (Band)"
};

/**
 * V44: Portal chỉ trả về bản ghi đã OFFICIAL (Quản lý điểm trường duyệt) — DRAFT/
 * SUBMITTED/REJECTED không bao giờ hiển thị ở đây (BE tự lọc), nhưng vẫn khai đủ map
 * theo đúng GradeStatus để không thiếu nhánh nếu type mở rộng sau này.
 */
const statusLabels: Record<GradeStatus, string> = {
  DRAFT: "Nháp",
  SUBMITTED: "Chờ duyệt",
  OFFICIAL: "Chính thức",
  REJECTED: "Bị từ chối"
};

const statusClasses: Record<GradeStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-500",
  SUBMITTED: "bg-gold/10 text-gold",
  OFFICIAL: "bg-teal/10 text-teal-deep",
  REJECTED: "bg-coral/10 text-coral"
};

const statusTextClasses: Record<GradeStatus, string> = {
  DRAFT: "text-slate-400",
  SUBMITTED: "text-gold",
  OFFICIAL: "text-teal-deep",
  REJECTED: "text-coral"
};

function TrendIcon({ current, previous }: { current: number; previous: number }) {
  if (current > previous) return <TrendingUp className="w-3 h-3 text-emerald-600 inline ml-1" aria-label="Tăng so với kỳ trước" />;
  if (current < previous) return <TrendingDown className="w-3 h-3 text-rose-600 inline ml-1" aria-label="Giảm so với kỳ trước" />;
  return <Minus className="w-3 h-3 text-slate-400 inline ml-1" aria-label="Không đổi so với kỳ trước" />;
}

export default function GradesTab({ studentId, classId }: GradesTabProps) {
  const [grades, setGrades] = useState<GradeEntryResponse[]>([]);
  const [periods, setPeriods] = useState<GradePeriodResponse[]>([]);
  const [periodResults, setPeriodResults] = useState<{ period: GradePeriodResponse; result: GradePeriodResultResponse }[]>([]);
  const [componentNames, setComponentNames] = useState<Map<number, string>>(new Map());
  /** Đầu điểm (Listening/Reading/...) lặp lại giữa các kỳ (VD Giữa kỳ 1 và Cuối kỳ 1 đều có Listening riêng) — cần tra ngược kỳ của từng đầu điểm để nhóm/hiện rõ, tránh học sinh nhầm 2 điểm Listening là trùng nhau. */
  const [componentPeriodId, setComponentPeriodId] = useState<Map<number, number>>(new Map());
  /** Danh sách đầy đủ đầu điểm của mọi kỳ (kể cả chưa có điểm) — dùng để dựng bảng gộp Đầu điểm × Kỳ (2026-07-29, cùng hướng với bảng tổng hợp UC-19 phía Giáo viên). */
  const [allComponents, setAllComponents] = useState<GradeComponentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    setError(null);
    const fetchGrades = studentId != null ? listGrades(studentId, classId) : listMyGrades(classId);
    const fetchPeriodResult = (periodId: number) =>
      studentId != null ? getPeriodResult(studentId, classId, periodId) : getMyPeriodResult(classId, periodId);

    const periodsPromise = getPortalClass(classId).then((cls) => listGradePeriods(cls.curriculumId));

    Promise.all([
      fetchGrades,
      periodsPromise,
      periodsPromise.then((periods) =>
        Promise.all(
          periods.map((period) =>
            fetchPeriodResult(period.id)
              .then((result) => ({ period, result }))
              .catch((err) => {
                if (err instanceof ApiError && err.status === 404) return null; // chưa có/chưa duyệt cho kỳ này — bỏ qua, không phải lỗi
                throw err;
              })
          )
        )
      ).then((rows) => rows.filter((r): r is { period: GradePeriodResponse; result: GradePeriodResultResponse } => r !== null)),
      // Đầu điểm (Listening/Reading/...) — GradeEntryResponse chỉ có gradeComponentId, phải tra tên qua đây (UC-19 group tables).
      periodsPromise.then((periods) => Promise.all(periods.map((p) => listGradeComponents(p.id))))
    ])
      .then(([entries, fetchedPeriods, results, componentsByPeriod]) => {
        setGrades(entries);
        setPeriods(fetchedPeriods);
        setPeriodResults(results);
        const flatComponents = componentsByPeriod.flat();
        setComponentNames(new Map(flatComponents.map((c) => [c.id, c.name])));
        setComponentPeriodId(new Map(flatComponents.map((c) => [c.id, c.gradePeriodId])));
        setAllComponents(flatComponents);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được điểm số."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [studentId, classId]);

  const sortedPeriods = useMemo(() => [...periods].sort((a, b) => a.displayOrder - b.displayOrder), [periods]);

  /** Gộp đầu điểm theo `code` xuyên suốt các kỳ (mỗi kỳ tự có bản ghi riêng, không dùng chung id) — dựng bảng Đầu điểm × Kỳ để xem tiến bộ, cùng hướng với bảng tổng hợp UC-19 phía Giáo viên (2026-07-29). */
  const codeGroups = useMemo(() => {
    const labelByCode = new Map<string, string>();
    const componentIdByCodeAndPeriod = new Map<string, Map<number, number>>();
    allComponents.forEach((c) => {
      if (!labelByCode.has(c.code)) labelByCode.set(c.code, c.name);
      if (!componentIdByCodeAndPeriod.has(c.code)) componentIdByCodeAndPeriod.set(c.code, new Map());
      componentIdByCodeAndPeriod.get(c.code)!.set(c.gradePeriodId, c.id);
    });
    return Array.from(labelByCode.entries()).map(([code, label]) => ({
      code,
      label,
      componentIdByPeriod: componentIdByCodeAndPeriod.get(code)!
    }));
  }, [allComponents]);

  const entryByComponentId = useMemo(() => new Map(grades.map((g) => [g.gradeComponentId, g])), [grades]);
  const resultByPeriodId = useMemo(() => new Map(periodResults.map((pr) => [pr.period.id, pr.result])), [periodResults]);

  /** Điểm không tra được đúng đầu điểm/kỳ nào (hiếm — VD đầu điểm gốc đã bị xoá sau khi điểm đã nhập) — không lên được bảng gộp, liệt kê riêng bên dưới để không mất dữ liệu. */
  const ungroupedGrades = grades.filter((g) => !componentPeriodId.has(g.gradeComponentId));

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-5">
        <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
          <Award className="text-teal" /> Bảng điểm tổng hợp qua các kỳ
        </h2>
        {grades.length === 0 && periodResults.length === 0 ? (
          <p className="text-xs text-muted font-bold italic">Chưa có điểm nào được công bố cho lớp này.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-100/80 border-b border-line text-[10px] font-black uppercase text-[#6e7c93] tracking-wider whitespace-nowrap">
                  <th className="p-3 pl-4">Đầu điểm</th>
                  {sortedPeriods.map((p) => (
                    <th key={p.id} className="p-3 text-center border-l border-line/60">
                      {p.name}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-line/60 text-xs font-bold text-ink">
                {codeGroups.map((g) => {
                  const scores = sortedPeriods.map((p) => {
                    const componentId = g.componentIdByPeriod.get(p.id);
                    return componentId != null ? entryByComponentId.get(componentId) : undefined;
                  });
                  return (
                    <tr key={g.code} className="hover:bg-slate-50/80">
                      <td className="p-3 pl-4 font-extrabold text-ink whitespace-nowrap">{g.label}</td>
                      {sortedPeriods.map((p, i) => {
                        const entry = scores[i];
                        const prevEntry = i > 0 ? scores[i - 1] : undefined;
                        return (
                          <td key={p.id} className="p-3 text-center border-l border-line/60">
                            {!entry ? (
                              <span className="text-slate-300">—</span>
                            ) : (
                              <div className="inline-flex flex-col items-center gap-0.5">
                                <span className={`text-sm font-extrabold ${statusTextClasses[entry.status]}`}>
                                  {entry.absenceFlag ? "—" : entry.score}
                                  {!entry.absenceFlag && prevEntry && !prevEntry.absenceFlag && <TrendIcon current={entry.score} previous={prevEntry.score} />}
                                </span>
                                {entry.absenceFlag && <span className="text-[9px] text-coral font-extrabold uppercase">Vắng</span>}
                              </div>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  );
                })}
                <tr className="hover:bg-slate-50/80 bg-gold/5">
                  <td className="p-3 pl-4 font-extrabold text-ink whitespace-nowrap">Overall</td>
                  {sortedPeriods.map((p, i) => {
                    const result = resultByPeriodId.get(p.id);
                    const prevResult = i > 0 ? resultByPeriodId.get(sortedPeriods[i - 1].id) : undefined;
                    return (
                      <td key={p.id} className="p-3 text-center border-l border-line/60">
                        {!result ? (
                          <span className="text-slate-300">—</span>
                        ) : (
                          <div className="inline-flex flex-col items-center gap-0.5">
                            <span className={`text-sm font-extrabold ${statusTextClasses[result.status]}`}>
                              {result.overallScore ?? "—"}
                              {result.overallScore != null && prevResult?.overallScore != null && (
                                <TrendIcon current={result.overallScore} previous={prevResult.overallScore} />
                              )}
                            </span>
                            {result.level && <span className="text-[9px] text-muted font-bold">{result.level}</span>}
                          </div>
                        )}
                      </td>
                    );
                  })}
                </tr>
              </tbody>
            </table>
          </div>
        )}

        {ungroupedGrades.length > 0 && (
          <div className="space-y-3 pt-2">
            <h3 className="text-[11px] font-extrabold text-muted uppercase tracking-wide border-b border-line/60 pb-1.5">Khác (chưa xác định kỳ)</h3>
            {ungroupedGrades.map((g) => (
              <div key={g.id} className="border border-line/60 p-4 rounded-[16px] flex flex-wrap justify-between items-center gap-2 bg-sky-2">
                <div>
                  <p className="text-xs font-extrabold text-ink">{componentNames.get(g.gradeComponentId) ?? "Đầu điểm"}</p>
                  {g.teacherNote && <p className="text-[10px] text-muted font-bold mt-0.5">{g.teacherNote}</p>}
                  {g.absenceFlag && <span className="text-[10px] text-coral font-extrabold uppercase">Vắng — không có điểm</span>}
                  <span className={`inline-block mt-1 text-[10px] font-extrabold uppercase px-2 py-0.5 rounded-full ${statusClasses[g.status]}`}>
                    {statusLabels[g.status]}
                  </span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-lg font-extrabold text-teal-deep">{g.absenceFlag ? "—" : g.score}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <ComingSoon
        title="Làm bài kiểm tra trực tuyến"
        description="Đang trong quá trình phát triển"
      />
    </div>
  );
}
