import React, { useEffect, useMemo, useState } from "react";
import { Award, Clock, Minus, TrendingDown, TrendingUp } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  GradeAppealResponse,
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
  listMyGradeAppeals,
  listMyGrades,
  submitGradeAppeal
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

const statusLabels: Record<GradeStatus, string> = {
  DRAFT: "Nháp",
  PROVISIONAL_PUBLISHED: "Công bố dự kiến",
  APPEAL: "Đang phúc khảo",
  OFFICIAL: "Chính thức"
};

const statusClasses: Record<GradeStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-500",
  PROVISIONAL_PUBLISHED: "bg-sky text-teal-deep",
  APPEAL: "bg-gold/10 text-gold",
  OFFICIAL: "bg-teal/10 text-teal-deep"
};

const statusTextClasses: Record<GradeStatus, string> = {
  DRAFT: "text-slate-400",
  PROVISIONAL_PUBLISHED: "text-teal-deep",
  APPEAL: "text-gold",
  OFFICIAL: "text-teal-deep"
};

type AppealTarget = { entityType: "GRADE_ENTRY" | "GRADE_PERIOD_RESULT"; entityId: number; label: string };

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
  const [appeals, setAppeals] = useState<GradeAppealResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [appealTarget, setAppealTarget] = useState<AppealTarget | null>(null);

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
                if (err instanceof ApiError && err.status === 404) return null; // chưa có/chưa công bố cho kỳ này — bỏ qua, không phải lỗi
                throw err;
              })
          )
        )
      ).then((rows) => rows.filter((r): r is { period: GradePeriodResponse; result: GradePeriodResultResponse } => r !== null)),
      listMyGradeAppeals(),
      // Đầu điểm (Listening/Reading/...) — GradeEntryResponse chỉ có gradeComponentId, phải tra tên qua đây (UC-19 group tables).
      periodsPromise.then((periods) => Promise.all(periods.map((p) => listGradeComponents(p.id))))
    ])
      .then(([entries, fetchedPeriods, results, myAppeals, componentsByPeriod]) => {
        setGrades(entries);
        setPeriods(fetchedPeriods);
        setPeriodResults(results);
        setAppeals(myAppeals);
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

  /** Bản ghi có yêu cầu phúc khảo PENDING/ACCEPTED chưa xử lý xong — ẩn nút gửi thêm (BE cũng tự chặn qua AppealAlreadyOpenException). */
  const hasOpenAppeal = (entityType: AppealTarget["entityType"], entityId: number) =>
    appeals.some((a) => a.entityType === entityType && a.entityId === entityId && a.status !== "RESOLVED");

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
                        const canAppeal = entry && entry.status === "PROVISIONAL_PUBLISHED" && !hasOpenAppeal("GRADE_ENTRY", entry.id);
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
                                {canAppeal && (
                                  <button
                                    onClick={() => setAppealTarget({ entityType: "GRADE_ENTRY", entityId: entry.id, label: `${g.label} — ${p.name}` })}
                                    className="text-[9px] font-extrabold text-coral hover:underline"
                                  >
                                    Phúc khảo
                                  </button>
                                )}
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
                    const canAppeal = result && result.status === "PROVISIONAL_PUBLISHED" && !hasOpenAppeal("GRADE_PERIOD_RESULT", result.id);
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
                            {canAppeal && (
                              <button
                                onClick={() => setAppealTarget({ entityType: "GRADE_PERIOD_RESULT", entityId: result.id, label: `Điểm tổng kết ${p.name}` })}
                                className="text-[9px] font-extrabold text-coral hover:underline"
                              >
                                Phúc khảo
                              </button>
                            )}
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
                  {g.status === "PROVISIONAL_PUBLISHED" && !hasOpenAppeal("GRADE_ENTRY", g.id) && (
                    <button
                      onClick={() => setAppealTarget({ entityType: "GRADE_ENTRY", entityId: g.id, label: componentNames.get(g.gradeComponentId) ?? "Đầu điểm" })}
                      className="text-[10px] font-extrabold text-coral hover:underline shrink-0"
                    >
                      Gửi phúc khảo
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {appeals.length > 0 && (
        <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
          <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
            <Clock className="text-teal" /> Lịch sử phúc khảo của tôi
          </h2>
          <div className="space-y-2">
            {appeals.map((a) => (
              <div key={a.id} className="border border-line/60 p-3 rounded-[14px] flex items-center justify-between gap-2 text-xs">
                <div>
                  <p className="font-bold text-ink">{a.reason || "(không ghi lý do)"}</p>
                  <p className="text-[10px] text-muted font-bold">Gửi lúc {new Date(a.createdAt).toLocaleString("vi-VN")}</p>
                </div>
                <span
                  className={`text-[10px] font-extrabold uppercase px-2 py-0.5 rounded-full ${a.status === "RESOLVED" ? "bg-teal/10 text-teal-deep" : a.status === "ACCEPTED" ? "bg-sky text-teal-deep" : "bg-gold/10 text-gold"
                    }`}
                >
                  {a.status === "PENDING" ? "Chờ giáo viên tiếp nhận" : a.status === "ACCEPTED" ? "Đang xử lý" : "Đã xử lý xong"}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      <ComingSoon
        title="Làm bài kiểm tra trực tuyến"
        description="Đang trong quá trình phát triển"
      />

      {appealTarget && (
        <SubmitAppealModal
          target={appealTarget}
          onClose={() => setAppealTarget(null)}
          onSubmitted={() => {
            setAppealTarget(null);
            load();
          }}
        />
      )}
    </div>
  );
}

function SubmitAppealModal({ target, onClose, onSubmitted }: { target: AppealTarget; onClose: () => void; onSubmitted: () => void }) {
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await submitGradeAppeal({ entityType: target.entityType, entityId: target.entityId, reason: reason.trim() || undefined });
      onSubmitted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gửi phúc khảo thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-[20px] p-6 w-full max-w-md space-y-4 shadow-xl">
        <h3 className="text-lg font-extrabold text-ink">Gửi phúc khảo — {target.label}</h3>
        <form onSubmit={handleSubmit} className="space-y-3">
          {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}
          <div>
            <label className="text-[10px] uppercase font-extrabold text-muted block mb-1">Lý do (tuỳ chọn)</label>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              placeholder="VD: Em thấy điểm chưa đúng với bài đã làm..."
              className="w-full bg-sky-2 border border-line/80 text-xs p-3 rounded-xl focus:outline-none"
            />
          </div>
          <div className="flex justify-end gap-2 pt-1">
            <button type="button" onClick={onClose} className="text-xs font-extrabold text-muted px-4 py-2 rounded-xl hover:bg-sky-2">
              Hủy
            </button>
            <button type="submit" disabled={submitting} className="text-xs font-extrabold text-white bg-teal px-4 py-2 rounded-xl disabled:opacity-50">
              {submitting ? "Đang gửi..." : "Gửi phúc khảo"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
