import React, { useState } from "react";
import { ApiError } from "@/lib/apiClient";
import { ClassEnrollmentResponse, GradeEntryResponse, enterGrade, submitGrades } from "../api";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";

const statusLabels: Record<GradeEntryResponse["status"], string> = { DRAFT: "Nháp", PENDING: "Chờ duyệt", APPROVED: "Đã duyệt", REJECTED: "Bị từ chối" };
const statusVariants: Record<GradeEntryResponse["status"], BadgeVariant> = { DRAFT: "neutral", PENDING: "warning", APPROVED: "success", REJECTED: "danger" };

interface GradebookTableProps {
  classId: number;
  componentId: number;
  enrollments: ClassEnrollmentResponse[];
  entries: GradeEntryResponse[];
  onChanged: () => void;
}

export default function GradebookTable({ classId, componentId, enrollments, entries, onChanged }: GradebookTableProps) {
  const [scoreInput, setScoreInput] = useState<Record<number, string>>({});
  const [noteInput, setNoteInput] = useState<Record<number, string>>({});
  const [absenceInput, setAbsenceInput] = useState<Record<number, boolean>>({});
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const activeStudents = enrollments.filter((en) => en.status === "ACTIVE");
  const entryByStudent = new Map(entries.map((e) => [e.studentId, e]));

  const handleSave = async (studentId: number) => {
    const existing = entryByStudent.get(studentId);
    const scoreStr = scoreInput[studentId] ?? (existing ? String(existing.score) : "");
    const score = parseFloat(scoreStr);
    if (scoreStr !== "" && (isNaN(score) || score < 0)) {
      setError("Điểm không hợp lệ.");
      return;
    }
    setSavingId(studentId);
    setError(null);
    try {
      await enterGrade(classId, componentId, {
        studentId,
        score: isNaN(score) ? 0 : score,
        absenceFlag: absenceInput[studentId] ?? existing?.absenceFlag ?? false,
        teacherNote: (noteInput[studentId] ?? existing?.teacherNote ?? "").trim() || undefined
      });
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu điểm thất bại.");
    } finally {
      setSavingId(null);
    }
  };

  const handleSubmitAll = async () => {
    const draftIds = entries.filter((e) => e.status === "DRAFT").map((e) => e.id);
    if (draftIds.length === 0) {
      setError("Không có điểm ở trạng thái Nháp để nộp.");
      return;
    }
    try {
      await submitGrades(classId, draftIds);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nộp điểm thất bại.");
    }
  };

  return (
    <div>
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 m-3 rounded-lg">{error}</div>}
      <TableContainer className="rounded-none border-0">
        <thead>
          <tr>
            <Th>Mã HS</Th>
            <Th>Học sinh</Th>
            <Th className="text-center">Điểm</Th>
            <Th className="text-center">Vắng</Th>
            <Th>Ghi chú</Th>
            <Th className="text-center">Trạng thái</Th>
            <Th className="text-center">Hành động</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {activeStudents.length === 0 ? (
            <tr>
              <td colSpan={7} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                Lớp chưa có học sinh nào đang ghi danh.
              </td>
            </tr>
          ) : (
            activeStudents.map((en) => {
              const existing = entryByStudent.get(en.studentId);
              const locked = existing?.status === "PENDING" || existing?.status === "APPROVED";
              return (
                <tr key={en.studentId} className="hover:bg-slate-50/40 transition-colors">
                  <Td className="font-mono font-bold text-slate-500">{en.studentCode}</Td>
                  <Td className="font-bold text-slate-900">{en.studentFullName}</Td>
                  <Td className="text-center">
                    <input
                      type="text"
                      placeholder={existing ? String(existing.score) : "—"}
                      value={scoreInput[en.studentId] ?? ""}
                      onChange={(e) => setScoreInput((prev) => ({ ...prev, [en.studentId]: e.target.value }))}
                      disabled={locked}
                      className="w-16 bg-slate-50 text-center border rounded py-1 text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-brand-orange disabled:opacity-50"
                    />
                  </Td>
                  <Td className="text-center">
                    <input
                      type="checkbox"
                      checked={absenceInput[en.studentId] ?? existing?.absenceFlag ?? false}
                      onChange={(e) => setAbsenceInput((prev) => ({ ...prev, [en.studentId]: e.target.checked }))}
                      disabled={locked}
                    />
                  </Td>
                  <Td>
                    <input
                      type="text"
                      placeholder={existing?.teacherNote ?? ""}
                      value={noteInput[en.studentId] ?? ""}
                      onChange={(e) => setNoteInput((prev) => ({ ...prev, [en.studentId]: e.target.value }))}
                      disabled={locked}
                      className="w-full bg-slate-50 border rounded py-1 px-2 text-xs focus:outline-none disabled:opacity-50"
                    />
                  </Td>
                  <Td className="text-center">{existing ? <Badge variant={statusVariants[existing.status]}>{statusLabels[existing.status]}</Badge> : <span className="text-[10px] text-slate-300 italic">Chưa nhập</span>}</Td>
                  <Td className="text-center">
                    {locked ? (
                      <span className="text-[10px] text-slate-400 italic">Khóa sửa</span>
                    ) : (
                      <button
                        onClick={() => handleSave(en.studentId)}
                        disabled={savingId === en.studentId}
                        className="px-2.5 py-1 bg-slate-900 hover:bg-slate-800 text-white font-semibold text-[10px] rounded disabled:opacity-50"
                      >
                        {savingId === en.studentId ? "Đang lưu..." : "Lưu"}
                      </button>
                    )}
                  </Td>
                </tr>
              );
            })
          )}
        </tbody>
      </TableContainer>
      {activeStudents.length > 0 && (
        <div className="px-5 py-3 border-t border-slate-100 flex justify-end">
          <Button size="sm" variant="primary" onClick={handleSubmitAll}>
            Nộp toàn bộ điểm nháp lên duyệt
          </Button>
        </div>
      )}
    </div>
  );
}
