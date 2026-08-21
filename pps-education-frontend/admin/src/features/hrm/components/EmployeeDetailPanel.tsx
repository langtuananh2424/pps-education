import React, { useEffect, useState } from "react";
import { Award, BookOpen, FileText, Plus, Save } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import {
  addCommendation,
  addContract,
  addQualification,
  CommendationResponse,
  DepartmentResponse,
  EmployeeResponse,
  EmploymentContractResponse,
  listCommendations,
  listContracts,
  listDepartments,
  listPositions,
  listQualifications,
  PositionResponse,
  QualificationResponse,
  updateContract,
  updateEmployee,
  UpdateEmployeeRequest
} from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { employeeStatusLabel, employeeStatusVariants } from "./EmployeeListPanel";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";
import DatePicker from "@/components/ui/DatePicker";
import AvatarUploadField from "@/components/ui/AvatarUploadField";
import { uploadMedia } from "@/features/lms/api";
import Select from "@/components/ui/Select";

const TODAY_ISO = new Date().toISOString().slice(0, 10);

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

type Tab = "profile" | "qualifications" | "commendations" | "contracts";

interface EmployeeDetailPanelProps {
  employee: EmployeeResponse;
  onChanged: () => void;
}

export default function EmployeeDetailPanel({ employee, onChanged }: EmployeeDetailPanelProps) {
  const { t } = useTranslation("hrm-employees");
  const [tab, setTab] = useState<Tab>("profile");
  const { message: toastMessage, showToast } = useToast();

  return (
    <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col">
      <div className="p-5 border-b border-slate-200 space-y-3 bg-slate-50/20">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-brand-red bg-orange-50 border border-orange-100 px-2 py-0.5 rounded-md">
              {employee.employeeCode}
            </span>
            <h2 className="text-sm font-bold text-slate-800 mt-1">{employee.fullName}</h2>
          </div>
          <Badge variant={employeeStatusVariants[employee.status]}>{employeeStatusLabel(t, employee.status)}</Badge>
        </div>

        <div className="flex border-b border-slate-200 pt-1 gap-5 overflow-x-auto">
          {(
            [
              ["profile", t("employeeDetail.tabs.profile"), FileText],
              ["qualifications", t("employeeDetail.tabs.qualifications"), BookOpen],
              ["commendations", t("employeeDetail.tabs.commendations"), Award],
              ["contracts", t("employeeDetail.tabs.contracts"), FileText]
            ] as const
          ).map(([key, label, Icon]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all whitespace-nowrap ${
                tab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 p-5 overflow-y-auto max-h-[560px]">
        {tab === "profile" && <ProfileTab employee={employee} onChanged={onChanged} showToast={showToast} />}
        {tab === "qualifications" && <QualificationsTab employeeId={employee.id} showToast={showToast} />}
        {tab === "commendations" && <CommendationsTab employeeId={employee.id} showToast={showToast} />}
        {tab === "contracts" && <ContractsTab employeeId={employee.id} showToast={showToast} />}
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

function ProfileTab({
  employee,
  onChanged,
  showToast
}: {
  employee: EmployeeResponse;
  onChanged: () => void;
  showToast: (msg: string) => void;
}) {
  const { t } = useTranslation("hrm-employees");
  const [form, setForm] = useState<UpdateEmployeeRequest>(() => toForm(employee));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dateOfBirthTouched, setDateOfBirthTouched] = useState(false);
  const dateOfBirthInvalid = dateOfBirthTouched && !form.dateOfBirth;
  const [positions, setPositions] = useState<PositionResponse[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);

  useEffect(() => setForm(toForm(employee)), [employee]);

  useEffect(() => {
    listPositions().then(setPositions).catch(() => undefined);
    listDepartments().then(setDepartments).catch(() => undefined);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setDateOfBirthTouched(true);
    if (!form.dateOfBirth) return;
    setSaving(true);
    setError(null);
    try {
      await updateEmployee(employee.id, form);
      onChanged();
      showToast(t("employeeDetail.profile.savedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("employeeDetail.profile.saveError"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <AvatarUploadField
        value={form.portraitUrl}
        onChange={(url) => setForm({ ...form, portraitUrl: url })}
        onUpload={(file) => uploadMedia(file, "EMPLOYEE")}
        fallbackName={employee.fullName}
      />

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.dobLabel")}</label>
          <DatePicker
            value={form.dateOfBirth}
            onChange={(v) => {
              setForm({ ...form, dateOfBirth: v });
              setDateOfBirthTouched(true);
            }}
            max={TODAY_ISO}
            hasError={dateOfBirthInvalid}
          />
          {dateOfBirthInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("employeeDetail.profile.dobRequired")}</p>}
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.employeeTypeLabel")}</label>
          <Select value={form.employeeType} onChange={(e) => setForm({ ...form, employeeType: e.target.value as UpdateEmployeeRequest["employeeType"] })} className={inputClass}>
            <option value="TEACHER">{t("employeeType.TEACHER")}</option>
            <option value="STAFF">{t("employeeType.STAFF")}</option>
            <option value="MANAGER">{t("employeeType.MANAGER")}</option>
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.idCardNumberLabel")}</label>
          <input value={form.idCardNumber ?? ""} onChange={(e) => setForm({ ...form, idCardNumber: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.idCardIssuedDateLabel")}</label>
          <DatePicker value={form.idCardIssuedDate ?? ""} onChange={(v) => setForm({ ...form, idCardIssuedDate: v })} max={TODAY_ISO} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>{t("employeeDetail.profile.idCardIssuedPlaceLabel")}</label>
          <input value={form.idCardIssuedPlace ?? ""} onChange={(e) => setForm({ ...form, idCardIssuedPlace: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.permanentAddressLabel")}</label>
          <input value={form.permanentAddress ?? ""} onChange={(e) => setForm({ ...form, permanentAddress: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.currentAddressLabel")}</label>
          <input value={form.currentAddress ?? ""} onChange={(e) => setForm({ ...form, currentAddress: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.bankAccountNumberLabel")}</label>
          <input value={form.bankAccountNumber ?? ""} onChange={(e) => setForm({ ...form, bankAccountNumber: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.bankNameLabel")}</label>
          <input value={form.bankName ?? ""} onChange={(e) => setForm({ ...form, bankName: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.taxCodeLabel")}</label>
          <input value={form.taxCode ?? ""} onChange={(e) => setForm({ ...form, taxCode: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.socialInsuranceNumberLabel")}</label>
          <input value={form.socialInsuranceNumber ?? ""} onChange={(e) => setForm({ ...form, socialInsuranceNumber: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.positionLabel")}</label>
          <Select
            value={form.positionId ?? ""}
            onChange={(e) => setForm({ ...form, positionId: e.target.value ? Number(e.target.value) : null })}
            className={inputClass}
          >
            <option value="">{t("employeeDetail.profile.positionUnassigned")}</option>
            {positions.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.departmentLabel")}</label>
          <Select
            value={form.departmentId ?? ""}
            onChange={(e) => setForm({ ...form, departmentId: e.target.value ? Number(e.target.value) : undefined })}
            className={inputClass}
          >
            <option value="">{t("employeeDetail.profile.departmentUnassigned")}</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("employeeDetail.profile.statusLabel")}</label>
          <Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as UpdateEmployeeRequest["status"] })} className={inputClass}>
            <option value="ACTIVE">{t("employeeStatus.ACTIVE")}</option>
            <option value="ON_LEAVE">{t("employeeStatus.ON_LEAVE")}</option>
            <option value="TERMINATED">{t("employeeStatus.TERMINATED")}</option>
          </Select>
        </div>
        {form.status === "TERMINATED" && (
          <div>
            <label className={labelClass}>{t("employeeDetail.profile.terminationDateLabel")}</label>
            <DatePicker value={form.terminationDate ?? ""} onChange={(v) => setForm({ ...form, terminationDate: v })} />
          </div>
        )}
      </div>
      <div className="flex items-center gap-4">
        <label className="flex items-center gap-2 text-xs font-semibold text-slate-700">
          <input type="checkbox" checked={!!form.isManagement} onChange={(e) => setForm({ ...form, isManagement: e.target.checked })} />
          {t("employeeDetail.profile.managementExemptCheckbox")}
        </label>
        <label className="flex items-center gap-2 text-xs font-semibold text-slate-700">
          <input type="checkbox" checked={form.isDefaultShiftRequired !== false} onChange={(e) => setForm({ ...form, isDefaultShiftRequired: e.target.checked })} />
          {t("employeeDetail.profile.defaultShiftCheckbox")}
        </label>
      </div>
      <Button type="submit" variant="primary" size="sm" disabled={saving}>
        <Save className="w-3.5 h-3.5" />
        {saving ? t("employeeDetail.profile.saving") : t("employeeDetail.profile.saveButton")}
      </Button>
    </form>
  );
}

function toForm(e: EmployeeResponse): UpdateEmployeeRequest {
  return {
    dateOfBirth: e.dateOfBirth,
    idCardNumber: e.idCardNumber ?? undefined,
    idCardIssuedDate: e.idCardIssuedDate ?? undefined,
    idCardIssuedPlace: e.idCardIssuedPlace ?? undefined,
    permanentAddress: e.permanentAddress ?? undefined,
    currentAddress: e.currentAddress ?? undefined,
    bankAccountNumber: e.bankAccountNumber ?? undefined,
    bankName: e.bankName ?? undefined,
    taxCode: e.taxCode ?? undefined,
    socialInsuranceNumber: e.socialInsuranceNumber ?? undefined,
    employeeType: e.employeeType,
    positionId: e.positionId ?? null,
    departmentId: e.departmentId ?? undefined,
    isManagement: e.isManagement,
    isDefaultShiftRequired: e.isDefaultShiftRequired,
    status: e.status,
    terminationDate: e.terminationDate ?? undefined,
    portraitUrl: e.portraitUrl ?? undefined
  };
}

function QualificationsTab({ employeeId, showToast }: { employeeId: number; showToast: (msg: string) => void }) {
  const { t } = useTranslation("hrm-employees");
  const [items, setItems] = useState<QualificationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ qualificationType: "DEGREE", title: "", issuer: "", issuedDate: "", expiryDate: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  const load = () => {
    setLoading(true);
    listQualifications(employeeId)
      .then(setItems)
      .finally(() => setLoading(false));
  };
  useEffect(load, [employeeId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim()) {
      setError(t("employeeDetail.qualifications.titleRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await addQualification(employeeId, {
        qualificationType: form.qualificationType as QualificationResponse["qualificationType"],
        title: form.title.trim(),
        issuer: form.issuer.trim() || undefined,
        issuedDate: form.issuedDate || undefined,
        expiryDate: form.expiryDate || undefined
      });
      setForm({ qualificationType: "DEGREE", title: "", issuer: "", issuedDate: "", expiryDate: "" });
      setShowForm(false);
      load();
      showToast(t("employeeDetail.qualifications.addedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("employeeDetail.qualifications.addError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("employeeDetail.qualifications.sectionTitle", { count: items.length })}</span>
        <Button size="sm" variant="secondary" onClick={() => setShowForm(true)}>
          <Plus className="w-3.5 h-3.5" />
          {t("employeeDetail.qualifications.addButton")}
        </Button>
      </div>

      <Modal open={showForm} onClose={() => setShowForm(false)} title={t("employeeDetail.qualifications.modalTitle")}>
        <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
          <div className="grid grid-cols-2 gap-3">
            <Select value={form.qualificationType} onChange={(e) => setForm({ ...form, qualificationType: e.target.value })} className={inputClass}>
              <option value="DEGREE">{t("qualificationType.DEGREE")}</option>
              <option value="PEDAGOGY_CERT">{t("qualificationType.PEDAGOGY_CERT")}</option>
              <option value="LANGUAGE_CERT">{t("qualificationType.LANGUAGE_CERT")}</option>
              <option value="OTHER">{t("qualificationType.OTHER")}</option>
            </Select>
            <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder={t("employeeDetail.qualifications.titlePlaceholder")} className={inputClass} />
            <input value={form.issuer} onChange={(e) => setForm({ ...form, issuer: e.target.value })} placeholder={t("employeeDetail.qualifications.issuerPlaceholder")} className={inputClass} />
            <DatePicker value={form.issuedDate} onChange={(v) => setForm({ ...form, issuedDate: v })} max={TODAY_ISO} />
          </div>
          <Button type="submit" size="sm" variant="primary" disabled={submitting}>
            {submitting ? t("employeeDetail.qualifications.saving") : t("employeeDetail.qualifications.submitButton")}
          </Button>
        </form>
      </Modal>

      {loading ? (
        <p className="text-xs text-slate-500">{t("employeeDetail.qualifications.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("employeeDetail.qualifications.empty")}</p>
      ) : (
        <div className="space-y-2">
          {items.map((q) => (
            <div key={q.id} className="border border-slate-200 rounded-lg p-3 text-xs">
              <span className="font-bold text-slate-800">{q.title}</span>
              <span className="text-slate-400 ml-2">({q.issuer || "—"})</span>
              {q.issuedDate && (
                <span className="text-slate-400 ml-2">
                  {t("employeeDetail.qualifications.issuedPrefix")} {q.issuedDate}
                </span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function CommendationsTab({ employeeId, showToast }: { employeeId: number; showToast: (msg: string) => void }) {
  const { t } = useTranslation("hrm-employees");
  const [items, setItems] = useState<CommendationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ recordType: "COMMENDATION", recordDate: "", title: "", amount: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  const load = () => {
    setLoading(true);
    listCommendations(employeeId)
      .then(setItems)
      .finally(() => setLoading(false));
  };
  useEffect(load, [employeeId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim() || !form.recordDate) {
      setError(t("employeeDetail.commendations.requiredError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await addCommendation(employeeId, {
        recordType: form.recordType as CommendationResponse["recordType"],
        recordDate: form.recordDate,
        title: form.title.trim(),
        amount: form.amount ? Number(form.amount) : undefined
      });
      setForm({ recordType: "COMMENDATION", recordDate: "", title: "", amount: "" });
      setShowForm(false);
      load();
      showToast(t("employeeDetail.commendations.addedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("employeeDetail.commendations.addError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("employeeDetail.commendations.sectionTitle", { count: items.length })}</span>
        <Button size="sm" variant="secondary" onClick={() => setShowForm(true)}>
          <Plus className="w-3.5 h-3.5" />
          {t("employeeDetail.commendations.addButton")}
        </Button>
      </div>

      <Modal open={showForm} onClose={() => setShowForm(false)} title={t("employeeDetail.commendations.modalTitle")}>
        <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
          <div className="grid grid-cols-2 gap-3">
            <div className="grid grid-cols-2 gap-2 col-span-2">
              <button
                type="button"
                onClick={() => setForm({ ...form, recordType: "COMMENDATION" })}
                className={`py-1.5 text-xs font-bold rounded-lg border ${
                  form.recordType === "COMMENDATION" ? "bg-emerald-50 text-emerald-600 border-emerald-200" : "bg-white border-slate-200 text-slate-500"
                }`}
              >
                {t("commendationType.COMMENDATION")}
              </button>
              <button
                type="button"
                onClick={() => setForm({ ...form, recordType: "DISCIPLINE" })}
                className={`py-1.5 text-xs font-bold rounded-lg border ${
                  form.recordType === "DISCIPLINE" ? "bg-rose-50 text-rose-600 border-rose-200" : "bg-white border-slate-200 text-slate-500"
                }`}
              >
                {t("commendationType.DISCIPLINE")}
              </button>
            </div>
            <DatePicker value={form.recordDate} onChange={(v) => setForm({ ...form, recordDate: v })} max={TODAY_ISO} />
            <input
              value={form.amount}
              onChange={(e) => setForm({ ...form, amount: e.target.value.replace(/[^0-9]/g, "") })}
              placeholder={t("employeeDetail.commendations.amountPlaceholder")}
              className={inputClass}
            />
            <input
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              placeholder={t("employeeDetail.commendations.titlePlaceholder")}
              className={`${inputClass} col-span-2`}
            />
          </div>
          <Button type="submit" size="sm" variant="primary" disabled={submitting}>
            {submitting ? t("employeeDetail.commendations.saving") : t("employeeDetail.commendations.submitButton")}
          </Button>
        </form>
      </Modal>

      {loading ? (
        <p className="text-xs text-slate-500">{t("employeeDetail.commendations.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("employeeDetail.commendations.empty")}</p>
      ) : (
        <div className="space-y-2">
          {items.map((c) => (
            <div key={c.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between">
              <div>
                <Badge variant={c.recordType === "COMMENDATION" ? "success" : "danger"}>
                  {c.recordType === "COMMENDATION" ? t("commendationType.COMMENDATION") : t("commendationType.DISCIPLINE")}
                </Badge>
                <span className="font-bold text-slate-800 ml-2">{c.title}</span>
              </div>
              <span className="text-slate-400">{c.recordDate}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function ContractsTab({ employeeId, showToast }: { employeeId: number; showToast: (msg: string) => void }) {
  const { t } = useTranslation("hrm-employees");
  const [items, setItems] = useState<EmploymentContractResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ contractNumber: "", contractType: "PROBATION", startDate: "", endDate: "", baseSalary: "", salaryType: "MONTHLY", status: "ACTIVE" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const { confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listContracts(employeeId)
      .then(setItems)
      .finally(() => setLoading(false));
  };
  useEffect(load, [employeeId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.contractNumber.trim() || !form.startDate || !form.baseSalary) {
      setError(t("employeeDetail.contracts.requiredError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await addContract(employeeId, {
        contractNumber: form.contractNumber.trim(),
        contractType: form.contractType as EmploymentContractResponse["contractType"],
        startDate: form.startDate,
        endDate: form.endDate || undefined,
        baseSalary: Number(form.baseSalary),
        salaryType: form.salaryType as EmploymentContractResponse["salaryType"],
        status: form.status as EmploymentContractResponse["status"]
      });
      setForm({ contractNumber: "", contractType: "PROBATION", startDate: "", endDate: "", baseSalary: "", salaryType: "MONTHLY", status: "ACTIVE" });
      setShowForm(false);
      load();
      showToast(t("employeeDetail.contracts.addedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("employeeDetail.contracts.addError"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleTerminate = async (contract: EmploymentContractResponse) => {
    if (!(await confirmDialog(t("employeeDetail.contracts.terminateConfirm", { number: contract.contractNumber }), { danger: true }))) return;
    try {
      await updateContract(employeeId, contract.id, {
        contractType: contract.contractType,
        startDate: contract.startDate,
        endDate: contract.endDate ?? undefined,
        baseSalary: contract.baseSalary,
        salaryType: contract.salaryType,
        status: "TERMINATED"
      });
      load();
      showToast(t("employeeDetail.contracts.terminatedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("employeeDetail.contracts.updateError"));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("employeeDetail.contracts.sectionTitle", { count: items.length })}</span>
        <Button size="sm" variant="secondary" onClick={() => setShowForm(true)}>
          <Plus className="w-3.5 h-3.5" />
          {t("employeeDetail.contracts.addButton")}
        </Button>
      </div>

      <Modal open={showForm} onClose={() => setShowForm(false)} title={t("employeeDetail.contracts.modalTitle")}>
        <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
          <div className="grid grid-cols-2 gap-3">
            <input
              value={form.contractNumber}
              onChange={(e) => setForm({ ...form, contractNumber: e.target.value })}
              placeholder={t("employeeDetail.contracts.numberPlaceholder")}
              className={inputClass}
            />
            <Select value={form.contractType} onChange={(e) => setForm({ ...form, contractType: e.target.value })} className={inputClass}>
              <option value="PROBATION">{t("contractType.PROBATION")}</option>
              <option value="FIXED_TERM">{t("contractType.FIXED_TERM")}</option>
              <option value="INDEFINITE">{t("contractType.INDEFINITE")}</option>
              <option value="SEASONAL">{t("contractType.SEASONAL")}</option>
            </Select>
            <div>
              <label className={labelClass}>{t("employeeDetail.contracts.startDateLabel")}</label>
              <DatePicker value={form.startDate} onChange={(v) => setForm({ ...form, startDate: v })} max={form.endDate || undefined} />
            </div>
            <div>
              <label className={labelClass}>{t("employeeDetail.contracts.endDateLabel")}</label>
              <DatePicker value={form.endDate} onChange={(v) => setForm({ ...form, endDate: v })} min={form.startDate || undefined} />
            </div>
            <input
              value={form.baseSalary}
              onChange={(e) => setForm({ ...form, baseSalary: e.target.value.replace(/[^0-9]/g, "") })}
              placeholder={t("employeeDetail.contracts.baseSalaryPlaceholder")}
              className={inputClass}
            />
            <Select value={form.salaryType} onChange={(e) => setForm({ ...form, salaryType: e.target.value })} className={inputClass}>
              <option value="MONTHLY">{t("salaryType.MONTHLY")}</option>
              <option value="HOURLY">{t("salaryType.HOURLY")}</option>
            </Select>
          </div>
          <Button type="submit" size="sm" variant="primary" disabled={submitting}>
            {submitting ? t("employeeDetail.contracts.saving") : t("employeeDetail.contracts.submitButton")}
          </Button>
        </form>
      </Modal>

      {loading ? (
        <p className="text-xs text-slate-500">{t("employeeDetail.contracts.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("employeeDetail.contracts.empty")}</p>
      ) : (
        <div className="space-y-2">
          {items.map((c) => (
            <div key={c.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between">
              <div>
                <span className="font-mono font-bold text-slate-800">{c.contractNumber}</span>
                <Badge variant={c.status === "ACTIVE" ? "success" : c.status === "TERMINATED" ? "danger" : "neutral"} className="ml-2">
                  {c.status}
                </Badge>
                <span className="text-slate-400 ml-2">
                  {c.baseSalary.toLocaleString("vi-VN")}đ/{c.salaryType === "MONTHLY" ? t("employeeDetail.contracts.monthUnit") : t("employeeDetail.contracts.hourUnit")}
                </span>
              </div>
              {c.status === "ACTIVE" && (
                <button onClick={() => handleTerminate(c)} className="text-rose-500 hover:text-rose-700 text-[11px] font-semibold">
                  {t("employeeDetail.contracts.terminateButton")}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
