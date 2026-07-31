import React, { useEffect, useState } from "react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import {
  ClassEnrollmentResponse,
  EnterGradePeriodResultRequest,
  GradeAppealWindowResponse,
  GradeComponentResponse,
  GradeEditWindowResponse,
  GradeEntryResponse,
  GradePeriodResultResponse,
  GradeStatus,
  enterGrade,
  enterPeriodResult,
  getGradeAppealWindow,
  getGradeEditWindow,
  listGradeEntries,
  listPeriodResults
} from "../api";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Select from "@/components/ui/Select";

const statusLabels: Record<GradeStatus, string> = {
  DRAFT: "Nháp",
  PROVISIONAL_PUBLISHED: "Công bố dự kiến",
  APPEAL: "Đang phúc khảo",
  OFFICIAL: "Chính thức"
};
const statusVariants: Record<GradeStatus, BadgeVariant> = {
  DRAFT: "neutral",
  PROVISIONAL_PUBLISHED: "success",
  APPEAL: "warning",
  OFFICIAL: "info"
};
/** Thứ tự "cần chú ý nhất trước" khi 1 dòng có nhiều bản ghi (nhiều đầu điểm + Overall) ở trạng thái khác nhau. */
const statusPriority: GradeStatus[] = ["DRAFT", "APPEAL", "PROVISIONAL_PUBLISHED", "OFFICIAL"];

const scaleLabels: Record<GradePeriodResultResponse["scaleType"], string> = {
  NUMERIC: "Số (0–10)",
  PERCENTAGE: "Phần trăm (%)",
  BAND: "Thang chữ (Band)"
};
const sourceLabels: Record<GradePeriodResultResponse["source"], string> = { MANUAL: "NHẬP TAY", EXCEL_IMPORT: "EXCEL" };

interface GradeSheetTableProps {
  classId: number;
  gradePeriodId: number;
  components: GradeComponentResponse[];
  enrollments: ClassEnrollmentResponse[];
  /** Cho khối Công bố điểm (UC-20) đọc lại danh sách entries/results vừa tải, khỏi phải fetch lần 2. */
  onLoaded?: (entries: GradeEntryResponse[], results: GradePeriodResultResponse[]) => void;
  /** Quản lý điểm trường xem lại điểm (kể cả đã công bố) — chỉ hiển thị, không cho sửa (họ không có quyền nhập điểm). */
  readOnly?: boolean;
}

/**
 * UC-19/UC-53 (V43 — 4 trạng thái + phúc khảo UC-62): sổ điểm đầy đủ theo lớp + kỳ đánh
 * giá, mỗi thành phần điểm là 1 cột, cộng Overall/Thang/Level (UC-53). Sửa/xoá được khi
 * DRAFT (không giới hạn thời gian) hoặc APPEAL (đã tiếp nhận đúng yêu cầu phúc khảo) —
 * PROVISIONAL_PUBLISHED/OFFICIAL bị chặn với actor thường, trừ actor có quyền
 * academic.grade.edit.override. Khoá ô nhập (read-only) ngay ở FE cho PROVISIONAL_
 * PUBLISHED/OFFICIAL khi không có quyền override — trước đây luôn để input hiện (chờ
 * backend từ chối) gây hiểu nhầm bấm sửa được, đã xác nhận với người dùng 2026-07-29 nên
 * đổi sang khoá rõ ràng, khớp đúng luồng "sent-row lockout" đã áp dụng ở DailyCommentPanel.
 * Riêng APPEAL vẫn để input hiện (backend mới biết chính xác actor có phải người đã tiếp
 * nhận yêu cầu phúc khảo hay không).
 */
