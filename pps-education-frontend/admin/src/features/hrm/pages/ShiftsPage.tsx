import React, { useEffect, useMemo, useState } from "react";
import { Clock3, Plus, Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { Badge, Button, TableContainer, Td, Th } from "@/components/ui";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";
import {
  assignEmployeeShift,
  deactivateShift,
  DepartmentResponse,
  EmployeeResponse,
  listDepartments,
  listEmployees,
  listShifts,
  ShiftResponse
} from "../api";
import ShiftFormModal from "../components/ShiftFormModal";

function formatWeekdays(csv: string, t: (key: string) => string): string {
  return csv
    .split(",")
    .map((s) => t(`weekday.short.${Number(s.trim())}`))
    .join(", ");
}

function weekParityLabel(t: (key: string) => string, weekParity: ShiftResponse["weekParity"]): string {
  return t(`weekParity.${weekParity}`);
}

/**
 * UC-70 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13) — quản lý Ca làm việc
 * + gán ca cho nhân sự. Đầu vào bắt buộc để UC-09 (chấm công) xác định được
 * "ngày làm việc" — trước đây không có trang này, không nhân sự nào có ca
 * được gán, mọi lượt chấm công thật đều bị từ chối.
 */
export default function ShiftsPage() {
  const { t } = useTranslation("hrm-shifts");
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("shiftsPage.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const handleDeactivate = async (shift: ShiftResponse) => {
    try {
      await deactivateShift(shift.id);
      showToast(t("shiftsPage.deactivateSuccess", { name: shift.name }));
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("shiftsPage.deactivateError"));
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("shiftsPage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("shiftsPage.description")}</p>
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
            {t("shiftsPage.addButton")}
          </Button>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <TableContainer>
        <thead>
          <tr>
            <Th>{t("shiftsPage.columns.code")}</Th>
            <Th>{t("shiftsPage.columns.name")}</Th>
            <Th>{t("shiftsPage.columns.time")}</Th>
            <Th>{t("shiftsPage.columns.weekdays")}</Th>
            <Th>{t("shiftsPage.columns.weekParity")}</Th>
            <Th>{t("shiftsPage.columns.status")}</Th>
            <Th />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {loading ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                {t("shiftsPage.loading")}
              </Td>
            </tr>
          ) : shifts.length === 0 ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                {t("shiftsPage.empty")}
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
                <Td className="text-slate-500">{formatWeekdays(s.appliesToWeekdays, t)}</Td>
                <Td className="text-slate-500">{weekParityLabel(t, s.weekParity)}</Td>
                <Td>
                  <Badge variant={s.active ? "success" : "neutral"}>
                    {s.active ? t("shiftsPage.statusActive") : t("shiftsPage.statusInactive")}
                  </Badge>
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
                        {t("shiftsPage.editButton")}
                      </Button>
                      {s.active && (
                        <Button size="sm" variant="danger" onClick={() => handleDeactivate(s)}>
                          {t("shiftsPage.deactivateButton")}
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

      {canAssign && (
        <BulkAssignShiftPanel
          shifts={shifts.filter((s) => s.active)}
          onAssigned={(count) => showToast(t("shiftsPage.bulkAssign.assignedToast", { count }))}
        />
      )}

      {formOpen && (
        <ShiftFormModal
          shift={editingShift}
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false);
            load();
            showToast(editingShift ? t("shiftsPage.updateSuccess") : t("shiftsPage.createSuccess"));
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

interface BulkAssignResult {
  employeeId: number;
  employeeName: string;
  ok: boolean;
  message?: string;
}

/**
 * Gán 1 ca cho NHIỀU nhân sự cùng lúc, lọc theo phòng ban -- gọi lặp lại
 * assignEmployeeShift (mỗi nhân sự 1 transaction riêng ở backend, xem
 * EmployeeShiftService.assignShift) nên 1 nhân sự bị chồng chéo lịch
 * (409) không chặn các nhân sự còn lại; kết quả từng người hiện ở bảng
 * bên dưới sau khi gán.
 */
function BulkAssignShiftPanel({ shifts, onAssigned }: { shifts: ShiftResponse[]; onAssigned: (count: number) => void }) {
  const { t } = useTranslation("hrm-shifts");
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [departmentId, setDepartmentId] = useState<number | "">("");
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [shiftId, setShiftId] = useState<number | "">("");
  const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString().slice(0, 10));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<BulkAssignResult[]>([]);

  useEffect(() => {
    listEmployees()
      .then(setEmployees)
      .catch(() => setEmployees([]));
    listDepartments()
      .then(setDepartments)
      .catch(() => setDepartments([]));
  }, []);

  const filteredEmployees = useMemo(
    () => (departmentId === "" ? employees : employees.filter((e) => e.departmentId === departmentId)),
    [employees, departmentId]
  );

  // Đổi phòng ban thì bỏ chọn những nhân sự không còn nằm trong danh sách lọc hiện tại.
  useEffect(() => {
    setSelectedIds((prev) => new Set(Array.from(prev).filter((id) => filteredEmployees.some((e) => e.id === id))));
  }, [filteredEmployees]);

  const allFilteredSelected = filteredEmployees.length > 0 && filteredEmployees.every((e) => selectedIds.has(e.id));

  const toggleEmployee = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    setSelectedIds((prev) => {
      if (allFilteredSelected) {
        const next = new Set(prev);
        filteredEmployees.forEach((e) => next.delete(e.id));
        return next;
      }
      const next = new Set(prev);
      filteredEmployees.forEach((e) => next.add(e.id));
      return next;
    });
  };

  const handleBulkAssign = async () => {
    if (selectedIds.size === 0 || shiftId === "") {
      setError(t("shiftsPage.bulkAssign.selectAtLeastOne"));
      return;
    }
    setSubmitting(true);
    setError(null);
    setResults([]);

    const targets = employees.filter((e) => selectedIds.has(e.id));
    const outcomes = await Promise.all(
      targets.map(async (emp): Promise<BulkAssignResult> => {
        try {
          await assignEmployeeShift({ employeeId: emp.id, shiftId, effectiveFrom });
          return { employeeId: emp.id, employeeName: emp.fullName, ok: true };
        } catch (err) {
          return {
            employeeId: emp.id,
            employeeName: emp.fullName,
            ok: false,
            message: err instanceof ApiError ? err.message : t("shiftsPage.bulkAssign.resultError")
          };
        }
      })
    );

    setResults(outcomes);
    setSubmitting(false);
    const successCount = outcomes.filter((r) => r.ok).length;
    if (successCount > 0) {
      onAssigned(successCount);
    }
    if (outcomes.some((r) => !r.ok)) {
      setError(
        t("shiftsPage.bulkAssign.partialFailure", {
          failed: outcomes.filter((r) => !r.ok).length,
          total: outcomes.length
        })
      );
    }
  };

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft p-5 space-y-4">
      <div className="flex items-center gap-2">
        <Users className="w-4 h-4 text-slate-400" />
        <span className="text-xs font-bold text-slate-700 font-display">{t("shiftsPage.bulkAssign.title")}</span>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={departmentId}
          onChange={(e) => setDepartmentId(e.target.value === "" ? "" : Number(e.target.value))}
          className="bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none min-w-[180px]"
        >
          <option value="">{t("shiftsPage.bulkAssign.allDepartments")}</option>
          {departments.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </Select>
        <Select
          value={shiftId}
          onChange={(e) => setShiftId(e.target.value === "" ? "" : Number(e.target.value))}
          className="bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none min-w-[160px]"
        >
          <option value="">{t("shiftsPage.bulkAssign.selectShiftPlaceholder")}</option>
          {shifts.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </Select>
        <div className="w-36">
          <DatePicker value={effectiveFrom} onChange={setEffectiveFrom} />
        </div>
        <Button variant="primary" disabled={submitting} onClick={handleBulkAssign}>
          <Clock3 className="w-3.5 h-3.5" />
          {submitting
            ? t("shiftsPage.bulkAssign.assigning")
            : selectedIds.size > 0
            ? t("shiftsPage.bulkAssign.assignButtonWithCount", { count: selectedIds.size })
            : t("shiftsPage.bulkAssign.assignButtonNoCount")}
        </Button>
      </div>

      <div className="border border-slate-200 rounded-lg max-h-64 overflow-y-auto">
        <label className="flex items-center gap-2 px-3 py-2 text-xs font-semibold text-slate-600 bg-slate-50 border-b border-slate-200 sticky top-0 cursor-pointer">
          <input type="checkbox" checked={allFilteredSelected} onChange={toggleSelectAll} />
          {departmentId !== ""
            ? t("shiftsPage.bulkAssign.selectAllLabelFiltered", { count: filteredEmployees.length })
            : t("shiftsPage.bulkAssign.selectAllLabel", { count: filteredEmployees.length })}
        </label>
        {filteredEmployees.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-3">{t("shiftsPage.bulkAssign.noEmployeesMatch")}</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {filteredEmployees.map((emp) => (
              <label key={emp.id} className="flex items-center gap-2 px-3 py-2 text-xs hover:bg-slate-50 cursor-pointer">
                <input type="checkbox" checked={selectedIds.has(emp.id)} onChange={() => toggleEmployee(emp.id)} />
                <span className="font-semibold text-slate-700">{emp.fullName}</span>
                <span className="text-slate-400">({emp.employeeCode})</span>
              </label>
            ))}
          </div>
        )}
      </div>

      {results.length > 0 && (
        <div className="pt-1">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("shiftsPage.bulkAssign.resultsTitle")}</span>
          <div className="divide-y divide-slate-100 mt-1">
            {results.map((r) => (
              <div key={r.employeeId} className="flex items-center justify-between py-1.5 text-xs">
                <span className="font-semibold text-slate-700">{r.employeeName}</span>
                {r.ok ? (
                  <Badge variant="success">{t("shiftsPage.bulkAssign.resultSuccess")}</Badge>
                ) : (
                  <span className="text-rose-600">{r.message}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
