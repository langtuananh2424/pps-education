import React from "react";
import { GradeEntry } from "@/types";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

interface GradebookTableProps {
  entries: GradeEntry[];
  midtermInput: Record<string, string>;
  finalInput: Record<string, string>;
  errors: Record<string, string>;
  onScoreChange: (studentId: string, type: "mid" | "final", value: string) => void;
  onSubmit: (studentId: string) => void;
}

export default function GradebookTable({ entries, midtermInput, finalInput, errors, onScoreChange, onSubmit }: GradebookTableProps) {
  return (
    <TableContainer className="rounded-none border-0">
      <thead>
        <tr>
          <Th>Mã ID</Th>
          <Th>Học sinh</Th>
          <Th className="text-center">Điểm Giữa kỳ</Th>
          <Th className="text-center">Điểm Cuối kỳ</Th>
          <Th className="text-center">Điểm Trung bình</Th>
          <Th className="text-center">Hành động</Th>
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {entries.map((entry) => {
          const error = errors[entry.studentId];
          return (
            <tr key={entry.id} className="hover:bg-slate-50/40 transition-colors">
              <Td className="font-mono font-bold text-slate-500">{entry.studentId}</Td>
              <Td>
                <span className="font-bold text-slate-900 block">{entry.studentName}</span>
                <span className="text-[10px] text-slate-400 font-bold block uppercase mt-0.5">Trạng thái: {entry.status}</span>
              </Td>
              <Td className="text-center">
                <input
                  type="text"
                  placeholder={entry.midtermScore?.toString() || "Chưa nhập"}
                  value={midtermInput[entry.studentId] ?? ""}
                  onChange={(e) => onScoreChange(entry.studentId, "mid", e.target.value)}
                  disabled={entry.status === "PENDING" || entry.status === "APPROVED"}
                  className="w-16 bg-slate-50 text-center border rounded py-1 text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-brand-orange"
                />
              </Td>
              <Td className="text-center">
                <input
                  type="text"
                  placeholder={entry.finalScore?.toString() || "Chưa nhập"}
                  value={finalInput[entry.studentId] ?? ""}
                  onChange={(e) => onScoreChange(entry.studentId, "final", e.target.value)}
                  disabled={entry.status === "PENDING" || entry.status === "APPROVED"}
                  className="w-16 bg-slate-50 text-center border rounded py-1 text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-brand-orange"
                />
              </Td>
              <Td className="text-center font-mono font-bold text-slate-900 text-sm">{entry.averageScore !== undefined ? entry.averageScore.toFixed(1) : "—"}</Td>
              <Td className="text-center relative">
                {entry.status === "DRAFT" || entry.status === "REJECTED" ? (
                  <button onClick={() => onSubmit(entry.studentId)} disabled={!!error} className="px-2.5 py-1 bg-slate-900 hover:bg-slate-800 text-white font-semibold text-[10px] rounded disabled:opacity-50">
                    Gửi duyệt
                  </button>
                ) : (
                  <span className="text-[10px] text-slate-400 italic">Khóa sửa</span>
                )}
                {error && <p className="text-[9px] text-brand-red font-semibold block mt-1 absolute bg-white p-1 border rounded shadow-xs z-10">{error}</p>}
              </Td>
            </tr>
          );
        })}
      </tbody>
    </TableContainer>
  );
}
