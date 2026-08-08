import React, { useEffect, useState } from "react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import {
  ClassEnrollmentResponse,
  GradeEditWindowResponse,
  GradeEntryResponse,
  GradeEvaluationComponentResponse,
  GradeEvaluationResultResponse,
  GradeStatus,
  enterEvaluationResult,
  enterGrade,
  getGradeEditWindow,
  listEvaluationResults,
  listGradeEntries
} from "../api";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge, { BadgeVariant } from "@/components/ui/Badge";

const statusLabels: Record<GradeStatus, string> = {
  DRAFT: "Nháp",
  SUBMITTED: "Chờ duyệt",
  OFFICIAL: "Chính thức",
  REJECTED: "Bị từ chối"
};
const statusVariants: Record<GradeStatus, BadgeVariant> = {
  DRAFT: "neutral",
  SUBMITTED: "warning",
  OFFICIAL: "success",
  REJECTED: "danger"
};
/** Thứ tự "cần chú ý nhất trước" khi 1 dòng có nhiều bản ghi (nhiều đầu điểm + Overall) ở trạng thái khác nhau. */
const statusPriority: GradeStatus[] = ["DRAFT", "REJECTED", "SUBMITTED", "OFFICIAL"];

const sourceLabels: Record<GradeEvaluationResultResponse["source"], string> = { MANUAL: "NHẬP TAY", EXCEL_IMPORT: "EXCEL" };

/** V97: setup đã có 1 thang điểm cố định (POINT_10/PERCENT/IELTS) — bỏ cột "Thang" cho GV chọn tay theo dòng, tự suy ra scaleType lưu vào GradeEvaluationResult theo đúng thang của setup. */
const resultScaleTypeBySetupScale: Record<"POINT_10" | "PERCENT" | "IELTS", GradeEvaluationResultResponse["scaleType"]> = {
  POINT_10: "NUMERIC",
  PERCENT: "PERCENTAGE",
  IELTS: "BAND"
};

interface GradeSheetTableProps {
  classId: number;
  setupId: number;
  /** Thang điểm của setup (V97) — dùng để tự suy ra scaleType lưu vào Overall, không cho chọn tay theo dòng nữa. */
  scaleType: "POINT_10" | "PERCENT" | "IELTS";
  components: GradeEvaluationComponentResponse[];
  enrollments: ClassEnrollmentResponse[];
  /** Cho khối Công bố điểm (UC-20) đọc lại danh sách entries/results vừa tải, khỏi phải fetch lần 2. */
  onLoaded?: (entries: GradeEntryResponse[], results: GradeEvaluationResultResponse[]) => void;
  /** Quản lý điểm trường xem lại điểm (kể cả đã công bố) — chỉ hiển thị, không cho sửa (họ không có quyền nhập điểm). */
  readOnly?: boolean;
}

/**
 * UC-19/UC-53 (V44 — 4 trạng thái, thay hẳn luồng "công bố dự kiến + phúc khảo" V43):
 * sổ điểm đầy đủ theo lớp + setup sổ điểm, mỗi thành phần điểm là 1 cột, cộng
 * Overall/Thang/Level/Nhận xét/Ghi chú (UC-53, V94). Sửa/xoá được khi DRAFT hoặc
 * REJECTED (không giới hạn thời gian) — SUBMITTED/OFFICIAL bị chặn với actor thường,
 * trừ actor có quyền academic.grade.edit.override. Khoá ô nhập (read-only) ngay ở FE
 * cho SUBMITTED/OFFICIAL khi không có quyền override.
 */
