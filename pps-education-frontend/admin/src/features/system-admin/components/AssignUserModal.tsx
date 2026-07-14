import React, { useState } from "react";
import { UserPlus } from "lucide-react";
import { Employee } from "@/types";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";

interface AssignUserModalProps {
  open: boolean;
  employees: Employee[];
  currentMemberIds: string[];
  onClose: () => void;
  onAssign: (employeeId: string) => void;
}

export default function AssignUserModal({ open, employees, currentMemberIds, onClose, onAssign }: AssignUserModalProps) {
  const [selectedId, setSelectedId] = useState("");

  return (
    <Modal open={open} onClose={onClose} title="Gán nhân sự vào vai trò" size="md">
      <div className="space-y-3">
        <label className="text-[10px] uppercase font-bold text-slate-500 block flex items-center gap-1.5">
          <UserPlus className="w-3.5 h-3.5 text-brand-red" />
          Chọn cán bộ nhân sự
        </label>
        <select
          value={selectedId}
          onChange={(e) => setSelectedId(e.target.value)}
          className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
        >
          <option value="">-- Vui lòng chọn nhân sự --</option>
          {employees.map((emp) => {
            const isAlreadyMember = currentMemberIds.includes(emp.id);
            return (
              <option key={emp.id} value={emp.id} disabled={isAlreadyMember}>
                {emp.fullName} ({emp.email}) {isAlreadyMember ? "[Đã gán]" : ""}
              </option>
            );
          })}
        </select>
      </div>

      <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
        <Button variant="secondary" onClick={onClose}>
          Hủy bỏ
        </Button>
        <Button
          variant="dark"
          disabled={!selectedId}
          onClick={() => {
            onAssign(selectedId);
            setSelectedId("");
          }}
        >
          Xác nhận gán
        </Button>
      </div>
    </Modal>
  );
}
