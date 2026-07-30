import React, { useEffect, useState } from "react";
import { Plus } from "lucide-react";
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
  const markTouched = (field: keyof typeof touched) => setTouched((t) => ({ ...t, [field]: true }));

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
      setError("Vui lòng điền đủ các trường bắt buộc được đánh dấu đỏ.");
      return;
    }
    if (!account.userId && (!account.newAccount?.username || !account.newAccount?.email || !account.newAccount?.fullName)) {
      setError("Vui lòng chọn tài khoản có sẵn, hoặc điền đủ Username/Email/Họ tên cho tài khoản mới.");
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
      setError(err instanceof ApiError ? err.message : "Tạo hồ sơ nhân sự thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Thêm nhân sự mới" size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="space-y-2 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">Ảnh đại diện</span>
          <AvatarUploadField
            value={form.portraitUrl}
            onChange={(url) => setForm({ ...form, portraitUrl: url })}
            onUpload={(file) => uploadMedia(file, "EMPLOYEE")}
            fallbackName={account.newAccount?.fullName || "Nhân sự"}
          />
        </div>
        <div className="space-y-2">
          <span className="text-[10px] font-bold uppercase text-slate-500">Tài khoản</span>
          <AccountSelector value={account} onChange={setAccount} submitAttempted={submitAttempted} />
        </div>

        <div className="space-y-3 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">Thông tin nhân sự</span>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Mã nhân sự *</label>
              <input
                value={form.employeeCode}
                onChange={(e) => setForm({ ...form, employeeCode: e.target.value })}
                onBlur={() => markTouched("employeeCode")}
                className={`${employeeCodeInvalid ? inputErrorClass : inputClass} font-mono`}
              />
              {employeeCodeInvalid && <p className="text-[10px] text-rose-600 mt-1">Vui lòng nhập Mã nhân sự.</p>}
            </div>
            <div>
              <label className={labelClass}>Loại nhân sự *</label>
              <Select value={form.employeeType} onChange={(e) => setForm({ ...form, employeeType: e.target.value })} className={inputClass}>
                <option value="TEACHER">Giáo viên</option>
                <option value="STAFF">Nhân viên</option>
                <option value="MANAGER">Quản lý</option>
              </Select>
            </div>
            <div>
              <label className={labelClass}>Ngày sinh *</label>
              <DatePicker
                value={form.dateOfBirth}
                onChange={(v) => {
                  setForm({ ...form, dateOfBirth: v });
                  markTouched("dateOfBirth");
                }}
                max={TODAY_ISO}
                hasError={dateOfBirthInvalid}
              />
              {dateOfBirthInvalid && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Ngày sinh.</p>}
            </div>
            <div>
              <label className={labelClass}>Ngày vào làm *</label>
              <DatePicker
                value={form.hireDate}
                onChange={(v) => {
                  setForm({ ...form, hireDate: v });
                  markTouched("hireDate");
                }}
                hasError={hireDateInvalid}
              />
              {hireDateInvalid && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Ngày vào làm.</p>}
            </div>
            <div>
              <label className={labelClass}>Chức vụ</label>
              <Select value={form.positionId} onChange={(e) => setForm({ ...form, positionId: e.target.value })} className={inputClass}>
                <option value="">-- Chưa gán --</option>
                {positions.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </Select>
              {positions.length === 0 && (
                <p className="text-[10px] text-slate-400 mt-1">Chưa có chức vụ nào — tạo tại "Phòng ban & Chức vụ".</p>
              )}
            </div>
            <div>
              <label className={labelClass}>Phòng ban</label>
              <Select value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: e.target.value })} className={inputClass}>
                <option value="">-- Chưa gán --</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <label className={labelClass}>Số CCCD</label>
              <input value={form.idCardNumber} onChange={(e) => setForm({ ...form, idCardNumber: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Ngày cấp CCCD</label>
              <DatePicker value={form.idCardIssuedDate} onChange={(v) => setForm({ ...form, idCardIssuedDate: v })} max={TODAY_ISO} />
            </div>
            <div className="col-span-2">
              <label className={labelClass}>Nơi cấp CCCD</label>
              <input value={form.idCardIssuedPlace} onChange={(e) => setForm({ ...form, idCardIssuedPlace: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Địa chỉ thường trú</label>
              <input value={form.permanentAddress} onChange={(e) => setForm({ ...form, permanentAddress: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Địa chỉ hiện tại</label>
              <input value={form.currentAddress} onChange={(e) => setForm({ ...form, currentAddress: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Số tài khoản ngân hàng</label>
              <input value={form.bankAccountNumber} onChange={(e) => setForm({ ...form, bankAccountNumber: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Ngân hàng</label>
              <input value={form.bankName} onChange={(e) => setForm({ ...form, bankName: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Mã số thuế</label>
              <input value={form.taxCode} onChange={(e) => setForm({ ...form, taxCode: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Số BHXH</label>
              <input value={form.socialInsuranceNumber} onChange={(e) => setForm({ ...form, socialInsuranceNumber: e.target.value })} className={inputClass} />
            </div>
          </div>
          <label className="flex items-center gap-2 text-xs font-semibold text-slate-700">
            <input type="checkbox" checked={form.isManagement} onChange={(e) => setForm({ ...form, isManagement: e.target.checked })} />
            Miễn trừ chấm công (is_management)
          </label>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Plus className="w-3.5 h-3.5" />
            {submitting ? "Đang tạo..." : "Tạo hồ sơ nhân sự"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