export default function GradeSheetTable({ classId, setupId, scaleType, components, enrollments, onLoaded, readOnly = false }: GradeSheetTableProps) {
  const { hasPermission } = useApp();
  const canOverride = hasPermission("academic.grade.edit.override");
  const [entriesByStudent, setEntriesByStudent] = useState<Map<number, Map<number, GradeEntryResponse>>>(new Map());
  const [resultsByStudent, setResultsByStudent] = useState<Map<number, GradeEvaluationResultResponse>>(new Map());
  const [editWindow, setEditWindow] = useState<GradeEditWindowResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [scoreInput, setScoreInput] = useState<Record<string, string>>({});
  const [overallInput, setOverallInput] = useState<Record<number, string>>({});
  const [levelInput, setLevelInput] = useState<Record<number, string>>({});
  const [commentInput, setCommentInput] = useState<Record<number, string>>({});
  const [noteInput, setNoteInput] = useState<Record<number, string>>({});
  const [disclaimerInput, setDisclaimerInput] = useState<string>("");
  const [savingKey, setSavingKey] = useState<string | null>(null);

  const activeStudents = enrollments.filter((en) => en.status === "ACTIVE");
  const componentIdsKey = components.map((c) => c.id).join(",");

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([Promise.all(components.map((c) => listGradeEntries(classId, c.id))), listEvaluationResults(classId, setupId)])
      .then(([entriesByComponent, results]) => {
        const flatEntries = entriesByComponent.flat();
        const map = new Map<number, Map<number, GradeEntryResponse>>();
        flatEntries.forEach((entry) => {
          if (!map.has(entry.studentId)) map.set(entry.studentId, new Map());
          map.get(entry.studentId)!.set(entry.gradeEvaluationComponentId, entry);
        });
        setEntriesByStudent(map);
        setResultsByStudent(new Map(results.map((r) => [r.studentId, r])));
        if (results.length > 0 && results[0].disclaimer) {
          setDisclaimerInput(results[0].disclaimer);
        }
        onLoaded?.(flatEntries, results);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được bảng điểm."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [classId, setupId, componentIdsKey]);
  useEffect(() => {
    getGradeEditWindow().then(setEditWindow).catch(() => undefined);
  }, []);

  const rowStatus = (studentId: number): GradeStatus | null => {
    const entryStatuses = [...(entriesByStudent.get(studentId)?.values() ?? [])].map((e) => e.status);
    const result = resultsByStudent.get(studentId);
    const statuses = result ? [...entryStatuses, result.status] : entryStatuses;
    if (statuses.length === 0) return null;
    return statusPriority.find((s) => statuses.includes(s)) ?? statuses[0];
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
    if (
      overallInput[studentId] === undefined &&
      levelInput[studentId] === undefined &&
      commentInput[studentId] === undefined &&
      noteInput[studentId] === undefined
    )
      return;
    const overallRaw = overallInput[studentId] ?? (existing ? String(existing.overallScore ?? "") : "");
    const scale = resultScaleTypeBySetupScale[scaleType];
    const level = levelInput[studentId] ?? existing?.level ?? "";
    const comment = commentInput[studentId] ?? existing?.comment ?? "";
    const note = noteInput[studentId] ?? existing?.note ?? "";
    const disclaimer = disclaimerInput || existing?.disclaimer || "";
    const overallScore = overallRaw.trim() === "" ? undefined : parseFloat(overallRaw);
    if (overallRaw.trim() !== "" && isNaN(Number(overallScore))) {
      setError("Overall không hợp lệ.");
      return;
    }
    setSavingKey(key);
    setError(null);
    try {
      const updated = await enterEvaluationResult(classId, studentId, setupId, {
        overallScore,
        scaleType: scale,
        level: level.trim() || undefined,
        comment: comment.trim() || undefined,
        note: note.trim() || undefined,
        disclaimer: disclaimer.trim() || undefined
      });
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
      setLevelInput((prev) => {
        const next = { ...prev };
        delete next[studentId];
        return next;
      });
      setCommentInput((prev) => {
        const next = { ...prev };
        delete next[studentId];
        return next;
      });
      setNoteInput((prev) => {
        const next = { ...prev };
        delete next[studentId];
        return next;
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu Overall/Level/Nhận xét/Lưu ý thất bại.");
    } finally {
      setSavingKey(null);
    }
  };

  const handleBlurDisclaimer = async () => {
    if (!disclaimerInput && !resultsByStudent.values().next().value?.disclaimer) return;
    const key = "disclaimer:all";
    setSavingKey(key);
    setError(null);
    try {
      const results = Array.from(resultsByStudent.values());
      await Promise.all(
        results.map((result) =>
          enterEvaluationResult(classId, result.studentId, setupId, {
            overallScore: result.overallScore ?? undefined,
            scaleType: result.scaleType,
            level: result.level ?? undefined,
            comment: result.comment ?? undefined,
            note: result.note ?? undefined,
            disclaimer: disclaimerInput.trim() || undefined
          })
        )
      );
      setResultsByStudent((prev) => {
        const next = new Map(prev);
        next.forEach((result) => {
          next.set(result.studentId, { ...result, disclaimer: disclaimerInput.trim() || null });
        });
        return next;
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu Lưu ý thất bại.");
    } finally {
      setSavingKey(null);
    }
  };

  if (loading) return <p className="text-xs text-slate-400 italic p-6 text-center">Đang tải bảng điểm...</p>;

  return (
    <div>
      {editWindow && (
        <div className="px-5 pt-3 space-y-1">
          <p className="text-[11px] text-slate-400 italic">
            Điểm còn Nháp sửa/xoá tự do, không giới hạn thời gian. Khi sẵn sàng, gửi duyệt để Quản lý điểm trường xét duyệt — chỉ khi Duyệt điểm
            mới hiển thị cho Phụ huynh/Học sinh. Nếu bị Từ chối, sửa lại rồi gửi duyệt lại.
          </p>
        </div>
      )}
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 m-3 rounded-lg">{error}</div>}
      {!readOnly && (
        <div className="px-5 py-3 space-y-2 border-b border-slate-200">
          <label className="block text-xs font-semibold text-slate-700">Lưu ý</label>
          <textarea
            value={disclaimerInput}
            onChange={(e) => setDisclaimerInput(e.target.value)}
            onBlur={handleBlurDisclaimer}
            disabled={savingKey === "disclaimer:all"}
            placeholder="Nhập lưu ý cho kỳ đánh giá này (VD: phạm vi đề thi, điều kiện điều chỉnh, ...)"
            className="w-full text-xs p-2 border border-slate-200 rounded-lg bg-white focus:outline-none focus:ring-1 focus:ring-blue-400 disabled:opacity-50"
            rows={3}
          />
        </div>
      )}
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
              <Th className="text-center">Level</Th>
              <Th className="text-center whitespace-nowrap">Nhận xét</Th>
              <Th className="text-center whitespace-nowrap">Ghi chú</Th>
              <Th className="text-center">Nguồn</Th>
              <Th className="text-center">Trạng thái</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {activeStudents.length === 0 ? (
              <tr>
                <td colSpan={components.length + 7} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                  Lớp chưa có học sinh nào đang ghi danh.
                </td>
              </tr>
            ) : (
              activeStudents.map((en) => {
                const result = resultsByStudent.get(en.studentId);
                const status = rowStatus(en.studentId);
                const resultLocked = !canOverride && (result?.status === "SUBMITTED" || result?.status === "OFFICIAL");
                return (
                  <tr key={en.studentId} className="hover:bg-slate-50/40 transition-colors">
                    <Td className="font-mono font-bold text-slate-500 whitespace-nowrap">{en.studentCode}</Td>
                    <Td className="font-bold text-slate-900 whitespace-nowrap">{en.studentFullName}</Td>
                    {components.map((c) => {
                      const existing = entriesByStudent.get(en.studentId)?.get(c.id);
                      const key = `${en.studentId}:${c.id}`;
                      const rejected = existing?.status === "REJECTED";
                      const locked = !canOverride && (existing?.status === "SUBMITTED" || existing?.status === "OFFICIAL");
                      return (
                        <Td key={c.id} className="text-center">
                          {readOnly || locked ? (
                            <span className="text-xs font-semibold text-slate-700" title={locked ? "Đang chờ duyệt/đã chính thức — không sửa được nữa." : undefined}>
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
                                title={rejected ? "Bị từ chối — sửa lại rồi gửi duyệt lại." : undefined}
                                className={`w-16 bg-slate-50 text-center border rounded py-1 text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-brand-orange disabled:opacity-50 ${
                                  rejected ? "border-rose-400 ring-1 ring-rose-300" : ""
                                }`}
                              />
                              {rejected && <span className="text-[9px] font-bold text-rose-600 uppercase">Bị từ chối</span>}
                            </div>
                          )}
                        </Td>
                      );
                    })}
                    <Td className="text-center">
                      {readOnly || resultLocked ? (
                        <span className="text-xs font-semibold text-slate-700" title={resultLocked ? "Đang chờ duyệt/đã chính thức — không sửa được nữa." : undefined}>
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
                            title={result?.status === "REJECTED" ? "Bị từ chối — sửa lại rồi gửi duyệt lại." : undefined}
                            className={`w-16 bg-slate-50 text-center border rounded py-1 text-xs font-semibold focus:outline-none ${
                              result?.status === "REJECTED" ? "border-rose-400 ring-1 ring-rose-300" : ""
                            }`}
                          />
                          {result?.status === "REJECTED" && <span className="text-[9px] font-bold text-rose-600 uppercase">Bị từ chối</span>}
                        </div>
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
                      {readOnly || resultLocked ? (
                        <span className="text-xs text-slate-700 whitespace-pre-wrap">{result?.comment ?? "—"}</span>
                      ) : (
                        <input
                          type="text"
                          placeholder={result?.comment ?? "Nhận xét..."}
                          value={commentInput[en.studentId] ?? ""}
                          onChange={(e) => setCommentInput((prev) => ({ ...prev, [en.studentId]: e.target.value }))}
                          onBlur={() => handleBlurResult(en.studentId)}
                          className="w-32 bg-slate-50 border rounded py-1 px-1.5 text-xs focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="text-center">
                      {readOnly || resultLocked ? (
                        <span className="text-xs text-slate-700 whitespace-pre-wrap">{result?.note ?? "—"}</span>
                      ) : (
                        <input
                          type="text"
                          placeholder={result?.note ?? "Ghi chú..."}
                          value={noteInput[en.studentId] ?? ""}
                          onChange={(e) => setNoteInput((prev) => ({ ...prev, [en.studentId]: e.target.value }))}
                          onBlur={() => handleBlurResult(en.studentId)}
                          className="w-28 bg-slate-50 border rounded py-1 px-1.5 text-xs focus:outline-none"
                        />
                      )}
                    </Td>
                    <Td className="text-center">
                      {result ? <Badge variant="info">{sourceLabels[result.source]}</Badge> : <span className="text-[10px] text-slate-300 italic">—</span>}
                    </Td>
                    <Td className="text-center">
                      {status ? (
                        <Badge variant={statusVariants[status]}>{statusLabels[status]}</Badge>
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
