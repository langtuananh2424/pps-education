import React, { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import AccountSelector, { AccountSelection } from "@/features/system-admin/components/AccountSelector";
import { createParent, createStudent, CreateStudentRequest, linkParent, listSites, SiteOption } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";

const TODAY_ISO = new Date().toISOString().slice(0, 10);

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface StudentFormModalProps {
  onClose: () => void;
  onCreated: (id: number) => void;
}

/** UC-13 Main Flow bước 1-3: khởi tạo hồ sơ học sinh, kèm tài khoản mới hoặc gán tài khoản có sẵn — có thể liên kết luôn phụ huynh. */
export default function StudentFormModal({ onClose, onCreated }: StudentFormModalProps) {
  const [account, setAccount] = useState<AccountSelection>({ newAccount: { username: "", email: "", fullName: "", phone: "", password: "" } });
  const [sites, setSites] = useState<SiteOption[]>([]);
  const [form, setForm] = useState({
    studentCode: "",
    dateOfBirth: "",
    gender: "",
    primarySiteId: "",
    originalSchool: "",
    originalClass: "",
    enrollmentDate: "",
    notes: ""
  });

  const [addParent, setAddParent] = useState(false);
  const [parentAccount, setParentAccount] = useState<AccountSelection>({ newAccount: { username: "", email: "", fullName: "", phone: "", password: "" } });
  const [parentInfo, setParentInfo] = useState({ relationship: "MOTHER", isPrimaryContact: true, isFinancialResponsible: true });

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [touched, setTouched] = useState({ studentCode: false, dateOfBirth: false, enrollmentDate: false });
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const markTouched = (field: keyof typeof touched) => setTouched((t) => ({ ...t, [field]: true }));

  const studentCodeInvalid = (touched.studentCode || submitAttempted) && !form.studentCode.trim();
  const dateOfBirthInvalid = (touched.dateOfBirth || submitAttempted) && !form.dateOfBirth;
  const enrollmentDateInvalid = (touched.enrollmentDate || submitAttempted) && !form.enrollmentDate;

  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (!form.studentCode.trim() || !form.dateOfBirth || !form.enrollmentDate) {
      setError("Vui lòng điền đủ các trường bắt buộc được đánh dấu đỏ.");
      return;
    }
    if (!account.userId && (!account.newAccount?.username || !account.newAccount?.email || !account.newAccount?.fullName)) {
      setError("Vui lòng chọn tài khoản có sẵn hoặc điền đủ thông tin tài khoản mới cho học sinh.");
      return;
    }
    if (addParent && !parentAccount.userId && (!parentAccount.newAccount?.username || !parentAccount.newAccount?.email || !parentAccount.newAccount?.fullName)) {
      setError("Vui lòng chọn tài khoản có sẵn hoặc điền đủ thông tin tài khoản mới cho phụ huynh.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateStudentRequest = {
        userId: account.userId,
        newAccount: account.userId ? undefined : account.newAccount,
        studentCode: form.studentCode.trim(),
        dateOfBirth: form.dateOfBirth,
        gender: (form.gender || undefined) as CreateStudentRequest["gender"],
        primarySiteId: form.primarySiteId ? Number(form.primarySiteId) : undefined,
        originalSchool: form.originalSchool.trim() || undefined,
        originalClass: form.originalClass.trim() || undefined,
        enrollmentDate: form.enrollmentDate,
        notes: form.notes.trim() || undefined
      };
      const student = await createStudent(request);

      if (addParent) {
        const parent = await createParent({
          userId: parentAccount.userId,
          newAccount: parentAccount.userId ? undefined : parentAccount.newAccount
        });
        await linkParent(student.id, {
          parentId: parent.id,
          relationship: parentInfo.relationship as "FATHER" | "MOTHER" | "GUARDIAN" | "OTHER",
          isPrimaryContact: parentInfo.isPrimaryContact,
          isFinancialResponsible: parentInfo.isFinancialResponsible
        });
      }

      onCreated(student.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo hồ sơ học sinh thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Thêm học sinh mới (UC-13)" size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="space-y-2">
          <span className="text-[10px] font-bold uppercase text-slate-500">Tài khoản học sinh</span>
          <AccountSelector value={account} onChange={setAccount} submitAttempted={submitAttempted} />
        </div>

        <div className="space-y-3 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">Thông tin học sinh</span>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Mã học sinh *</label>
              <input
                value={form.studentCode}
                onChange={(e) => setForm({ ...form, studentCode: e.target.value })}
                onBlur={() => markTouched("studentCode")}
                className={`${studentCodeInvalid ? inputErrorClass : inputClass} font-mono`}
              />
              {studentCodeInvalid && <p className="text-[10px] text-rose-600 mt-1">Vui lòng nhập Mã học sinh.</p>}
            </div>
            <div>
              <label className={labelClass}>Giới tính</label>
              <select value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value })} className={inputClass}>
                <option value="">-- Chưa rõ --</option>
                <option value="MALE">Nam</option>
                <option value="FEMALE">Nữ</option>
                <option value="OTHER">Khác</option>
              </select>
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
              <label className={labelClass}>Ngày nhập học *</label>
              <DatePicker
                value={form.enrollmentDate}
                onChange={(v) => {
                  setForm({ ...form, enrollmentDate: v });
                  markTouched("enrollmentDate");
                }}
                hasError={enrollmentDateInvalid}
              />
              {enrollmentDateInvalid && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Ngày nhập học.</p>}
            </div>
            <div>
              <label className={labelClass}>Điểm trường chính</label>
              <select value={form.primarySiteId} onChange={(e) => setForm({ ...form, primarySiteId: e.target.value })} className={inputClass}>
                <option value="">-- Chưa gán --</option>
                {sites.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Trường gốc</label>
              <input value={form.originalSchool} onChange={(e) => setForm({ ...form, originalSchool: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Lớp gốc</label>
              <input value={form.originalClass} onChange={(e) => setForm({ ...form, originalClass: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Ghi chú</label>
              <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} className={inputClass} />
            </div>
          </div>
        </div>

        <div className="border-t border-slate-100 pt-4 space-y-3">
          <label className="flex items-center gap-2 text-xs font-bold text-slate-700 uppercase">
            <input type="checkbox" checked={addParent} onChange={(e) => setAddParent(e.target.checked)} />
            Liên kết Phụ huynh ngay
          </label>
          {addParent && (
            <div className="space-y-3 bg-slate-50/60 border border-slate-200 rounded-xl p-3">
              <AccountSelector value={parentAccount} onChange={setParentAccount} submitAttempted={submitAttempted} />
              <div className="grid grid-cols-3 gap-2 items-end">
                <div>
                  <label className={labelClass}>Quan hệ</label>
                  <select value={parentInfo.relationship} onChange={(e) => setParentInfo({ ...parentInfo, relationship: e.target.value })} className={inputClass}>
                    <option value="FATHER">Bố</option>
                    <option value="MOTHER">Mẹ</option>
                    <option value="GUARDIAN">Người giám hộ</option>
                    <option value="OTHER">Khác</option>
                  </select>
                </div>
                <label className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-700 pb-2.5">
                  <input type="checkbox" checked={parentInfo.isPrimaryContact} onChange={(e) => setParentInfo({ ...parentInfo, isPrimaryContact: e.target.checked })} />
                  Người liên hệ chính
                </label>
                <label className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-700 pb-2.5">
                  <input type="checkbox" checked={parentInfo.isFinancialResponsible} onChange={(e) => setParentInfo({ ...parentInfo, isFinancialResponsible: e.target.checked })} />
                  Chịu trách nhiệm tài chính
                </label>
              </div>
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Plus className="w-3.5 h-3.5" />
            {submitting ? "Đang tạo..." : "Tạo hồ sơ học sinh"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
