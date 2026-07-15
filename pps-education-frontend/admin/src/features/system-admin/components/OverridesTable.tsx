import React, { useState } from "react";
import { Calendar, X } from "lucide-react";
import { UserPermissionOverrideSummary } from "../api";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge from "@/components/ui/Badge";

interface OverridesTableProps {
  overrides: UserPermissionOverrideSummary[];
  onRemove: (permissionId: number) => Promise<void>;
}

export default function OverridesTable({ overrides, onRemove }: OverridesTableProps) {
  const [removingId, setRemovingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleRemove = async (permissionId: number) => {
    setRemovingId(permissionId);
    setError(null);
    try {
      await onRemove(permissionId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Gỡ ngoại lệ thất bại.");
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <div className="lg:col-span-3 bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm h-fit">
      <div className="p-4 border-b bg-slate-50/20">
        <span className="text-xs font-bold text-slate-800 uppercase tracking-wider block">Ngoại lệ đang áp dụng cho tài khoản ({overrides.length})</span>
      </div>
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border-b border-rose-100 p-2.5">{error}</div>}
      {overrides.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-6 text-center">Tài khoản này chưa có ngoại lệ quyền nào.</p>
      ) : (
        <TableContainer className="rounded-none border-0">
          <thead>
            <tr>
              <Th>Quyền hạt nhân</Th>
              <Th>Kiểu điều phối</Th>
              <Th>Lý do điều phối</Th>
              <Th>Thời gian hết hạn</Th>
              <Th className="text-center">Gỡ bỏ</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {overrides.map((ov) => (
              <tr key={ov.permissionId} className="hover:bg-slate-50/50 transition-colors">
                <Td className="font-mono font-bold text-brand-red">{ov.permissionCode}</Td>
                <Td>
                  <Badge variant={ov.overrideType === "GRANT" ? "success" : "danger"}>{ov.overrideType === "GRANT" ? "Cấp thêm" : "Tước bỏ"}</Badge>
                </Td>
                <Td className="text-slate-500 italic max-w-xs truncate" title={ov.reason}>
                  {ov.reason}
                </Td>
                <Td className="font-mono text-[10px] text-slate-500">
                  {ov.expiresAt ? (
                    <div className="flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                      <span>{new Date(ov.expiresAt).toLocaleDateString("vi-VN")}</span>
                    </div>
                  ) : (
                    "Vô thời hạn"
                  )}
                </Td>
                <Td className="text-center">
                  <button
                    onClick={() => handleRemove(ov.permissionId)}
                    disabled={removingId === ov.permissionId}
                    className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors disabled:opacity-50"
                    title="Hủy ngoại lệ này"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </Td>
              </tr>
            ))}
          </tbody>
        </TableContainer>
      )}
    </div>
  );
}
