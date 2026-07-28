import React, { useEffect, useState } from "react";
import { Award, BookOpen, FileText, Save } from "lucide-react";
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
import { employeeStatusLabels, employeeStatusVariants } from "./EmployeeListPanel";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import DatePicker from "@/components/ui/DatePicker";
import AvatarUploadField from "@/components/ui/AvatarUploadField";
import { uploadMedia } from "@/features/lms/api";

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
          <Badge variant={employeeStatusVariants[employee.status]}>{employeeStatusLabels[employee.status]}</Badge>
        </div>

        <div className="flex border-b border-slate-200 pt-1 gap-5 overflow-x-auto">
          {(
            [
              ["profile", "Hồ sơ", FileText],
              ["qualifications", "Bằng cấp", BookOpen],
              ["commendations", "Khen thưởng/Kỷ luật", Award],
              ["contracts", "Hợp đồng", FileText]
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
      showToast("Đã lưu hồ sơ nhân sự thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật hồ sơ thất bại.");
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
          <label className={labelClass}>Ngày sinh *</label>
          <DatePicker
            value={form.dateOfBirth}
            onChange={(v) => {
              setForm({ ...form, dateOfBirth: v });
              setDateOfBirthTouched(true);
            }}
            max={TODAY_ISO}
            hasError={dateOfBirthInvalid}
          />
          {dateOfBirthInvalid && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Ngày sinh.</p>}
        </div>
        <div>
          <label className={labelClass}>Loại nhân sự *</label>
          <select value={form.employeeType} onChange={(e) => setForm({ ...form, employeeType: e.target.value as UpdateEmployeeRequest["employeeType"] })} className={inputClass}>
            <option value="TEACHER">Giáo viên</option>
            <option value="STAFF">Nhân viên</option>
            <option value="MANAGER">Quản lý</option>
          </select>
        </div>
        <div>
          <label className={labelClass}>Số CCCD</label>
          <input value={form.idCardNumber ?? ""} onChange={(e) => setForm({ ...form, idCardNumber: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Ngày cấp CCCD</label>
          <DatePicker value={form.idCardIssuedDate ?? ""} onChange={(v) => setForm({ ...form, idCardIssuedDate: v })} max={TODAY_ISO} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>Nơi cấp CCCD</label>
          <input value={form.idCardIssuedPlace ?? ""} onChange={(e) => setForm({ ...form, idCardIssuedPlace: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Địa chỉ thường trú</label>
          <input value={form.permanentAddress ?? ""} onChange={(e) => setForm({ ...form, permanentAddress: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Địa chỉ hiện tại</label>
          <input value={form.currentAddress ?? ""} onChange={(e) => setForm({ ...form, currentAddress: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Số tài khoản ngân hàng</label>
          <input value={form.bankAccountNumber ?? ""} onChange={(e) => setForm({ ...form, bankAccountNumber: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Ngân hàng</label>
          <input value={form.bankName ?? ""} onChange={(e) => setForm({ ...form, bankName: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Mã số thuế</label>
          <input value={form.taxCode ?? ""} onChange={(e) => setForm({ ...form, taxCode: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Số BHXH</label>
          <input value={form.socialInsuranceNumber ?? ""} onChange={(e) => setForm({ ...form, socialInsuranceNumber: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Chức vụ</label>
          <select
            value={form.positionId ?? ""}
            onChange={(e) => setForm({ ...form, positionId: e.target.value ? Number(e.target.value) : null })}
            className={inputClass}
          >
            <option value="">-- Chưa gán --</option>
            {positions.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className={labelClass}>Phòng ban</label>
          <select
            value={form.departmentId ?? ""}
            onChange={(e) => setForm({ ...form, departmentId: e.target.value ? Number(e.target.value) : undefined })}
            className={inputClass}
          >
            <option value="">-- Chưa gán --</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className={labelClass}>Trạng thái *</label>
          <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as UpdateEmployeeRequest["status"] })} className={inputClass}>
            <option value="ACTIVE">Đang làm việc</option>
            <option value="ON_LEAVE">Đang nghỉ phép dài hạn</option>
            <option value="TERMINATED">Đã nghỉ việc</option>
          </select>
        </div>
        {form.status === "TERMINATED" && (
          <div>
            <label className={labelClass}>Ngày nghỉ việc</label>
            <DatePicker value={form.terminationDate ?? ""} onChange={(v) => setForm({ ...form, terminationDate: v })} />
          </div>
        )}
      </div>
      <div className="flex items-center gap-4">
        <label className="flex items-center gap-2 text-xs font-semibold text-slate-700">
          <input type="checkbox" checked={!!form.isManagement} onChange={(e) => setForm({ ...form, isManagement: e.target.checked })} />
          Miễn trừ chấm công
        </label>
        <label className="flex items-center gap-2 text-xs font-semibold text-slate-700">
          <input type="checkbox" checked={form.isDefaultShiftRequired !== false} onChange={(e) => setForm({ ...form, isDefaultShiftRequired: e.target.checked })} />
          Áp dụng ca mặc định
        </label>
      </div>
      <Button type="submit" variant="primary" size="sm" disabled={saving}>
        <Save className="w-3.5 h-3.5" />
        {saving ? "Đang lưu..." : "Lưu hồ sơ"}
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
  const [items, setItems] = useState<QualificationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ qualificationType: "DEGREE", title: "", issuer: "", issuedDate: "", expiryDate: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
      setError("Vui lòng nhập tên bằng cấp/chứng chỉ.");
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
      load();
      showToast("Đã thêm bằng cấp/chứng chỉ thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Thêm thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <select value={form.qualificationType} onChange={(e) => setForm({ ...form, qualificationType: e.target.value })} className={inputClass}>
            <option value="DEGREE">Bằng cấp</option>
            <option value="PEDAGOGY_CERT">Chứng chỉ nghiệp vụ sư phạm</option>
            <option value="LANGUAGE_CERT">Chứng chỉ ngoại ngữ</option>
            <option value="OTHER">Khác</option>
          </select>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Tên bằng cấp/chứng chỉ *" className={inputClass} />
          <input value={form.issuer} onChange={(e) => setForm({ ...form, issuer: e.target.value })} placeholder="Đơn vị cấp" className={inputClass} />
          <DatePicker value={form.issuedDate} onChange={(v) => setForm({ ...form, issuedDate: v })} max={TODAY_ISO} />
        </div>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Thêm bằng cấp/chứng chỉ"}
        </Button>
      </form>

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa có bằng cấp/chứng chỉ nào.</p>
      ) : (
        <div className="space-y-2">
          {items.map((q) => (
            <div key={q.id} className="border border-slate-200 rounded-lg p-3 text-xs">
              <span className="font-bold text-slate-800">{q.title}</span>
              <span className="text-slate-400 ml-2">({q.issuer || "—"})</span>
              {q.issuedDate && <span className="text-slate-400 ml-2">Cấp: {q.issuedDate}</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function CommendationsTab({ employeeId, showToast }: { employeeId: number; showToast: (msg: string) => void }) {
  const [items, setItems] = useState<CommendationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ recordType: "COMMENDATION", recordDate: "", title: "", amount: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
      setError("Vui lòng nhập tiêu đề và ngày ghi nhận.");
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
      load();
      showToast("Đã ghi nhận khen thưởng/kỷ luật thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Thêm thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
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
              Khen thưởng
            </button>
            <button
              type="button"
              onClick={() => setForm({ ...form, recordType: "DISCIPLINE" })}
              className={`py-1.5 text-xs font-bold rounded-lg border ${
                form.recordType === "DISCIPLINE" ? "bg-rose-50 text-rose-600 border-rose-200" : "bg-white border-slate-200 text-slate-500"
              }`}
            >
              Kỷ luật
            </button>
          </div>
          <DatePicker value={form.recordDate} onChange={(v) => setForm({ ...form, recordDate: v })} max={TODAY_ISO} />
          <input value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value.replace(/[^0-9]/g, "") })} placeholder="Số tiền (nếu có)" className={inputClass} />
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Tiêu đề quyết định *" className={`${inputClass} col-span-2`} />
        </div>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Ghi nhận"}
        </Button>
      </form>

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa có quyết định khen thưởng/kỷ luật nào.</p>
      ) : (
        <div className="space-y-2">
          {items.map((c) => (
            <div key={c.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between">
              <div>
                <Badge variant={c.recordType === "COMMENDATION" ? "success" : "danger"}>{c.recordType === "COMMENDATION" ? "Khen thưởng" : "Kỷ luật"}</Badge>
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
  const [items, setItems] = useState<EmploymentContractResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ contractNumber: "", contractType: "PROBATION", startDate: "", endDate: "", baseSalary: "", salaryType: "MONTHLY", status: "ACTIVE" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
      setError("Vui lòng nhập đủ số hợp đồng, ngày bắt đầu, lương cơ bản.");
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
      load();
      showToast("Đã thêm hợp đồng thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Thêm hợp đồng thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleTerminate = async (contract: EmploymentContractResponse) => {
    if (!window.confirm(`Chấm dứt hợp đồng ${contract.contractNumber}?`)) return;
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
      showToast("Đã chấm dứt hợp đồng thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật thất bại.");
    }
  };

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <input value={form.contractNumber} onChange={(e) => setForm({ ...form, contractNumber: e.target.value })} placeholder="Số hợp đồng *" className={inputClass} />
          <select value={form.contractType} onChange={(e) => setForm({ ...form, contractType: e.target.value })} className={inputClass}>
            <option value="PROBATION">Thử việc</option>
            <option value="FIXED_TERM">Xác định thời hạn</option>
            <option value="INDEFINITE">Không xác định thời hạn</option>
            <option value="SEASONAL">Thời vụ</option>
          </select>
          <div>
            <label className={labelClass}>Ngày bắt đầu *</label>
            <DatePicker value={form.startDate} onChange={(v) => setForm({ ...form, startDate: v })} max={form.endDate || undefined} />
          </div>
          <div>
            <label className={labelClass}>Ngày kết thúc (bỏ trống nếu vô thời hạn)</label>
            <DatePicker value={form.endDate} onChange={(v) => setForm({ ...form, endDate: v })} min={form.startDate || undefined} />
          </div>
          <input value={form.baseSalary} onChange={(e) => setForm({ ...form, baseSalary: e.target.value.replace(/[^0-9]/g, "") })} placeholder="Lương cơ bản *" className={inputClass} />
          <select value={form.salaryType} onChange={(e) => setForm({ ...form, salaryType: e.target.value })} className={inputClass}>
            <option value="MONTHLY">Theo tháng</option>
            <option value="HOURLY">Theo giờ</option>
          </select>
        </div>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Thêm hợp đồng"}
        </Button>
      </form>

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa có hợp đồng nào.</p>
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
                  {c.baseSalary.toLocaleString("vi-VN")}đ/{c.salaryType === "MONTHLY" ? "tháng" : "giờ"}
                </span>
              </div>
              {c.status === "ACTIVE" && (
                <button onClick={() => handleTerminate(c)} className="text-rose-500 hover:text-rose-700 text-[11px] font-semibold">
                  Chấm dứt
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
