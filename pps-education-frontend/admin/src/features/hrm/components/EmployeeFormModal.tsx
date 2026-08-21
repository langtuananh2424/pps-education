import React, { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import AccountSelector, { AccountSelection } from "@/features/system-admin/components/AccountSelector";
import { createEmployee, CreateEmployeeRequest, DepartmentResponse, listDepartments, listPositions, PositionResponse } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import AvatarUploadField from "@/components/ui/AvatarUploadField";
import { uploadMedia } from "@/features/lms/api";
import Select from "@/components/ui/Select";

const TODAY_ISO = new Date().toISOString().slice(0, 10);

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface EmployeeFormModalProps {
  onClose: () => void;
  onCreated: (id: number) => void;
}

/** UC-08 Main Flow bước 1-2: khởi tạo hồ sơ nhân sự mới, kèm tài khoản mới hoặc gán tài khoản có sẵn (tìm theo email). */
export default function EmployeeFormModal({ onClose, onCreated }: EmployeeFormModalProps) {
  const { t } = useTranslation("hrm-employees");
  const [account, setAccount] = useState<AccountSelection>({ newAccount: { username: "", email: "", fullName: "", phone: "", password: "" } });
  const [form, setForm] = useState({
    employeeCode: "",
    dateOfBirth: "",
    idCardNumber: "",
    idCardIssuedDate: "",
    idCardIssuedPlace: "",
    permanentAddress: "",
    currentAddress: "",
    bankAccountNumber: "",
    bankName: "",
    taxCode: "",
    socialInsuranceNumber: "",
    employeeType: "TEACHER",
    positionId: "",
    departmentId: "",
    hireDate: "",
    isManagement: false,
    portraitUrl: ""
  });
  const [positions, setPositions] = useState<PositionResponse[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [touched, setTouched] = useState({ employeeCode: false, dateOfBirth: false, hireDate: false });
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const markTouched = (field: keyof typeof touched) => setTouched((prev) => ({ ...prev, [field]: true }));

  useEffect(() => {
    listPositions().then(setPositions).catch(() => undefined);
    listDepartments().then(setDepartments).catch(() => undefined);
  }, []);

  const employeeCodeInvalid = (touched.employeeCode || submitAttempted) && !form.employeeCode.trim();
  const dateOfBirthInvalid = (touched.dateOfBirth || submitAttempted) && !form.dateOfBirth;
  const hireDateInvalid = (touched.hireDate || submitAttempted) && !form.hireDate;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (!form.employeeCode.trim() || !form.dateOfBirth || !form.hireDate) {
      setError(t("employeeForm.requiredFieldsError"));
      return;
    }
    if (!account.userId && (!account.newAccount?.username || !account.newAccount?.email || !account.newAccount?.fullName)) {
      setError(t("employeeForm.accountError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateEmployeeRequest = {
        userId: account.userId,
        newAccount: account.userId ? undefined : account.newAccount,
        employeeCode: form.employeeCode.trim(),
        dateOfBirth: form.dateOfBirth,
        idCardNumber: form.idCardNumber.trim() || undefined,
        idCardIssuedDate: form.idCardIssuedDate || undefined,
        idCardIssuedPlace: form.idCardIssuedPlace.trim() || undefined,
        permanentAddress: form.permanentAddress.trim() || undefined,
        currentAddress: form.currentAddress.trim() || undefined,
        bankAccountNumber: form.bankAccountNumber.trim() || undefined,
        bankName: form.bankName.trim() || undefined,
        taxCode: form.taxCode.trim() || undefined,
        socialInsuranceNumber: form.socialInsuranceNumber.trim() || undefined,
        employeeType: form.employeeType as CreateEmployeeRequest["employeeType"],
        positionId: form.positionId ? Number(form.positionId) : undefined,
        departmentId: form.departmentId ? Number(form.departmentId) : undefined,
        isManagement: form.isManagement,
        hireDate: form.hireDate,
        portraitUrl: form.portraitUrl || undefined
      };
      const employee = await createEmployee(request);
      onCreated(employee.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("employeeForm.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("employeeForm.modalTitle")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="space-y-2 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("employeeForm.avatarLabel")}</span>
          <AvatarUploadField
            value={form.portraitUrl}
            onChange={(url) => setForm({ ...form, portraitUrl: url })}
            onUpload={(file) => uploadMedia(file, "EMPLOYEE")}
            fallbackName={account.newAccount?.fullName || t("employeeForm.avatarFallback")}
          />
        </div>
        <div className="space-y-2">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("employeeForm.accountSectionTitle")}</span>
          <AccountSelector value={account} onChange={setAccount} submitAttempted={submitAttempted} />
        </div>

        <div className="space-y-3 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("employeeForm.infoSectionTitle")}</span>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>{t("employeeForm.codeLabel")}</label>
              <input
                value={form.employeeCode}
                onChange={(e) => setForm({ ...form, employeeCode: e.target.value })}
                onBlur={() => markTouched("employeeCode")}
                className={`${employeeCodeInvalid ? inputErrorClass : inputClass} font-mono`}
              />
              {employeeCodeInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("employeeForm.codeRequired")}</p>}
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.employeeTypeLabel")}</label>
              <Select value={form.employeeType} onChange={(e) => setForm({ ...form, employeeType: e.target.value })} className={inputClass}>
                <option value="TEACHER">{t("employeeType.TEACHER")}</option>
                <option value="STAFF">{t("employeeType.STAFF")}</option>
                <option value="MANAGER">{t("employeeType.MANAGER")}</option>
              </Select>
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.dobLabel")}</label>
              <DatePicker
                value={form.dateOfBirth}
                onChange={(v) => {
                  setForm({ ...form, dateOfBirth: v });
                  markTouched("dateOfBirth");
                }}
                max={TODAY_ISO}
                hasError={dateOfBirthInvalid}
              />
              {dateOfBirthInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("employeeForm.dobRequired")}</p>}
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.hireDateLabel")}</label>
              <DatePicker
                value={form.hireDate}
                onChange={(v) => {
                  setForm({ ...form, hireDate: v });
                  markTouched("hireDate");
                }}
                hasError={hireDateInvalid}
              />
              {hireDateInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("employeeForm.hireDateRequired")}</p>}
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.positionLabel")}</label>
              <Select value={form.positionId} onChange={(e) => setForm({ ...form, positionId: e.target.value })} className={inputClass}>
                <option value="">{t("employeeForm.positionUnassigned")}</option>
                {positions.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </Select>
              {positions.length === 0 && <p className="text-[10px] text-slate-400 mt-1">{t("employeeForm.positionEmptyHint")}</p>}
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.departmentLabel")}</label>
              <Select value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: e.target.value })} className={inputClass}>
                <option value="">{t("employeeForm.departmentUnassigned")}</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.idCardNumberLabel")}</label>
              <input value={form.idCardNumber} onChange={(e) => setForm({ ...form, idCardNumber: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.idCardIssuedDateLabel")}</label>
              <DatePicker value={form.idCardIssuedDate} onChange={(v) => setForm({ ...form, idCardIssuedDate: v })} max={TODAY_ISO} />
            </div>
            <div className="col-span-2">
              <label className={labelClass}>{t("employeeForm.idCardIssuedPlaceLabel")}</label>
              <input value={form.idCardIssuedPlace} onChange={(e) => setForm({ ...form, idCardIssuedPlace: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.permanentAddressLabel")}</label>
              <input value={form.permanentAddress} onChange={(e) => setForm({ ...form, permanentAddress: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.currentAddressLabel")}</label>
              <input value={form.currentAddress} onChange={(e) => setForm({ ...form, currentAddress: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.bankAccountNumberLabel")}</label>
              <input value={form.bankAccountNumber} onChange={(e) => setForm({ ...form, bankAccountNumber: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.bankNameLabel")}</label>
              <input value={form.bankName} onChange={(e) => setForm({ ...form, bankName: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.taxCodeLabel")}</label>
              <input value={form.taxCode} onChange={(e) => setForm({ ...form, taxCode: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>{t("employeeForm.socialInsuranceNumberLabel")}</label>
              <input value={form.socialInsuranceNumber} onChange={(e) => setForm({ ...form, socialInsuranceNumber: e.target.value })} className={inputClass} />
            </div>
          </div>
          <label className="flex items-center gap-2 text-xs font-semibold text-slate-700">
            <input type="checkbox" checked={form.isManagement} onChange={(e) => setForm({ ...form, isManagement: e.target.checked })} />
            {t("employeeForm.managementExemptCheckbox")}
          </label>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("employeeForm.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Plus className="w-3.5 h-3.5" />
            {submitting ? t("employeeForm.creating") : t("employeeForm.createButton")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
