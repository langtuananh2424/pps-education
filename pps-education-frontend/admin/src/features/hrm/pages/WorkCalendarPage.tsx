import React, { useEffect, useState } from "react";
import { CalendarPlus, Trash2 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { Badge, Button, Modal, TableContainer, Td, Th } from "@/components/ui";
import Select from "@/components/ui/Select";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";
import {
  createWorkCalendarOverride,
  deleteWorkCalendarOverride,
  EmployeeResponse,
  listEmployees,
  listShifts,
  listWorkCalendar,
  ShiftResponse,
  WorkCalendarResponse
} from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const dayTypeLabels: Record<WorkCalendarResponse["dayType"], string> = {
  WORKING: "Làm bù",
  OFF: "Nghỉ",
  HOLIDAY: "Nghỉ lễ",
  COMPENSATORY: "Làm bù (lễ)"
};

const dayTypeVariant: Record<WorkCalendarResponse["dayType"], "success" | "danger" | "warning" | "neutral"> = {
  WORKING: "success",
  OFF: "neutral",
  HOLIDAY: "danger",
  COMPENSATORY: "success"
};

const scopeLabels: Record<WorkCalendarResponse["appliesToScope"], string> = {
  ALL: "Toàn trung tâm",
  SHIFT: "Theo ca",
  EMPLOYEE: "Theo nhân sự"
};

function firstDayOfMonth(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

function lastDayOfMonth(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth() + 1, 0).toISOString().slice(0, 10);
}

/**
 * UC-70 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13) — Lịch làm việc/nghỉ
 * lễ (work_calendar), override theo ngày cho UC-09 Main Flow bước 3.
 */
export default function WorkCalendarPage() {
  const { hasPermission } = useApp();
  const canCreate = hasPermission("hrm.work-calendar.create");
  const canDelete = hasPermission("hrm.work-calendar.delete");

  const [from, setFrom] = useState(firstDayOfMonth());
  const [to, setTo] = useState(lastDayOfMonth());
  const [overrides, setOverrides] = useState<WorkCalendarResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listWorkCalendar(from, to)
      .then(setOverrides)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được lịch làm việc."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [from, to]);

  const handleDelete = async (id: number) => {
    try {
      await deleteWorkCalendarOverride(id);
      showToast("Đã xoá override.");
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xoá thất bại.");
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Lịch làm việc / Nghỉ lễ</h1>
          <p className="text-xs text-slate-500 mt-1">
            Khai báo ngoại lệ so với ca làm việc mặc định (nghỉ lễ, làm bù T7) — dùng khi xác định "ngày làm việc" lúc chấm công (UC-09).
          </p>
        </div>
        {canCreate && (
          <Button variant="primary" onClick={() => setFormOpen(true)}>
            <CalendarPlus className="w-3.5 h-3.5" />
            Thêm override
          </Button>
        )}
      </div>

      <div className="flex items-center gap-2">
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className={`${inputClass} max-w-[160px]`} />
        <span className="text-[10px] text-slate-400">đến</span>
        <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className={`${inputClass} max-w-[160px]`} />
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <TableContainer>
        <thead>
          <tr>
            <Th>Ngày</Th>
            <Th>Loại</Th>
            <Th>Phạm vi</Th>
            <Th>Mô tả</Th>
            <Th />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {loading ? (
            <tr>
              <Td colSpan={5} className="text-center text-slate-400">
                Đang tải...
              </Td>
            </tr>
          ) : overrides.length === 0 ? (
            <tr>
              <Td colSpan={5} className="text-center text-slate-400">
                Không có override nào trong khoảng đã chọn.
              </Td>
            </tr>
          ) : (
            overrides.map((o) => (
              <tr key={o.id} className="hover:bg-slate-50/50 transition-colors">
                <Td className="font-mono font-bold text-slate-700">{o.calendarDate}</Td>
                <Td>
                  <Badge variant={dayTypeVariant[o.dayType]}>{dayTypeLabels[o.dayType]}</Badge>
                </Td>
                <Td>
                  {scopeLabels[o.appliesToScope]}
                  {o.shiftName && <span className="text-slate-400"> — {o.shiftName}</span>}
                  {o.employeeFullName && <span className="text-slate-400"> — {o.employeeFullName}</span>}
                </Td>
                <Td className="text-slate-500">{o.description ?? "—"}</Td>
                <Td>
                  {canDelete && (
                    <Button size="sm" variant="danger" onClick={() => handleDelete(o.id)}>
                      <Trash2 className="w-3.5 h-3.5" />
                    </Button>
                  )}
                </Td>
              </tr>
            ))
          )}
        </tbody>
      </TableContainer>

      {formOpen && (
        <WorkCalendarFormModal
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false);
            load();
            showToast("Đã tạo override lịch làm việc thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function WorkCalendarFormModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [calendarDate, setCalendarDate] = useState(new Date().toISOString().slice(0, 10));
  const [dayType, setDayType] = useState<WorkCalendarResponse["dayType"]>("HOLIDAY");
  const [scope, setScope] = useState<WorkCalendarResponse["appliesToScope"]>("ALL");
  const [shiftId, setShiftId] = useState<number | "">("");
  const [employeeId, setEmployeeId] = useState<number | "">("");
  const [description, setDescription] = useState("");
  const [shifts, setShifts] = useState<ShiftResponse[]>([]);
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listShifts()
      .then(setShifts)
      .catch(() => setShifts([]));
    listEmployees()
      .then(setEmployees)
      .catch(() => setEmployees([]));
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (scope === "SHIFT" && shiftId === "") {
      setError("Vui lòng chọn ca làm việc.");
      return;
    }
    if (scope === "EMPLOYEE" && employeeId === "") {
      setError("Vui lòng chọn nhân sự.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createWorkCalendarOverride({
        calendarDate,
        dayType,
        appliesToScope: scope,
        shiftId: scope === "SHIFT" ? (shiftId as number) : undefined,
        employeeId: scope === "EMPLOYEE" ? (employeeId as number) : undefined,
        description: description.trim() || undefined
      });
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo override thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Thêm override lịch làm việc" size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Ngày *</label>
            <input type="date" value={calendarDate} onChange={(e) => setCalendarDate(e.target.value)} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Loại *</label>
            <Select value={dayType} onChange={(e) => setDayType(e.target.value as WorkCalendarResponse["dayType"])} className={inputClass}>
              <option value="HOLIDAY">Nghỉ lễ</option>
              <option value="OFF">Nghỉ</option>
              <option value="WORKING">Làm bù</option>
              <option value="COMPENSATORY">Làm bù (lễ)</option>
            </Select>
          </div>
          <div className="col-span-2">
            <label className={labelClass}>Phạm vi áp dụng *</label>
            <Select
              value={scope}
              onChange={(e) => {
                setScope(e.target.value as WorkCalendarResponse["appliesToScope"]);
                setShiftId("");
                setEmployeeId("");
              }}
              className={inputClass}
            >
              <option value="ALL">Toàn trung tâm</option>
              <option value="SHIFT">Theo ca làm việc</option>
              <option value="EMPLOYEE">Theo nhân sự</option>
            </Select>
          </div>
          {scope === "SHIFT" && (
            <div className="col-span-2">
              <label className={labelClass}>Ca làm việc *</label>
              <Select value={shiftId} onChange={(e) => setShiftId(e.target.value === "" ? "" : Number(e.target.value))} className={inputClass}>
                <option value="">-- Chọn ca --</option>
                {shifts.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
            </div>
          )}
          {scope === "EMPLOYEE" && (
            <div className="col-span-2">
              <label className={labelClass}>Nhân sự *</label>
              <Select value={employeeId} onChange={(e) => setEmployeeId(e.target.value === "" ? "" : Number(e.target.value))} className={inputClass}>
                <option value="">-- Chọn nhân sự --</option>
                {employees.map((emp) => (
                  <option key={emp.id} value={emp.id}>
                    {emp.fullName} ({emp.employeeCode})
                  </option>
                ))}
              </Select>
            </div>
          )}
          <div className="col-span-2">
            <label className={labelClass}>Mô tả</label>
            <input value={description} onChange={(e) => setDescription(e.target.value)} className={inputClass} placeholder="VD: Nghỉ Quốc khánh 2/9" />
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Tạo override"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
