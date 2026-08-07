import React, { useEffect, useMemo, useState } from "react";
import { Award, TrendingDown, TrendingUp } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  GradeComponentSetupResponse,
  GradeEntryResponse,
  GradeEvaluationComponentResponse,
  GradeEvaluationResultResponse,
  GradeStatus,
  getMyEvaluationResult,
  getEvaluationResult,
  listGradeComponentSetups,
  listGradeEvaluationComponents,
  listGrades,
  listMyGrades
} from "../api";
import ComingSoon from "./ComingSoon";

interface GradesTabProps {
  /** Không truyền = tự xem điểm của chính mình (UC-61, Học sinh). Có truyền = Phụ huynh xem theo con cụ thể (UC-25). */
  studentId?: number;
  classId: number;
}

const scaleLabels: Record<GradeEvaluationResultResponse["scaleType"], string> = {
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

function setupLabel(s: GradeComponentSetupResponse): string {
  return `${s.academicTermName} — ${s.evaluationType === "MID_TERM" ? "Giữa kỳ" : "Cuối kỳ"}`;
}

function TrendIcon({ current, previous }: { current: number; previous: number }) {
  if (current > previous) return <TrendingUp className="w-3 h-3 text-emerald-600 inline ml-1" aria-label="Tăng so với kỳ trước" />;
  if (current < previous) return <TrendingDown className="w-3 h-3 text-rose-600 inline ml-1" aria-label="Giảm so với kỳ trước" />;
  return null;
}

export default function GradesTab({ studentId, classId }: GradesTabProps) {
  const [grades, setGrades] = useState<GradeEntryResponse[]>([]);
  const [setups, setSetups] = useState<GradeComponentSetupResponse[]>([]);
  const [setupResults, setSetupResults] = useState<{ setup: GradeComponentSetupResponse; result: GradeEvaluationResultResponse }[]>([]);
  const [componentNames, setComponentNames] = useState<Map<number, string>>(new Map());
  /** Đầu điểm (Listening/Reading/...) lặp lại giữa các setup (VD Giữa kỳ 1 và Cuối kỳ 1 đều có Listening riêng) — cần tra ngược setup của từng đầu điểm để nhóm/hiện rõ, tránh học sinh nhầm 2 điểm Listening là trùng nhau. */
  const [componentSetupId, setComponentSetupId] = useState<Map<number, number>>(new Map());
  /** Danh sách đầy đủ đầu điểm của mọi setup (kể cả chưa có điểm) — dùng để dựng bảng gộp Đầu điểm × Kỳ (2026-07-29, cùng hướng với bảng tổng hợp UC-19 phía Giáo viên). */
  const [allComponents, setAllComponents] = useState<GradeEvaluationComponentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    setError(null);
    const fetchGrades = studentId != null ? listGrades(studentId, classId) : listMyGrades(classId);
    const fetchEvaluationResult = (s: GradeComponentSetupResponse) =>
      studentId != null
        ? getEvaluationResult(studentId, classId, s.academicTermId, s.evaluationType)
        : getMyEvaluationResult(classId, s.academicTermId, s.evaluationType);

    const setupsPromise = listGradeComponentSetups(classId);

    Promise.all([
      fetchGrades,
      setupsPromise,
      setupsPromise.then((setupList) =>
        Promise.all(
          setupList.map((s) =>
            fetchEvaluationResult(s)
              .then((result) => ({ setup: s, result }))
              .catch((err) => {
                if (err instanceof ApiError && err.status === 404) return null; // chưa có/chưa duyệt cho setup này — bỏ qua, không phải lỗi
                throw err;
              })
          )
        )
      ).then((rows) => rows.filter((r): r is { setup: GradeComponentSetupResponse; result: GradeEvaluationResultResponse } => r !== null)),
      // Đầu điểm (Listening/Reading/...) — GradeEntryResponse chỉ có gradeEvaluationComponentId, phải tra tên qua đây (UC-19 group tables).
      setupsPromise.then((setupList) => Promise.all(setupList.map((s) => listGradeEvaluationComponents(s.id))))
    ])
      .then(([entries, fetchedSetups, results, componentsBySetup]) => {
        setGrades(entries);
        setSetups(fetchedSetups);
        setSetupResults(results);
        const flatComponents = componentsBySetup.flat();
        setComponentNames(new Map(flatComponents.map((c) => [c.id, c.name])));
        setComponentSetupId(new Map(flatComponents.map((c) => [c.id, c.gradeComponentSetupId])));
        setAllComponents(flatComponents);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được điểm số."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [studentId, classId]);

  const sortedSetups = useMemo(
    () => [...setups].sort((a, b) => a.academicTermId - b.academicTermId || a.evaluationType.localeCompare(b.evaluationType)),
    [setups]
  );

  /** Gộp đầu điểm theo `code` xuyên suốt các setup (mỗi setup tự có bản ghi riêng, không dùng chung id) — dựng bảng Đầu điểm × Kỳ để xem tiến bộ, cùng hướng với bảng tổng hợp UC-19 phía Giáo viên (2026-07-29). */
  const codeGroups = useMemo(() => {
    const labelByCode = new Map<string, string>();
    const componentIdByCodeAndSetup = new Map<string, Map<number, number>>();
    allComponents.forEach((c) => {
      if (!labelByCode.has(c.code)) labelByCode.set(c.code, c.name);
      if (!componentIdByCodeAndSetup.has(c.code)) componentIdByCodeAndSetup.set(c.code, new Map());
      componentIdByCodeAndSetup.get(c.code)!.set(c.gradeComponentSetupId, c.id);
    });
    return Array.from(labelByCode.entries()).map(([code, label]) => ({
      code,
      label,
      componentIdBySetup: componentIdByCodeAndSetup.get(code)!
    }));
  }, [allComponents]);

  const entryByComponentId = useMemo(() => new Map(grades.map((g) => [g.gradeEvaluationComponentId, g])), [grades]);
  const resultBySetupId = useMemo(() => new Map(setupResults.map((sr) => [sr.setup.id, sr.result])), [setupResults]);

  /** Điểm không tra được đúng đầu điểm/setup nào (hiếm — VD đầu điểm gốc đã bị xoá sau khi điểm đã nhập) — không lên được bảng gộp, liệt kê riêng bên dưới để không mất dữ liệu. */
  const ungroupedGrades = grades.filter((g) => !componentSetupId.has(g.gradeEvaluationComponentId));

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-5">
        <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
          <Award className="text-teal" /> Bảng điểm tổng hợp
        </h2>
        {grades.length === 0 && setupResults.length === 0 ? (
          <p className="text-xs text-muted font-bold italic">Chưa có điểm nào được công bố cho lớp này.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-100/80 border-b border-line text-sm font-black uppercase text-[#6e7c93] tracking-wider whitespace-nowrap">
                  <th className="p-3 pl-4">Đầu điểm</th>
                  {sortedSetups.map((s) => (
                    <th key={s.id} className="p-3 text-center border-l border-line/60">
                      {setupLabel(s)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-line/60 text-sm font-bold text-ink">
                {codeGroups.map((g) => {
                  const scores = sortedSetups.map((s) => {
                    const componentId = g.componentIdBySetup.get(s.id);
                    return componentId != null ? entryByComponentId.get(componentId) : undefined;
                  });
                  return (
                    <tr key={g.code} className="hover:bg-slate-50/80">
                      <td className="p-3 pl-4 font-extrabold text-ink whitespace-nowrap">{g.label}</td>
                      {sortedSetups.map((s, i) => {
                        const entry = scores[i];
                        const prevEntry = i > 0 ? scores[i - 1] : undefined;
                        return (
                          <td key={s.id} className="p-3 text-center border-l border-line/60">
                            {!entry ? (
                              <span className="text-slate-300">—</span>
                            ) : (
                              <div className="inline-flex flex-col items-center gap-0.5">
                                <span className={`text-[17px] font-extrabold ${statusTextClasses[entry.status]}`}>
                                  {entry.absenceFlag ? "—" : entry.score}
                                  {!entry.absenceFlag && prevEntry && !prevEntry.absenceFlag && <TrendIcon current={entry.score} previous={prevEntry.score} />}
                                </span>
                                {entry.absenceFlag && <span className="text-[11px] text-coral font-extrabold uppercase">Vắng</span>}
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
                  {sortedSetups.map((s, i) => {
                    const result = resultBySetupId.get(s.id);
                    const prevResult = i > 0 ? resultBySetupId.get(sortedSetups[i - 1].id) : undefined;
                    return (
                      <td key={s.id} className="p-3 text-center border-l border-line/60">
                        {!result ? (
                          <span className="text-slate-300">—</span>
                        ) : (
                          <div className="inline-flex flex-col items-center gap-0.5">
                            <span className={`text-[17px] font-extrabold ${statusTextClasses[result.status]}`}>
                              {result.overallScore ?? "—"}
                              {result.overallScore != null && prevResult?.overallScore != null && (
                                <TrendIcon current={result.overallScore} previous={prevResult.overallScore} />
                              )}
                            </span>
                          </div>
                        )}
                      </td>
                    );
                  })}
                </tr>
                <tr className="hover:bg-slate-50/80 bg-sky-1">
                  <td className="p-3 pl-4 font-extrabold text-ink whitespace-nowrap">Level</td>
                  {sortedSetups.map((s) => {
                    const result = resultBySetupId.get(s.id);
                    return (
                      <td key={s.id} className="p-3 text-center border-l border-line/60">
                        {!result || !result.level ? (
                          <span className="text-slate-300">—</span>
                        ) : (
                          <span className="text-[22px] font-black text-teal-deep">{result.level}</span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              </tbody>
            </table>
          </div>
        )}

        {/* V94 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): Nhận xét/Ghi chú tích hợp vào sổ điểm, hiển thị theo từng setup đã công bố. V100: Lưu ý bổ sung. */}
        {setupResults.filter((sr) => sr.result.disclaimer || sr.result.comment || sr.result.note).length > 0 && (
          <div className="space-y-2 pt-2">
            {setupResults.filter((sr) => sr.result.disclaimer).length > 0 && (
              <>
                <h3 className="text-[13px] font-extrabold text-muted uppercase tracking-wide border-b border-line/60 pb-1.5">Lưu ý</h3>
                {setupResults
                  .filter((sr) => sr.result.disclaimer)
                  .map((sr) => (
                    <div key={`disclaimer-${sr.setup.id}`} className="border border-line/60 p-4 rounded-[16px] bg-amber-50 space-y-1">
                      <p className="text-sm font-extrabold text-amber-900">{setupLabel(sr.setup)}</p>
                      <p className="text-sm text-amber-800/90 leading-relaxed">{sr.result.disclaimer}</p>
                    </div>
                  ))}
              </>
            )}
            {setupResults.filter((sr) => sr.result.comment || sr.result.note).length > 0 && (
              <>
                <h3 className="text-[13px] font-extrabold text-muted uppercase tracking-wide border-b border-line/60 pb-1.5 pt-2">Nhận xét của giáo viên</h3>
                {setupResults
                  .filter((sr) => sr.result.comment || sr.result.note)
                  .map((sr) => (
                    <div key={sr.setup.id} className="border border-line/60 p-4 rounded-[16px] bg-sky-2 space-y-1">
                      <p className="text-sm font-extrabold text-ink">{setupLabel(sr.setup)}</p>
                      {sr.result.comment && <p className="text-sm text-ink/80">{sr.result.comment}</p>}
                      {sr.result.note && <p className="text-[13px] text-muted italic">{sr.result.note}</p>}
                    </div>
                  ))}
              </>
            )}
          </div>
        )}

        {ungroupedGrades.length > 0 && (
          <div className="space-y-3 pt-2">
            <h3 className="text-[13px] font-extrabold text-muted uppercase tracking-wide border-b border-line/60 pb-1.5">Khác (chưa xác định kỳ)</h3>
            {ungroupedGrades.map((g) => (
              <div key={g.id} className="border border-line/60 p-4 rounded-[16px] flex flex-wrap justify-between items-center gap-2 bg-sky-2">
                <div>
                  <p className="text-sm font-extrabold text-ink">{componentNames.get(g.gradeEvaluationComponentId) ?? "Đầu điểm"}</p>
                  {g.teacherNote && <p className="text-[12px] text-muted font-bold mt-0.5">{g.teacherNote}</p>}
                  {g.absenceFlag && <span className="text-[12px] text-coral font-extrabold uppercase">Vắng — không có điểm</span>}
                  <span className={`inline-block mt-1 text-[12px] font-extrabold uppercase px-2 py-0.5 rounded-full ${statusClasses[g.status]}`}>
                    {statusLabels[g.status]}
                  </span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-[17px] font-extrabold text-teal-deep">{g.absenceFlag ? "—" : g.score}</span>
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