export default function GradeSheetTable({ classId, gradePeriodId, components, enrollments, onLoaded, readOnly = false }: GradeSheetTableProps) {
  const { hasPermission } = useApp();
  const canOverride = hasPermission("academic.grade.edit.override");
  const [entriesByStudent, setEntriesByStudent] = useState<Map<number, Map<number, GradeEntryResponse>>>(new Map());
  const [resultsByStudent, setResultsByStudent] = useState<Map<number, GradePeriodResultResponse>>(new Map());
  const [editWindow, setEditWindow] = useState<GradeEditWindowResponse | null>(null);
  const [appealWindow, setAppealWindow] = useState<GradeAppealWindowResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [scoreInput, setScoreInput] = useState<Record<string, string>>({});
  const [overallInput, setOverallInput] = useState<Record<number, string>>({});
  const [scaleInput, setScaleInput] = useState<Record<number, string>>({});
  const [levelInput, setLevelInput] = useState<Record<number, string>>({});
  const [savingKey, setSavingKey] = useState<string | null>(null);

  const activeStudents = enrollments.filter((en) => en.status === "ACTIVE");
  const componentIdsKey = components.map((c) => c.id).join(",");

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([Promise.all(components.map((c) => listGradeEntries(classId, c.id))), listPeriodResults(classId, gradePeriodId)])
      .then(([entriesByComponent, results]) => {
        const flatEntries = entriesByComponent.flat();
        const map = new Map<number, Map<number, GradeEntryResponse>>();
        flatEntries.forEach((entry) => {
          if (!map.has(entry.studentId)) map.set(entry.studentId, new Map());
          map.get(entry.studentId)!.set(entry.gradeComponentId, entry);
        });
        setEntriesByStudent(map);
        setResultsByStudent(new Map(results.map((r) => [r.studentId, r])));
        onLoaded?.(flatEntries, results);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được bảng điểm."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [classId, gradePeriodId, componentIdsKey]);
  useEffect(() => {
    getGradeEditWindow().then(setEditWindow).catch(() => undefined);
    getGradeAppealWindow().then(setAppealWindow).catch(() => undefined);
  }, []);

  const rowStatus = (studentId: number): GradeStatus | null => {
    const entryStatuses = [...(entriesByStudent.get(studentId)?.values() ?? [])].map((e) => e.status);
    const result = resultsByStudent.get(studentId);
    const statuses = result ? [...entryStatuses, result.status] : entryStatuses;
    if (statuses.length === 0) return null;
    return statusPriority.find((s) => statuses.includes(s)) ?? statuses[0];
  };

  /**
   * Hạn tự động khoá "Chính thức" (UC-62) = publishedAt SỚM NHẤT trong các bản ghi đang
   * PROVISIONAL_PUBLISHED/APPEAL của học sinh này, cộng số ngày phúc khảo cấu hình được
   * (grade-appeal-window-days) — GradeSchedulerService tự khoá bất kể còn ở trạng thái nào
   * trong 2 trạng thái đó khi hết hạn.
   */
  const officialLockInfo = (studentId: number): { deadline: Date; daysLeft: number } | null => {
    if (!appealWindow) return null;
    const records: { status: GradeStatus; publishedAt: string | null }[] = [
      ...(entriesByStudent.get(studentId)?.values() ?? []),
      ...(resultsByStudent.get(studentId) ? [resultsByStudent.get(studentId)!] : [])
    ];
    const publishedTimestamps = records
      .filter((r) => (r.status === "PROVISIONAL_PUBLISHED" || r.status === "APPEAL") && r.publishedAt)
      .map((r) => new Date(r.publishedAt!).getTime());
    if (publishedTimestamps.length === 0) return null;
    const earliest = Math.min(...publishedTimestamps);
    const deadline = new Date(earliest + appealWindow.days * 24 * 60 * 60 * 1000);
    const daysLeft = Math.ceil((deadline.getTime() - Date.now()) / (24 * 60 * 60 * 1000));
    return { deadline, daysLeft };
  };

  const handleBlurScore = async (studentId: number, componentId: number) => {
    const key = `${studentId}:${componentId}`;
    const raw = scoreInput[key];
    if (raw === undefined) return;
    const existing = entriesByStudent.get(studentId)?.get(componentId);
    const score = parseFloat(raw);
    if (raw.trim() !== "" && (isNaN(score) || score < 0)) {
      setError("Điểm không hợp lệ.");
      return;
    }
    setSavingKey(key);
    setError(null);
    try {
      const updated = await enterGrade(classId, componentId, {
        studentId,
        score: isNaN(score) ? 0 : score,
        absenceFlag: existing?.absenceFlag ?? false,
        teacherNote: existing?.teacherNote ?? undefined
      });
      // Cập nhật thẳng từ response thay vì gọi load() — load() set loading=true khiến cả bảng bị
      // unmount rồi mount lại (cảm giác "reload" mỗi lần rời khỏi 1 ô điểm), không cần thiết vì
      // response đã có đủ dữ liệu mới nhất của đúng 1 ô vừa lưu.
      setEntriesByStudent((prev) => {
        const next = new Map(prev);
        const studentMap = new Map(next.get(studentId) ?? []);
        studentMap.set(componentId, updated);
        next.set(studentId, studentMap);
        return next;
      });
      setScoreInput((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu điểm thất bại.");
    } finally {
      setSavingKey(null);
    }
  };

  const handleBlurResult = async (studentId: number) => {
    const key = `overall:${studentId}`;
    const existing = resultsByStudent.get(studentId);
    if (overallInput[studentId] === undefined && scaleInput[studentId] === undefined && levelInput[studentId] === undefined) return;
    const overallRaw = overallInput[studentId] ?? (existing ? String(existing.overallScore ?? "") : "");
    const scale = (scaleInput[studentId] ?? existing?.scaleType ?? "NUMERIC") as EnterGradePeriodResultRequest["scaleType"];
    const level = levelInput[studentId] ?? existing?.level ?? "";
    const overallScore = overallRaw.trim() === "" ? undefined : parseFloat(overallRaw);
    if (overallRaw.trim() !== "" && isNaN(Number(overallScore))) {
      setError("Overall không hợp lệ.");
      return;
    }
    setSavingKey(key);
    setError(null);
    try {
      const updated = await enterPeriodResult(classId, studentId, gradePeriodId, { overallScore, scaleType: scale, level: level.trim() || undefined });
      setResultsByStudent((prev) => {
        const next = new Map(prev);
        next.set(studentId, updated);
        return next;
      });
      setOverallInput((prev) => {
        const next = { ...prev };
        delete next[studentId];
        return next;
      });
      setScaleInput((prev) => {
        const next = { ...prev };
        delete next[studentId];
        return next;
      });
      setLevelInput((prev) => {
        const next = { ...prev };
        delete next[studentId];
        return next;
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu Overall/Level thất bại.");
    } finally {
      setSavingKey(null);
    }
  };

  if (loading) return <p className="text-xs text-slate-400 italic p-6 text-center">Đang tải bảng điểm...</p>;

  return (
    <div>
      {(editWindow || appealWindow) && (
        <div className="px-5 pt-3 space-y-1">
          {editWindow && (
            <p className="text-[11px] text-slate-400 italic">
              Sau {editWindow.days} ngày kể từ lần đầu nhập điểm cho lớp + kỳ này, điểm còn Nháp sẽ tự động chuyển "Công bố dự kiến" nếu không ai
              công bố tay. Sửa/xoá điểm chỉ còn giới hạn theo trạng thái (Nháp hoặc đang Phúc khảo đã tiếp nhận) — không còn theo thời gian.
            </p>
          )}
          {appealWindow && (
            <p className="text-[11px] text-slate-400 italic">
              Sau {appealWindow.days} ngày kể từ lúc "Công bố dự kiến", điểm tự động khoá "Chính thức" — không sửa được nữa (trừ tài khoản có
              quyền toàn quyền sửa điểm).
            </p>
          )}
        </div>
      )}
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 m-3 rounded-lg">{error}</div>}
      <div className="overflow-x-auto">
        <TableContainer className="rounded-none border-0">
          <thead>
            <tr>
              <Th>Mã HS</Th>
              <Th>Học sinh</Th>
              {components.map((c) => (
                <Th key={c.id} className="text-center whitespace-nowrap">
                  {c.name.toUpperCase()}
                </Th>
              ))}
              <Th className="text-center">Overall</Th>
              <Th className="text-center">Thang</Th>
              <Th className="text-center">Level</Th>
              <Th className="text-center">Nguồn</Th>
              <Th className="text-center">Trạng thái</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {activeStudents.length === 0 ? (
              <tr>
                <td colSpan={components.length + 6} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  Lớp chưa có học sinh nào đang ghi danh.
                </td>
              </tr>
            ) : (
              activeStudents.map((en) => {
                const result = resultsByStudent.get(en.studentId);
                const status = rowStatus(en.studentId);
                const resultLocked = !canOverride && (result?.status === "PROVISIONAL_PUBLISHED" || result?.status === "OFFICIAL");
                return (
                  <tr key={en.studentId} className="hover:bg-slate-50/40 transition-colors">
                    <Td className="font-mono font-bold text-slate-500 whitespace-nowrap">{en.studentCode}</Td>
                    <Td className="font-bold text-slate-900 whitespace-nowrap">{en.studentFullName}</Td>
                    {components.map((c) => {
                      const existing = entriesByStudent.get(en.studentId)?.get(c.id);
                      const key = `${en.studentId}:${c.id}`;
                      const underAppeal = existing?.status === "APPEAL";
                      const locked = !canOverride && (existing?.status === "PROVISIONAL_PUBLISHED" || existing?.status === "OFFICIAL");
                      return (
                        <Td key={c.id} className="text-center">
                          {readOnly || locked ? (
                            <span className="text-xs font-semibold text-slate-700" title={locked ? "Đã công bố/chính thức — không sửa được nữa." : undefined}>
                              {existing ? existing.score : "—"}
                            </span>
                          ) : (
                            <div className="inline-flex flex-col items-center gap-0.5">
                              <input
                                type="text"
                                placeholder={existing ? String(existing.score) : "—"}
                                value={scoreInput[key] ?? ""}
                                onChange={(e) => setScoreInput((prev) => ({ ...prev, [key]: e.target.value }))}
                                onBlur={() => handleBlurScore(en.studentId, c.id)}
                                disabled={savingKey === key}
                                title={underAppeal ? "Đang phúc khảo — nhập lại điểm (kể cả giữ nguyên giá trị) rồi rời ô để xử lý xong." : undefined}
                                className={`w-16 bg-slate-50 text-center border rounded py-1 text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-brand-orange disabled:opacity-50 ${
                                  underAppeal ? "border-amber-400 ring-1 ring-amber-300" : ""
                                }`}
                              />
                              {underAppeal && <span className="text-[9px] font-bold text-amber-600 uppercase">Phúc khảo</span>}
                            </div>
                          )}
                        </Td>
                      );
                    })}
                    <Td className="text-center">
                      {readOnly || resultLocked ? (
                        <span className="text-xs font-semibold text-slate-700" title={resultLocked ? "Đã công bố/chính thức — không sửa được nữa." : undefined}>
                          {result?.overallScore ?? "—"}
                        </span>
                      ) : (
                        <div className="inline-flex flex-col items-center gap-0.5">
                          <input
                            type="text"
                            placeholder={result?.overallScore != null ? String(result.overallScore) : "—"}
                            value={overallInput[en.studentId] ?? ""}
                            onChange={(e) => setOverallInput((prev) => ({ ...prev, [en.studentId]: e.target.value }))}
                            onBlur={() => handleBlurResult(en.studentId)}
                            title={result?.status === "APPEAL" ? "Đang phúc khảo — nhập lại điểm (kể cả giữ nguyên giá trị) rồi rời ô để xử lý xong." : undefined}
                            className={`w-16 bg-slate-50 text-center border rounded py-1 text-xs font-semibold focus:outline-none ${
                              result?.status === "APPEAL" ? "border-amber-400 ring-1 ring-amber-300" : ""
                            }`}
                          />
                          {result?.status === "APPEAL" && <span className="text-[9px] font-bold text-amber-600 uppercase">Phúc khảo</span>}
                        </div>
                      )}
                    </Td>
                    <Td className="text-center">
                      {readOnly || resultLocked ? (
                        <span className="text-[11px] font-semibold text-slate-700">{scaleLabels[result?.scaleType ?? "NUMERIC"]}</span>
                      ) : (
                        <Select
                          value={scaleInput[en.studentId] ?? result?.scaleType ?? "NUMERIC"}
                          onChange={(e) => setScaleInput((prev) => ({ ...prev, [en.studentId]: e.target.value }))}
                          onBlur={() => handleBlurResult(en.studentId)}
                          className="bg-slate-50 border rounded py-1 px-1.5 text-[11px] font-semibold focus:outline-none"
                        >
                          {Object.entries(scaleLabels).map(([value, label]) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </Select>
                      )}
                    </Td>
                    <Td className="text-center">
                      {readOnly || resultLocked ? (
                        <span className="text-xs font-semibold text-slate-700">{result?.level ?? "—"}</span>
                      ) : (
                        <input
                          type="text"
                          placeholder={result?.level ?? "—"}
                          value={levelInput[en.studentId] ?? ""}
                          onChange={(e) => setLevelInput((prev) => ({ ...prev, [en.studentId]: e.target.value }))}
                          onBlur={() => handleBlurResult(en.studentId)}
                          className="w-20 bg-slate-50 text-center border rounded py-1 text-xs font-semibold focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="text-center">
                      {result ? <Badge variant="info">{sourceLabels[result.source]}</Badge> : <span className="text-[10px] text-slate-300 italic">—</span>}
                    </Td>
                    <Td className="text-center">
                      {status ? (
                        <div className="inline-flex flex-col items-center gap-0.5">
                          <Badge variant={statusVariants[status]}>{statusLabels[status]}</Badge>
                          {(status === "PROVISIONAL_PUBLISHED" || status === "APPEAL") &&
                            (() => {
                              const lockInfo = officialLockInfo(en.studentId);
                              if (!lockInfo) return null;
                              const { deadline, daysLeft } = lockInfo;
                              return (
                                <span className={`text-[9px] font-bold whitespace-nowrap ${daysLeft <= 1 ? "text-rose-600" : "text-slate-400"}`}>
                                  {daysLeft > 0
                                    ? `Khoá Chính thức sau ${daysLeft} ngày (${deadline.toLocaleDateString("vi-VN")})`
                                    : "Sắp tự khoá Chính thức"}
                                </span>
                              );
                            })()}
                        </div>
                      ) : (
                        <span className="text-[10px] text-slate-300 italic">Chưa nhập</span>
                      )}
                    </Td>
                  </tr>
                );
              })
            )}
          </tbody>
        </TableContainer>
      </div>
    </div>
  );
}
