import React from "react";
import { Clock } from "lucide-react";
import { PermissionAuditLog } from "@/types";

interface RoleAuditTimelineProps {
  roleName?: string;
  logs: PermissionAuditLog[];
}

export default function RoleAuditTimeline({ roleName, logs }: RoleAuditTimelineProps) {
  return (
    <div className="space-y-4">
      <div>
        <h4 className="text-xs font-bold text-slate-800 uppercase tracking-wider block">Nhật ký thay đổi vai trò (Audit Log)</h4>
        <p className="text-[10px] text-slate-400 mt-0.5">Lưu vết truy cập và thiết lập bảo mật riêng cho vai trò "{roleName}".</p>
      </div>

      {logs.length === 0 ? (
        <div className="py-12 border rounded-xl bg-slate-50 text-center text-slate-400 text-xs italic">
          Chưa có vết hoạt động bảo mật nào được lưu cho vai trò này.
        </div>
      ) : (
        <div className="relative border-l-2 border-slate-200 pl-4 ml-2 space-y-5 py-2">
          {logs.map((log) => {
            const isAdded = log.action.includes("ADDED") || log.action.includes("GRANTED");
            return (
              <div key={log.id} className="relative space-y-1">
                <span className={`absolute -left-[23px] top-1 w-2.5 h-2.5 rounded-full ring-4 ring-white ${isAdded ? "bg-emerald-500" : "bg-rose-500"}`} />
                <div className="flex items-center gap-2 text-[10px] font-semibold text-slate-400">
                  <span className="flex items-center gap-1 font-mono">
                    <Clock className="w-3 h-3 text-slate-300" />
                    <span>{log.createdAt}</span>
                  </span>
                  <span>•</span>
                  <span className="text-slate-600 font-bold">{log.actorName}</span>
                </div>
                <p className="text-xs text-slate-700 font-medium">{log.details}</p>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
