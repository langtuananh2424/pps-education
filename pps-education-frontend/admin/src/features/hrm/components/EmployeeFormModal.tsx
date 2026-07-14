import React, { useState } from "react";
import { Save, Users } from "lucide-react";
import { Employee, UserRole } from "@/types";
import { mockCampuses } from "@/data/mockData";
import { roleLabels } from "@/constants/roles";
import Modal from "@/components/ui/Modal";

const campusShortNames: Record<string, string> = {
  "CAMP-01": "Hai Bà Trưng",
  "CAMP-02": "Cầu Giấy",
  "CAMP-03": "TH Nghĩa Tân",
  "CAMP-04": "THCS Lê Quý Đôn"
};

export interface EmployeeFormValues {
  fullName: string;
  email: string;
  phone: string;
  role: UserRole;
  campusIds: string[];
  degrees: string;
  certificates: string;
  contractType: "LABOR" | "COLLABORATOR" | "PROBATION";
  baseSalary: number;
  status: "ACTIVE" | "INACTIVE";
}

interface EmployeeFormModalProps {
  mode: "create" | "edit";
  initialValues: EmployeeFormValues;
  onClose: () => void;
  onSubmit: (values: EmployeeFormValues) => void;
}

export default function EmployeeFormModal({ mode, initialValues, onClose, onSubmit }: EmployeeFormModalProps) {
  const [values, setValues] = useState(initialValues);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!values.fullName || !values.email || !values.phone) {
      alert("Vui lòng nhập đầy đủ các trường thông tin bắt buộc!");
      return;
    }
    onSubmit(values);
  };

  const toggleCampus = (campusId: string, checked: boolean) => {
    setValues((v) => ({
      ...v,
      campusIds: checked ? [...v.campusIds, campusId] : v.campusIds.filter((id) => id !== campusId)
    }));
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={mode === "create" ? "Khai báo hồ sơ cán bộ mới" : "Cập nhật hồ sơ cán bộ"}
      size="lg"
    >
      <form onSubmit={handleSubmit} className="space-y-4 text-xs">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">
              Họ và tên <span className="text-rose-500">*</span>
            </label>
            <input
              required
              type="text"
              placeholder="Nguyễn Văn A"
              value={values.fullName}
              onChange={(e) => setValues((v) => ({ ...v, fullName: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none focus:border-brand-orange"
            />
          </div>

          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">
              Số điện thoại <span className="text-rose-500">*</span>
            </label>
            <input
              required
              type="text"
              placeholder="09xxxxxxxx"
              value={values.phone}
              onChange={(e) => setValues((v) => ({ ...v, phone: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none focus:border-brand-orange"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">
              Email công vụ <span className="text-rose-500">*</span>
            </label>
            <input
              required
              type="email"
              placeholder="email@pps.edu.vn"
              value={values.email}
              onChange={(e) => setValues((v) => ({ ...v, email: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none focus:border-brand-orange"
            />
          </div>

          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Chức vụ / Vai trò</label>
            <select
              value={values.role}
              onChange={(e) => setValues((v) => ({ ...v, role: e.target.value as UserRole }))}
              className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
            >
              {Object.values(UserRole).map((role) => (
                <option key={role} value={role}>
                  {roleLabels[role]}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Điểm trường phụ trách (Đa cơ sở)</label>
          <div className="grid grid-cols-2 gap-2 p-3 bg-slate-50 border border-slate-200/60 rounded-lg">
            {mockCampuses.map((camp) => (
              <label key={camp.id} className="flex items-center gap-2 select-none cursor-pointer">
                <input
                  type="checkbox"
                  checked={values.campusIds.includes(camp.id)}
                  onChange={(e) => toggleCampus(camp.id, e.target.checked)}
                  className="rounded border-slate-300 text-brand-orange focus:ring-brand-orange"
                />
                <span className="text-[11px] text-slate-700 truncate" title={camp.name}>
                  {campusShortNames[camp.id] || camp.name}
                </span>
              </label>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Bằng cấp (Ngăn cách bởi dấu phẩy)</label>
            <input
              type="text"
              placeholder="Thạc sĩ Sư Phạm, Cử nhân Tiếng Anh"
              value={values.degrees}
              onChange={(e) => setValues((v) => ({ ...v, degrees: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
            />
          </div>

          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Chứng chỉ (Ngăn cách bởi dấu phẩy)</label>
            <input
              type="text"
              placeholder="IELTS 8.0, CELTA, TESOL"
              value={values.certificates}
              onChange={(e) => setValues((v) => ({ ...v, certificates: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
            />
          </div>
        </div>

        {mode === "edit" && (
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Trạng thái làm việc</label>
            <select
              value={values.status}
              onChange={(e) => setValues((v) => ({ ...v, status: e.target.value as "ACTIVE" | "INACTIVE" }))}
              className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
            >
              <option value="ACTIVE">Đang làm việc (ACTIVE)</option>
              <option value="INACTIVE">Nghỉ việc / Tắt hoạt động (INACTIVE)</option>
            </select>
          </div>
        )}

        <div className="p-3 bg-orange-50/40 rounded-xl border border-brand-yellow/20 space-y-3.5">
          <span className="text-[10px] font-extrabold text-brand-orange uppercase tracking-wider block">Thiết lập hợp đồng</span>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Loại hợp đồng</label>
              <select
                value={values.contractType}
                onChange={(e) => setValues((v) => ({ ...v, contractType: e.target.value as EmployeeFormValues["contractType"] }))}
                className="w-full bg-white border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
              >
                <option value="LABOR">HĐ Lao động Dài hạn (LABOR)</option>
                <option value="COLLABORATOR">HĐ Cộng tác viên / Khoán (COLLABORATOR)</option>
                <option value="PROBATION">HĐ Thử việc (PROBATION)</option>
              </select>
            </div>

            <div className="space-y-1">
              <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500 font-mono">Lương cơ bản (đ)</label>
              <input
                type="number"
                value={values.baseSalary}
                onChange={(e) => setValues((v) => ({ ...v, baseSalary: Number(e.target.value) }))}
                className="w-full bg-white border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none font-mono"
              />
            </div>
          </div>
        </div>

        <div className="flex gap-2.5 justify-end pt-3 border-t border-slate-100 shrink-0">
          <button type="button" onClick={onClose} className="px-4 py-2 border border-slate-200 hover:bg-slate-50 text-slate-600 rounded-lg font-bold">
            Hủy bỏ
          </button>
          <button type="submit" className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-lg font-bold flex items-center gap-1.5">
            {mode === "create" ? <Users className="w-4 h-4 text-brand-yellow" /> : <Save className="w-4 h-4 text-brand-yellow" />}
            <span>{mode === "create" ? "Khai báo hồ sơ" : "Lưu thay đổi"}</span>
          </button>
        </div>
      </form>
    </Modal>
  );
}
