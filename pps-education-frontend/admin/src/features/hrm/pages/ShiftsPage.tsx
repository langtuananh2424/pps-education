import React, { useEffect, useState } from "react";
import { Clock3, Plus, UserPlus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { Badge, Button, TableContainer, Td, Th } from "@/components/ui";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";
import {
  assignEmployeeShift,
  deactivateShift,
  EmployeeResponse,
  EmployeeShiftResponse,
  listEmployees,
  listEmployeeShiftHistory,
  listShifts,
  ShiftResponse
} from "../api";
import ShiftFormModal from "../components/ShiftFormModal";

const WEEKDAY_SHORT: Record<number, string> = { 1: "T2", 2: "T3", 3: "T4", 4: "T5", 5: "T6", 6: "T7", 7: "CN" };

function formatWeekdays(csv: string): string {
  return csv
    .split(",")
    .map((s) => WEEKDAY_SHORT[Number(s.trim())] ?? s)
    .join(", ");
}

const weekParityLabels: Record<ShiftResponse["weekParity"], string> = {
  ALL: "Mọi tuần",
  ODD: "Tuần lẻ",
  EVEN: "Tuần chẵn"
};

/**
 * UC-70 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13) — quản lý Ca làm việc
 * + gán ca cho nhân sự. Đầu vào bắt buộc để UC-09 (chấm công) xác định được
 * "ngày làm việc" — trước đây không có trang này, không nhân sự nào có ca
 * được gán, mọi lượt chấm công thật đều bị từ chối.
 */
export default function ShiftsPage() {
  const { hasPermission } = useApp();
  const canManageShift = hasPermission("hrm.shift.create");
  const canAssign = hasPermission("hrm.employee-shift.assign");

  const [shifts, setShifts] = useState<ShiftResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editingShift, setEditingShift] = useState<ShiftResponse | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listShifts()
      .then(setShifts)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách ca làm việc."))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const handleDeactivate = async (shift: ShiftResponse) => {
    try {
      await deactivateShift(shift.id);
      showToast(`Đã vô hiệu hoá ca "${shift.name}".`);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Vô hiệu hoá thất bại.");
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Ca làm việc</h1>
          <p className="text-xs text-slate-500 mt-1">
            Cấu hình ca làm việc chuẩn + gán ca cho nhân sự — đầu vào bắt buộc để xác định "ngày làm việc" khi chấm công (UC-09).
          </p>
        </div>
        {canManageShift && (
          <Button
            variant="primary"
            onClick={() => {
              setEditingShift(null);
              setFormOpen(true);
            }}
          >
            <Plus className="w-3.5 h-3.5" />
            Thêm ca làm việc
          </Button>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <TableContainer>
        <thead>
          <tr>
            <Th>Mã</Th>
            <Th>Tên ca</Th>
            <Th>Giờ vào/ra</Th>
            <Th>Ngày áp dụng</Th>
            <Th>Tuần</Th>
            <Th>Trạng thái</Th>
            <Th />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {loading ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                Đang tải...
              </Td>
            </tr>
          ) : shifts.length === 0 ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                Chưa có ca làm việc nào.
              </Td>
            </tr>
          ) : (
            shifts.map((s) => (
              <tr key={s.id} className="hover:bg-slate-50/50 transition-colors">
                <Td className="font-mono font-bold text-slate-700">{s.code}</Td>
                <Td className="font-bold text-slate-800">{s.name}</Td>
                <Td className="font-mono">
                  {s.checkInTime.slice(0, 5)} - {s.checkOutTime.slice(0, 5)}
                </Td>
                <Td className="text-slate-500">{formatWeekdays(s.appliesToWeekdays)}</Td>
                <Td className="text-slate-500">{weekParityLabels[s.weekParity]}</Td>
                <Td>
                  <Badge variant={s.active ? "success" : "neutral"}>{s.active ? "Đang dùng" : "Đã vô hiệu hoá"}</Badge>
                </Td>
                <Td>
                  {canManageShift && (
                    <div className="flex items-center gap-1.5 justify-end">
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => {
                          setEditingShift(s);
                          setFormOpen(true);
                        }}
                      >
                        Sửa
                      </Button>
                      {s.active && (
                        <Button size="sm" variant="danger" onClick={() => handleDeactivate(s)}>
                          Vô hiệu hoá
                        </Button>
                      )}
                    </div>
                  )}
                </Td>
              </tr>
            ))
          )}
        </tbody>
      </TableContainer>

      {canAssign && <AssignShiftPanel shifts={shifts.filter((s) => s.active)} onAssigned={() => showToast("Đã gán ca làm việc thành công!")} />}

      {formOpen && (
        <ShiftFormModal
          shift={editingShift}
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false);
            load();
            showToast(editingShift ? "Đã cập nhật ca làm việc thành công!" : "Đã tạo ca làm việc thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function AssignShiftPanel({ shifts, onAssigned }: { shifts: ShiftResponse[]; onAssigned: () => void }) {
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [employeeId, setEmployeeId] = useState<number | "">("");
  const [shiftId, setShiftId] = useState<number | "">("");
  const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString().slice(0, 10));
  const [history, setHistory] = useState<EmployeeShiftResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listEmployees()
      .then(setEmployees)
      .catch(() => setEmployees([]));
  }, []);

  useEffect(() => {
    if (employeeId === "") {
      setHistory([]);
      return;
    }
    listEmployeeShiftHistory(employeeId)
      .then(setHistory)
      .catch(() => setHistory([]));
  }, [employeeId]);

  const handleAssign = async () => {
    if (employeeId === "" || shiftId === "") {
      setError("Vui lòng chọn nhân sự và ca làm việc.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await assignEmployeeShift({ employeeId, shiftId, effectiveFrom });
      const refreshed = await listEmployeeShiftHistory(employeeId);
      setHistory(refreshed);
      onAssigned();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gán ca thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft p-5 space-y-4">
      <div className="flex items-center gap-2">
        <UserPlus className="w-4 h-4 text-slate-400" />
        <span className="text-xs font-bold text-slate-700 font-display">Gán ca cho nhân sự</span>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex flex-wrap items-center gap-2">
        <select
          value={employeeId}
          onChange={(e) => setEmployeeId(e.target.value === "" ? "" : Number(e.target.value))}
          className="bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none min-w-[200px]"
        >
          <option value="">-- Chọn nhân sự --</option>
          {employees.map((emp) => (
            <option key={emp.id} value={emp.id}>
              {emp.fullName} ({emp.employeeCode})
            </option>
          ))}
        </select>
        <select
          value={shiftId}
          onChange={(e) => setShiftId(e.target.value === "" ? "" : Number(e.target.value))}
          className="bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none min-w-[160px]"
        >
          <option value="">-- Chọn ca --</option>
          {shifts.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        <input
          type="date"
          value={effectiveFrom}
          onChange={(e) => setEffectiveFrom(e.target.value)}
          className="bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
        />
        <Button variant="primary" disabled={submitting} onClick={handleAssign}>
          <Clock3 className="w-3.5 h-3.5" />
          {submitting ? "Đang gán..." : "Gán ca"}
        </Button>
      </div>

      {employeeId !== "" && (
        <div className="pt-2">
          <span className="text-[10px] font-bold uppercase text-slate-500">Lịch sử gán ca</span>
          {history.length === 0 ? (
            <p className="text-xs text-slate-400 italic mt-1">Nhân sự này chưa được gán ca nào.</p>
          ) : (
            <div className="divide-y divide-slate-100 mt-1">
              {history.map((h) => (
                <div key={h.id} className="flex items-center justify-between py-2 text-xs">
                  <span className="font-semibold text-slate-700">{h.shiftName}</span>
                  <span className="text-slate-400">
                    {h.effectiveFrom} → {h.effectiveTo ?? "hiện tại"}
                  </span>
                  {h.effectiveTo == null && <Badge variant="success">Đang áp dụng</Badge>}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
