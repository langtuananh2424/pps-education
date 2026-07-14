import React, { useState } from "react";
import { UserMinus, UserPlus, Users } from "lucide-react";
import { Employee } from "@/types";
import { getEmployeeOrganization } from "../utils";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge from "@/components/ui/Badge";
import AssignUserModal from "./AssignUserModal";

interface RoleMembersPanelProps {
  memberEmployees: Employee[];
  allEmployees: Employee[];
  memberIds: string[];
  onAssign: (employeeId: string) => void;
  onRemove: (employeeId: string) => void;
}

export default function RoleMembersPanel({ memberEmployees, allEmployees, memberIds, onAssign, onRemove }: RoleMembersPanelProps) {
  const [showAssignModal, setShowAssignModal] = useState(false);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h4 className="text-xs font-bold text-slate-800 uppercase tracking-wider block">Thành viên hoạt vụ gán kèm</h4>
          <p className="text-[10px] text-slate-400 mt-0.5">Quản lý trực tiếp các cán bộ nhân sự được áp dụng cấu hình vai trò này.</p>
        </div>

        <button
          onClick={() => setShowAssignModal(true)}
          className="bg-brand-gradient hover:opacity-90 text-white px-3.5 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all shadow-sm"
        >
          <UserPlus className="w-4 h-4" />
          <span>Gán cán sự</span>
        </button>
      </div>

      <AssignUserModal
        open={showAssignModal}
        employees={allEmployees}
        currentMemberIds={memberIds}
        onClose={() => setShowAssignModal(false)}
        onAssign={(employeeId) => {
          onAssign(employeeId);
          setShowAssignModal(false);
        }}
      />

      {memberEmployees.length === 0 ? (
        <div className="py-12 border-2 border-dashed border-slate-200 rounded-xl flex flex-col items-center justify-center text-center p-6 space-y-2">
          <Users className="w-8 h-8 text-slate-300" />
          <div>
            <p className="text-xs font-bold text-slate-700">Chưa có thành viên nào gán vai trò này</p>
            <p className="text-[10px] text-slate-400 mt-0.5 max-w-xs leading-normal">
              Hệ thống yêu cầu gán ít nhất một tài khoản hoạt vụ để thực thi các tác vụ bảo mật.
            </p>
          </div>
        </div>
      ) : (
        <TableContainer>
          <thead>
            <tr>
              <Th>Họ và tên</Th>
              <Th>Email tài khoản</Th>
              <Th>Phân tổ phòng ban</Th>
              <Th>Trạng thái</Th>
              <Th className="text-center">Gỡ bỏ</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {memberEmployees.map((emp) => {
              const initials = emp.fullName.split(" ").slice(-2).map((n) => n[0]).join("").toUpperCase();
              return (
                <tr key={emp.id} className="hover:bg-slate-50/50 transition-colors">
                  <Td>
                    <div className="flex items-center gap-2.5">
                      <div className="w-7 h-7 rounded-full bg-orange-100 border border-orange-200 flex items-center justify-center text-[10px] font-bold text-brand-red shadow-inner shrink-0">
                        {initials}
                      </div>
                      <div>
                        <span className="font-bold text-slate-900 block tracking-tight">{emp.fullName}</span>
                        <span className="text-[9px] font-mono font-bold text-slate-400 block mt-0.5">{emp.id}</span>
                      </div>
                    </div>
                  </Td>
                  <Td className="truncate">{emp.email}</Td>
                  <Td>
                    <span className="text-[10px] bg-slate-100 text-slate-700 px-2 py-0.5 rounded-full border border-slate-200 font-medium">
                      {getEmployeeOrganization(emp)}
                    </span>
                  </Td>
                  <Td>
                    <Badge variant="success">{emp.status}</Badge>
                  </Td>
                  <Td className="text-center">
                    <button
                      onClick={() => onRemove(emp.id)}
                      className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                      title="Gỡ khỏi vai trò này"
                    >
                      <UserMinus className="w-4 h-4" />
                    </button>
                  </Td>
                </tr>
              );
            })}
          </tbody>
        </TableContainer>
      )}
    </div>
  );
}
