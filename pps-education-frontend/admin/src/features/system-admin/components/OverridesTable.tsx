import React from "react";
import { Calendar, X } from "lucide-react";
import { Employee, Permission, UserOverride } from "@/types";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge from "@/components/ui/Badge";

interface OverridesTableProps {
  overrides: UserOverride[];
  employees: Employee[];
  permissions: Permission[];
  onRemove: (index: number) => void;
}

export default function OverridesTable({ overrides, employees, permissions, onRemove }: OverridesTableProps) {
  return (
    <div className="lg:col-span-3 bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm h-fit">
      <div className="p-4 border-b bg-slate-50/20">
        <span className="text-xs font-bold text-slate-800 uppercase tracking-wider block">Bản đồ điều chỉnh ngoại lệ hiện hành ({overrides.length})</span>
      </div>
      <TableContainer className="rounded-none border-0">
        <thead>
          <tr>
            <Th>Tài khoản nhân sự</Th>
            <Th>Quyền hạt nhân</Th>
            <Th>Kiểu điều phối</Th>
            <Th>Lý do điều phối</Th>
            <Th>Thời gian hết hạn</Th>
            <Th className="text-center">Gỡ bỏ</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {overrides.map((ov, index) => {
            const emp = employees.find((e) => e.id === ov.userId);
            const permObj = permissions.find((p) => p.id === ov.permissionId);

            return (
              <tr key={index} className="hover:bg-slate-50/50 transition-colors">
                <Td>
                  <span className="font-bold text-slate-900 block tracking-tight">{emp?.fullName || ov.userId}</span>
                  <span className="text-[9px] font-mono font-bold text-slate-400 block mt-0.5">{emp?.id}</span>
                </Td>
                <Td className="font-mono font-bold text-brand-red">{permObj?.code || ov.permissionId}</Td>
                <Td>
                  <Badge variant={ov.type === "GRANT" ? "success" : "danger"}>{ov.type === "GRANT" ? "Cấp thêm" : "Tước bỏ"}</Badge>
                </Td>
                <Td className="text-slate-500 italic max-w-xs truncate" title={ov.reason}>
                  {ov.reason}
                </Td>
                <Td className="font-mono text-[10px] text-slate-500">
                  {ov.expiresAt ? (
                    <div className="flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                      <span>{ov.expiresAt}</span>
                    </div>
                  ) : (
                    "Vô thời hạn"
                  )}
                </Td>
                <Td className="text-center">
                  <button onClick={() => onRemove(index)} className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors" title="Hủy ngoại lệ này">
                    <X className="w-4 h-4" />
                  </button>
                </Td>
              </tr>
            );
          })}
        </tbody>
      </TableContainer>
    </div>
  );
}
