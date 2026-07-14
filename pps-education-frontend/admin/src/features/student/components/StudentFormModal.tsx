import React, { useState } from "react";
import { Save } from "lucide-react";
import { Classroom, Student } from "@/types";
import { mockCampuses } from "@/data/mockData";
import Modal from "@/components/ui/Modal";

export interface StudentFormValues {
  fullName: string;
  birthDate: string;
  avatarUrl: string;
  phone: string;
  parentName: string;
  parentPhone: string;
  campusId: string;
  classIds: string[];
  status: Student["status"];
}

interface StudentFormModalProps {
  isEdit: boolean;
  initialValues: StudentFormValues;
  classrooms: Classroom[];
  onClose: () => void;
  onSubmit: (values: StudentFormValues) => void;
}

export default function StudentFormModal({ isEdit, initialValues, classrooms, onClose, onSubmit }: StudentFormModalProps) {
  const [values, setValues] = useState(initialValues);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!values.fullName.trim()) return;
    onSubmit(values);
  };

  const toggleClass = (classId: string) => {
    const isChecked = values.classIds.includes(classId);
    if (isChecked) {
      if (values.classIds.length > 1) {
        setValues((v) => ({ ...v, classIds: v.classIds.filter((id) => id !== classId) }));
      } else {
        alert("Lỗi: Học viên phải ở trong ít nhất 1 lớp học!");
      }
    } else {
      setValues((v) => ({ ...v, classIds: [...v.classIds, classId] }));
    }
  };

  return (
    <Modal open onClose={onClose} title={isEdit ? "Cập nhật thông tin học viên" : "Tiếp nhận học viên mới"} size="md">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Họ và tên học viên</label>
            <input
              type="text"
              required
              placeholder="Nguyễn Văn A"
              value={values.fullName}
              onChange={(e) => setValues((v) => ({ ...v, fullName: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 rounded px-3 py-1.5 text-xs focus:outline-none"
            />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Ngày sinh</label>
            <input
              type="date"
              required
              value={values.birthDate}
              onChange={(e) => setValues((v) => ({ ...v, birthDate: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 rounded px-3 py-1.5 text-xs focus:outline-none font-mono"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Số điện thoại</label>
            <input
              type="text"
              placeholder="035..."
              value={values.phone}
              onChange={(e) => setValues((v) => ({ ...v, phone: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 rounded px-3 py-1.5 text-xs focus:outline-none"
            />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Trạng thái hiện tại</label>
            <select
              value={values.status}
              onChange={(e) => setValues((v) => ({ ...v, status: e.target.value as Student["status"] }))}
              className="w-full bg-slate-50 border border-slate-200 rounded px-3 py-1.5 text-xs focus:outline-none font-bold"
            >
              <option value="STUDYING">Đang Học</option>
              <option value="RESERVED">Bảo Lưu</option>
              <option value="SUSPENDED">Đình Chỉ</option>
              <option value="GRADUATED">Đã Tốt Nghiệp</option>
            </select>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Họ tên Phụ huynh</label>
            <input
              type="text"
              required
              placeholder="Nguyễn Văn B"
              value={values.parentName}
              onChange={(e) => setValues((v) => ({ ...v, parentName: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 rounded px-3 py-1.5 text-xs focus:outline-none"
            />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">SĐT Phụ huynh</label>
            <input
              type="text"
              required
              placeholder="091..."
              value={values.parentPhone}
              onChange={(e) => setValues((v) => ({ ...v, parentPhone: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 rounded px-3 py-1.5 text-xs focus:outline-none"
            />
          </div>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">URL ảnh đại diện (Tùy chọn)</label>
          <input
            type="text"
            placeholder="https://images.unsplash.com..."
            value={values.avatarUrl}
            onChange={(e) => setValues((v) => ({ ...v, avatarUrl: e.target.value }))}
            className="w-full bg-slate-50 border border-slate-200 rounded px-3 py-1.5 text-xs focus:outline-none"
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Điểm trường</label>
            <select
              value={values.campusId}
              onChange={(e) => setValues((v) => ({ ...v, campusId: e.target.value }))}
              className="w-full bg-slate-50 border border-slate-200 rounded px-3 py-1.5 text-xs focus:outline-none font-bold"
            >
              {mockCampuses.map((camp) => (
                <option key={camp.id} value={camp.id}>
                  {camp.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Lớp học tham gia</label>
            <div className="bg-slate-50 border border-slate-200 rounded p-2.5 max-h-[120px] overflow-y-auto space-y-1">
              {classrooms.map((cls) => (
                <label key={cls.id} className="flex items-center gap-2 text-xs font-semibold text-slate-700 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={values.classIds.includes(cls.id)}
                    onChange={() => toggleClass(cls.id)}
                    className="h-3.5 w-3.5 rounded border-slate-300 text-brand-orange focus:ring-brand-orange"
                  />
                  <span>{cls.name}</span>
                </label>
              ))}
            </div>
          </div>
        </div>

        <div className="pt-4 border-t border-slate-100 flex gap-2 justify-end">
          <button type="button" onClick={onClose} className="px-4 py-2 border border-slate-200 text-slate-600 hover:bg-slate-50 font-bold text-xs rounded-lg">
            Hủy bỏ
          </button>
          <button type="submit" className="px-4 py-2 bg-brand-orange hover:bg-brand-orange/90 text-white font-bold text-xs rounded-lg flex items-center gap-1.5 shadow-sm transition-all">
            <Save className="w-4 h-4 text-white" />
            <span>{isEdit ? "Cập nhật" : "Thêm mới"}</span>
          </button>
        </div>
      </form>
    </Modal>
  );
}
