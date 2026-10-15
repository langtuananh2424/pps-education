import React, { useState } from "react";
import { Save } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { SiteResponse } from "@/features/facility/api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import Time24Input from "@/components/ui/Time24Input";
import FloatingError from "@/components/ui/FloatingError";
import { createManualAttendanceRecord, EmployeeResponse } from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[12px] uppercase font-bold text-slate-500 block mb-1";

interface ManualAttendanceModalProps {
  employees: EmployeeResponse[];
  sites: SiteResponse[];
  onClose: () => void;
  onSaved: () => void;
}

/**
 * UC-09 (bổ sung): quản trị viên nhập chấm công thủ công thay cho nhân sự. Lý do bắt buộc;
 * người nhập do backend ghi từ tài khoản đăng nhập (attendance_records_history).
 */
export default function ManualAttendanceModal({ employees, sites, onClose, onSaved }: ManualAttendanceModalProps) {
  const { t } = useTranslation("hrm-attendance");
  const [employeeId, setEmployeeId] = useState<number | "">("");
  const [workDate, setWorkDate] = useState(new Date().toISOString().slice(0, 10));
  const [checkIn, setCheckIn] = useState("");
  const [checkOut, setCheckOut] = useState("");
  const [siteId, setSiteId] = useState<number | "">("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const timeOk = (v: string) => v === "" || /^\d{2}:\d{2}$/.test(v);
    if (employeeId === "" || !workDate || (!checkIn && !checkOut) || !timeOk(checkIn) || !timeOk(checkOut) || !reason.trim()) {
      setError(t("attendancePage.manualForm.required"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createManualAttendanceRecord({
        employeeId,
        workDate,
        checkInTime: checkIn || undefined,
        checkOutTime: checkOut || undefined,
        siteId: siteId === "" ? undefined : siteId,
        reason: reason.trim()
      });
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("attendancePage.manualForm.saveError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("attendancePage.manualForm.title")} size="md">
      <form onSubmit={handleSubmit} className="space-y-4">
        <FloatingError message={error} onClose={() => setError(null)} />
        <div>
          <label className={labelClass}>{t("attendancePage.manualForm.employee")}</label>
          <Select
            value={employeeId}
            onChange={(e) => setEmployeeId(e.target.value === "" ? "" : Number(e.target.value))}
            className={inputClass}
          >
            <option value="">{t("attendancePage.manualForm.selectEmployee")}</option>
            {employees.map((emp) => (
              <option key={emp.id} value={emp.id}>
                {emp.fullName} ({emp.employeeCode})
              </option>
            ))}
          </Select>
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div>
            <label className={labelClass}>{t("attendancePage.manualForm.date")}</label>
            <DatePicker value={workDate} onChange={setWorkDate} max={new Date().toISOString().slice(0, 10)} />
          </div>
          <div>
            <label className={labelClass}>{t("attendancePage.manualForm.checkIn")}</label>
            <Time24Input value={checkIn} onChange={setCheckIn} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("attendancePage.manualForm.checkOut")}</label>
            <Time24Input value={checkOut} onChange={setCheckOut} className={inputClass} />
          </div>
        </div>
        <div>
          <label className={labelClass}>{t("attendancePage.manualForm.site")}</label>
          <Select value={siteId} onChange={(e) => setSiteId(e.target.value === "" ? "" : Number(e.target.value))} className={inputClass}>
            <option value="">{t("attendancePage.manualForm.noSite")}</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("attendancePage.manualForm.reason")}</label>
          <textarea
            value={reason}
            maxLength={500}
            rows={3}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t("attendancePage.manualForm.reasonPlaceholder")}
            className={inputClass}
          />
          <p className="text-[12px] text-slate-400 mt-1">{t("attendancePage.manualForm.hint")}</p>
        </div>
        <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
          <Button type="button" variant="ghost" onClick={onClose}>
            {t("attendancePage.manualForm.cancel", { defaultValue: "Huỷ" })}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Save className="w-3.5 h-3.5" />
            {submitting ? t("attendancePage.manualForm.saving") : t("attendancePage.manualForm.save")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
