import React, { useEffect, useState } from "react";
import { CalendarPlus, Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";
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

function dayTypeLabel(t: (key: string) => string, dayType: WorkCalendarResponse["dayType"]): string {
  return t(`workCalendarPage.dayType.${dayType}`);
}

const dayTypeVariant: Record<WorkCalendarResponse["dayType"], "success" | "danger" | "warning" | "neutral"> = {
  WORKING: "success",
  OFF: "neutral",
  HOLIDAY: "danger",
  COMPENSATORY: "success"
};

function scopeLabel(t: (key: string) => string, scope: WorkCalendarResponse["appliesToScope"]): string {
  return t(`workCalendarPage.scope.${scope}`);
}

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
  const { t } = useTranslation("hrm-shifts");
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("workCalendarPage.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [from, to]);

  const handleDelete = async (id: number) => {
    try {
      await deleteWorkCalendarOverride(id);
      showToast(t("workCalendarPage.deleteSuccess"));
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("workCalendarPage.deleteError"));
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("workCalendarPage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("workCalendarPage.description")}</p>
        </div>
        {canCreate && (
          <Button variant="primary" onClick={() => setFormOpen(true)}>
            <CalendarPlus className="w-3.5 h-3.5" />
            {t("workCalendarPage.addButton")}
          </Button>
        )}
      </div>

      <div className="flex items-center gap-2">
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className={`${inputClass} max-w-[160px]`} />
        <span className="text-[10px] text-slate-400">{t("workCalendarPage.rangeSeparator")}</span>
        <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className={`${inputClass} max-w-[160px]`} />
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <TableContainer>
        <thead>
          <tr>
            <Th>{t("workCalendarPage.columns.date")}</Th>
            <Th>{t("workCalendarPage.columns.type")}</Th>
            <Th>{t("workCalendarPage.columns.scope")}</Th>
            <Th>{t("workCalendarPage.columns.description")}</Th>
            <Th />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {loading ? (
            <tr>
              <Td colSpan={5} className="text-center text-slate-400">
                {t("workCalendarPage.loading")}
              </Td>
            </tr>
          ) : overrides.length === 0 ? (
            <tr>
              <Td colSpan={5} className="text-center text-slate-400">
                {t("workCalendarPage.empty")}
              </Td>
            </tr>
          ) : (
            overrides.map((o) => (
              <tr key={o.id} className="hover:bg-slate-50/50 transition-colors">
                <Td className="font-mono font-bold text-slate-700">{o.calendarDate}</Td>
                <Td>
                  <Badge variant={dayTypeVariant[o.dayType]}>{dayTypeLabel(t, o.dayType)}</Badge>
                </Td>
                <Td>
                  {scopeLabel(t, o.appliesToScope)}
                  {o.shiftName && <span className="text-slate-400"> — {o.shiftName}</span>}
                  {o.employeeFullName && <span className="text-slate-400"> — {o.employeeFullName}</span>}
                </Td>
                <Td className="text-slate-500">{o.description ?? t("workCalendarPage.noDescription")}</Td>
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
            showToast(t("workCalendarPage.createSuccess"));
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function WorkCalendarFormModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation("hrm-shifts");
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
      setError(t("workCalendarPage.form.selectShiftRequired"));
      return;
    }
    if (scope === "EMPLOYEE" && employeeId === "") {
      setError(t("workCalendarPage.form.selectEmployeeRequired"));
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
      setError(err instanceof ApiError ? err.message : t("workCalendarPage.form.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("workCalendarPage.form.title")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("workCalendarPage.form.dateLabel")}</label>
            <input type="date" value={calendarDate} onChange={(e) => setCalendarDate(e.target.value)} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("workCalendarPage.form.typeLabel")}</label>
            <Select value={dayType} onChange={(e) => setDayType(e.target.value as WorkCalendarResponse["dayType"])} className={inputClass}>
              <option value="HOLIDAY">{t("workCalendarPage.dayType.HOLIDAY")}</option>
              <option value="OFF">{t("workCalendarPage.dayType.OFF")}</option>
              <option value="WORKING">{t("workCalendarPage.dayType.WORKING")}</option>
              <option value="COMPENSATORY">{t("workCalendarPage.dayType.COMPENSATORY")}</option>
            </Select>
          </div>
          <div className="col-span-2">
            <label className={labelClass}>{t("workCalendarPage.form.scopeLabel")}</label>
            <Select
              value={scope}
              onChange={(e) => {
                setScope(e.target.value as WorkCalendarResponse["appliesToScope"]);
                setShiftId("");
                setEmployeeId("");
              }}
              className={inputClass}
            >
              <option value="ALL">{t("workCalendarPage.form.scopeOptionAll")}</option>
              <option value="SHIFT">{t("workCalendarPage.form.scopeOptionShift")}</option>
              <option value="EMPLOYEE">{t("workCalendarPage.form.scopeOptionEmployee")}</option>
            </Select>
          </div>
          {scope === "SHIFT" && (
            <div className="col-span-2">
              <label className={labelClass}>{t("workCalendarPage.form.shiftLabel")}</label>
              <Select value={shiftId} onChange={(e) => setShiftId(e.target.value === "" ? "" : Number(e.target.value))} className={inputClass}>
                <option value="">{t("workCalendarPage.form.selectShiftPlaceholder")}</option>
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
              <label className={labelClass}>{t("workCalendarPage.form.employeeLabel")}</label>
              <Select value={employeeId} onChange={(e) => setEmployeeId(e.target.value === "" ? "" : Number(e.target.value))} className={inputClass}>
                <option value="">{t("workCalendarPage.form.selectEmployeePlaceholder")}</option>
                {employees.map((emp) => (
                  <option key={emp.id} value={emp.id}>
                    {emp.fullName} ({emp.employeeCode})
                  </option>
                ))}
              </Select>
            </div>
          )}
          <div className="col-span-2">
            <label className={labelClass}>{t("workCalendarPage.form.descriptionLabel")}</label>
            <input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className={inputClass}
              placeholder={t("workCalendarPage.form.descriptionPlaceholder")}
            />
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("workCalendarPage.form.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("workCalendarPage.form.saving") : t("workCalendarPage.form.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
