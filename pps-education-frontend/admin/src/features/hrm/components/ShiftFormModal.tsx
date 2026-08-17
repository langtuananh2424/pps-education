import React, { useState } from "react";
import { Plus, Save } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { createShift, CreateShiftRequest, ShiftResponse, updateShift, UpdateShiftRequest } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const WEEKDAY_LABELS: { value: number; label: string }[] = [
  { value: 1, label: "T2" },
  { value: 2, label: "T3" },
  { value: 3, label: "T4" },
  { value: 4, label: "T5" },
  { value: 5, label: "T6" },
  { value: 6, label: "T7" },
  { value: 7, label: "CN" }
];

interface ShiftFormModalProps {
  shift?: ShiftResponse | null;
  onClose: () => void;
  onSaved: (id: number) => void;
}

function parseWeekdays(csv: string): Set<number> {
  return new Set(csv.split(",").map((s) => Number(s.trim())).filter((n) => !Number.isNaN(n)));
}

/**
 * UC-70 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13) — tạo/sửa Ca làm việc.
 * "T7 xen kẽ": tạo 2 ca riêng (VD 1 ca weekParity=ODD + 1 ca weekParity=EVEN)
 * rồi gán cả 2 cho cùng 1 nhân sự qua màn Gán ca (EmployeeShiftFormModal).
 */
export default function ShiftFormModal({ shift, onClose, onSaved }: ShiftFormModalProps) {
  const isEdit = shift != null;
  const [form, setForm] = useState({
    code: shift?.code ?? "",
    name: shift?.name ?? "",
    checkInTime: shift?.checkInTime?.slice(0, 5) ?? "08:00",
    checkOutTime: shift?.checkOutTime?.slice(0, 5) ?? "17:00",
    checkInWindowBeforeMinutes: shift?.checkInWindowBeforeMinutes ?? 30,
    checkInWindowAfterMinutes: shift?.checkInWindowAfterMinutes ?? 30,
    checkOutWindowBeforeMinutes: shift?.checkOutWindowBeforeMinutes ?? 30,
    checkOutWindowAfterMinutes: shift?.checkOutWindowAfterMinutes ?? 60,
    weekParity: shift?.weekParity ?? ("ALL" as "ALL" | "ODD" | "EVEN")
  });
  const [weekdays, setWeekdays] = useState<Set<number>>(
    shift ? parseWeekdays(shift.appliesToWeekdays) : new Set([1, 2, 3, 4, 5, 6])
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggleWeekday = (value: number) => {
    setWeekdays((prev) => {
      const next = new Set(prev);
      if (next.has(value)) next.delete(value);
      else next.add(value);
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.name.trim()) {
      setError("Vui lòng điền mã và tên ca làm việc.");
      return;
    }
    if (weekdays.size === 0) {
      setError("Vui lòng chọn ít nhất 1 ngày áp dụng.");
      return;
    }
    const appliesToWeekdays = Array.from(weekdays).sort((a, b) => a - b).join(",");

    setSubmitting(true);
    setError(null);
    try {
      if (isEdit) {
        const request: UpdateShiftRequest = {
          name: form.name.trim(),
          checkInTime: form.checkInTime,
          checkOutTime: form.checkOutTime,
          checkInWindowBeforeMinutes: form.checkInWindowBeforeMinutes,
          checkInWindowAfterMinutes: form.checkInWindowAfterMinutes,
          checkOutWindowBeforeMinutes: form.checkOutWindowBeforeMinutes,
          checkOutWindowAfterMinutes: form.checkOutWindowAfterMinutes,
          appliesToWeekdays,
          weekParity: form.weekParity
        };
        const updated = await updateShift(shift!.id, request);
        onSaved(updated.id);
      } else {
        const request: CreateShiftRequest = {
          code: form.code.trim(),
          name: form.name.trim(),
          checkInTime: form.checkInTime,
          checkOutTime: form.checkOutTime,
          checkInWindowBeforeMinutes: form.checkInWindowBeforeMinutes,
          checkInWindowAfterMinutes: form.checkInWindowAfterMinutes,
          checkOutWindowBeforeMinutes: form.checkOutWindowBeforeMinutes,
          checkOutWindowAfterMinutes: form.checkOutWindowAfterMinutes,
          appliesToWeekdays,
          weekParity: form.weekParity
        };
        const created = await createShift(request);
        onSaved(created.id);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu ca làm việc thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={isEdit ? "Sửa ca làm việc" : "Thêm ca làm việc"} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Mã ca *</label>
            <input
              value={form.code}
              disabled={isEdit}
              onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
              className={`${inputClass} font-mono disabled:opacity-60`}
            />
          </div>
          <div>
            <label className={labelClass}>Tên ca *</label>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Giờ vào *</label>
            <input
              type="time"
              value={form.checkInTime}
              onChange={(e) => setForm({ ...form, checkInTime: e.target.value })}
              className={inputClass}
            />
          </div>
          <div>
            <label className={labelClass}>Giờ ra *</label>
            <input
              type="time"
              value={form.checkOutTime}
              onChange={(e) => setForm({ ...form, checkOutTime: e.target.value })}
              className={inputClass}
            />
          </div>
        </div>

        <div className="space-y-2 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">Cửa sổ chấm công (phút)</span>
          <div className="grid grid-cols-4 gap-3">
            <div>
              <label className={labelClass}>Vào - trước</label>
              <input
                type="number"
                min={0}
                value={form.checkInWindowBeforeMinutes}
                onChange={(e) => setForm({ ...form, checkInWindowBeforeMinutes: Number(e.target.value) })}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Vào - sau</label>
              <input
                type="number"
                min={0}
                value={form.checkInWindowAfterMinutes}
                onChange={(e) => setForm({ ...form, checkInWindowAfterMinutes: Number(e.target.value) })}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Ra - trước</label>
              <input
                type="number"
                min={0}
                value={form.checkOutWindowBeforeMinutes}
                onChange={(e) => setForm({ ...form, checkOutWindowBeforeMinutes: Number(e.target.value) })}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Ra - sau</label>
              <input
                type="number"
                min={0}
                value={form.checkOutWindowAfterMinutes}
                onChange={(e) => setForm({ ...form, checkOutWindowAfterMinutes: Number(e.target.value) })}
                className={inputClass}
              />
            </div>
          </div>
        </div>

        <div className="space-y-2 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">Ngày áp dụng trong tuần *</span>
          <div className="flex flex-wrap gap-2">
            {WEEKDAY_LABELS.map((wd) => (
              <button
                key={wd.value}
                type="button"
                onClick={() => toggleWeekday(wd.value)}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold border transition-colors cursor-pointer ${
                  weekdays.has(wd.value)
                    ? "bg-brand-orange/10 border-brand-orange text-brand-red"
                    : "bg-white border-slate-200 text-slate-500 hover:bg-slate-50"
                }`}
              >
                {wd.label}
              </button>
            ))}
          </div>
          <div>
            <label className={labelClass}>Tuần áp dụng</label>
            <Select
              value={form.weekParity}
              onChange={(e) => setForm({ ...form, weekParity: e.target.value as "ALL" | "ODD" | "EVEN" })}
              className={`${inputClass} max-w-[200px]`}
            >
              <option value="ALL">Mọi tuần</option>
              <option value="ODD">Tuần lẻ</option>
              <option value="EVEN">Tuần chẵn</option>
            </Select>
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {isEdit ? <Save className="w-3.5 h-3.5" /> : <Plus className="w-3.5 h-3.5" />}
            {submitting ? "Đang lưu..." : isEdit ? "Lưu thay đổi" : "Tạo ca làm việc"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
