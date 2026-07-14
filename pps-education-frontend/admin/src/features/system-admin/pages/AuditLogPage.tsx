import React from "react";
import { mockAuditLogs } from "@/data/mockData";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge from "@/components/ui/Badge";

const actionLabels: Record<string, string> = {
  ROLE_GRANTED: "Cấp vai trò",
  ROLE_REVOKED: "Thu hồi vai trò",
  PERM_OVERRIDE_ADDED: "Cấp ngoại lệ",
  PERM_OVERRIDE_REMOVED: "Hủy ngoại lệ"
};

export default function AuditLogPage() {
  return (
    <div className="space-y-4 animate-in fade-in duration-200">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-slate-800 uppercase tracking-wider block">Nhật ký kiểm toán thay đổi phân quyền (Audit Log - UC-05)</h2>
          <p className="text-[10px] text-slate-400 mt-0.5">
            Truy vết dòng thời gian hoạt động bảo mật toàn hệ thống để phát hiện rủi ro và tuân thủ an ninh.
          </p>
        </div>
        <span className="text-[10px] font-mono text-slate-500 font-bold bg-slate-100 px-3 py-1 rounded-full border whitespace-nowrap">
          Lưu trữ tối đa 1,000 sự kiện gần nhất
        </span>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        <TableContainer className="rounded-none border-0">
          <thead>
            <tr>
              <Th>Thời gian diễn ra</Th>
              <Th>Quản trị viên xử lý</Th>
              <Th>Đối tượng chịu tác động</Th>
              <Th>Tác vụ thay đổi</Th>
              <Th>Thông tin chi tiết hành động bảo mật</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {mockAuditLogs.map((log) => (
              <tr key={log.id} className="hover:bg-slate-50/50 transition-colors">
                <Td className="font-mono whitespace-nowrap">{log.createdAt}</Td>
                <Td className="font-bold text-slate-800">{log.actorName}</Td>
                <Td className="font-semibold">{log.targetName}</Td>
                <Td>
                  <Badge variant={log.action.includes("ADDED") || log.action.includes("GRANTED") ? "success" : "danger"}>
                    {actionLabels[log.action]}
                  </Badge>
                </Td>
                <Td className="text-[11px] leading-relaxed max-w-lg">{log.details}</Td>
              </tr>
            ))}
          </tbody>
        </TableContainer>
      </div>
    </div>
  );
}
