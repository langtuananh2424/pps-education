import React, { useEffect, useState } from "react";
import { ArrowRightLeft, FileText, History, Save, UserPlus, Users, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import AccountSelector, { AccountSelection } from "@/features/system-admin/components/AccountSelector";
import {
  createParent,
  linkParent,
  listSites,
  listStatusHistory,
  listStudentParents,
  listTransferHistory,
  ParentStudentResponse,
  recordTransfer,
  RecordTransferRequest,
  searchParents,
  SiteOption,
  StudentResponse,
  StudentStatusHistoryResponse,
  StudentTransferHistoryResponse,
  unlinkParent,
  updateStudent,
  updateStudentStatus,
  UpdateStudentRequest
} from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { studentStatusLabels, studentStatusVariants } from "./StudentListPanel";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import DatePicker from "@/components/ui/DatePicker";

const TODAY_ISO = new Date().toISOString().slice(0, 10);

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

type Tab = "profile" | "parents" | "transfer" | "status";

interface StudentDetailPanelProps {
  student: StudentResponse;
  onChanged: () => void;
}

export default function StudentDetailPanel({ student, onChanged }: StudentDetailPanelProps) {
  const [tab, setTab] = useState<Tab>("profile");
  const { message: toastMessage, showToast } = useToast();

  return (
    <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col">
      <div className="p-5 border-b border-slate-200 space-y-3 bg-slate-50/20">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-brand-red bg-orange-50 border border-orange-100 px-2 py-0.5 rounded-md">
              {student.studentCode}
            </span>
            <h2 className="text-sm font-bold text-slate-800 mt-1">{student.fullName}</h2>
          </div>
          <Badge variant={studentStatusVariants[student.status]}>{studentStatusLabels[student.status]}</Badge>
        </div>

        <div className="flex border-b border-slate-200 pt-1 gap-5 overflow-x-auto">
          {(
            [
              ["profile", "Hồ sơ", FileText],
              ["parents", "Phụ huynh", Users],
              ["transfer", "Chuyển lớp/điểm trường", ArrowRightLeft],
              ["status", "Trạng thái học tập", History]
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
        {tab === "profile" && <ProfileTab student={student} onChanged={onChanged} showToast={showToast} />}
        {tab === "parents" && <ParentsTab studentId={student.id} showToast={showToast} />}
        {tab === "transfer" && <TransferTab studentId={student.id} onChanged={onChanged} showToast={showToast} />}
        {tab === "status" && <StatusTab student={student} onChanged={onChanged} showToast={showToast} />}
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

function ProfileTab({
  student,
  onChanged,
  showToast
}: {
  student: StudentResponse;
  onChanged: () => void;
  showToast: (msg: string) => void;
}) {
  const [form, setForm] = useState<UpdateStudentRequest>(() => toForm(student));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dateOfBirthTouched, setDateOfBirthTouched] = useState(false);
  const dateOfBirthInvalid = dateOfBirthTouched && !form.dateOfBirth;

  useEffect(() => setForm(toForm(student)), [student]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setDateOfBirthTouched(true);
    if (!form.dateOfBirth) return;
    setSaving(true);
    setError(null);
    try {
      await updateStudent(student.id, form);
      onChanged();
      showToast("Đã lưu hồ sơ học sinh thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật hồ sơ thất bại.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
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
          <label className={labelClass}>Giới tính</label>
          <select value={form.gender ?? ""} onChange={(e) => setForm({ ...form, gender: (e.target.value || undefined) as UpdateStudentRequest["gender"] })} className={inputClass}>
            <option value="">-- Chưa rõ --</option>
            <option value="MALE">Nam</option>
            <option value="FEMALE">Nữ</option>
            <option value="OTHER">Khác</option>
          </select>
        </div>
        <div>
          <label className={labelClass}>Trường gốc</label>
          <input value={form.originalSchool ?? ""} onChange={(e) => setForm({ ...form, originalSchool: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Lớp gốc</label>
          <input value={form.originalClass ?? ""} onChange={(e) => setForm({ ...form, originalClass: e.target.value })} className={inputClass} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>URL ảnh đại diện</label>
          <input value={form.portraitUrl ?? ""} onChange={(e) => setForm({ ...form, portraitUrl: e.target.value })} className={inputClass} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>Ghi chú</label>
          <textarea value={form.notes ?? ""} onChange={(e) => setForm({ ...form, notes: e.target.value })} rows={2} className={inputClass} />
        </div>
      </div>
      <p className="text-[10px] text-slate-400 italic">
        Điểm trường chính chỉ đổi được qua tab "Chuyển lớp/điểm trường"; trạng thái học tập đổi qua tab "Trạng thái học tập".
      </p>
      <Button type="submit" variant="primary" size="sm" disabled={saving}>
        <Save className="w-3.5 h-3.5" />
        {saving ? "Đang lưu..." : "Lưu hồ sơ"}
      </Button>
    </form>
  );
}

function toForm(s: StudentResponse): UpdateStudentRequest {
  return {
    dateOfBirth: s.dateOfBirth,
    gender: s.gender ?? undefined,
    portraitUrl: s.portraitUrl ?? undefined,
    originalSchool: s.originalSchool ?? undefined,
    originalClass: s.originalClass ?? undefined,
    notes: s.notes ?? undefined
  };
}

const relationshipLabels: Record<string, string> = { FATHER: "Bố", MOTHER: "Mẹ", GUARDIAN: "Người giám hộ", OTHER: "Khác" };

function ParentsTab({ studentId, showToast }: { studentId: number; showToast: (msg: string) => void }) {
  const [links, setLinks] = useState<ParentStudentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [addingNew, setAddingNew] = useState(false);
  const [account, setAccount] = useState<AccountSelection>({ newAccount: { username: "", email: "", fullName: "", phone: "", password: "" } });
  const [info, setInfo] = useState({ relationship: "MOTHER", isPrimaryContact: false, isFinancialResponsible: false });
  const [submitting, setSubmitting] = useState(false);
  const [submitAttempted, setSubmitAttempted] = useState(false);

  const load = () => {
    setLoading(true);
    listStudentParents(studentId)
      .then(setLinks)
      .finally(() => setLoading(false));
  };
  useEffect(load, [studentId]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (!account.userId && (!account.newAccount?.username || !account.newAccount?.email || !account.newAccount?.fullName)) {
      setError("Vui lòng chọn tài khoản có sẵn hoặc điền đủ thông tin tài khoản mới.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      let parentId: number;
      if (account.userId) {
        // Tài khoản có sẵn có thể ĐÃ có hồ sơ phụ huynh từ lần liên kết học sinh khác trước đó
        // (1 phụ huynh có thể có nhiều con) — tìm lại hồ sơ cũ để dùng liên kết thêm, không gọi
        // createParent nữa vì BE sẽ báo lỗi 409 "đã có hồ sơ phụ huynh" (1 user = đúng 1 Parent).
        const existing = (await searchParents()).find((p) => p.userId === account.userId);
        parentId = existing ? existing.id : (await createParent({ userId: account.userId })).id;
      } else {
        parentId = (await createParent({ newAccount: account.newAccount })).id;
      }
      await linkParent(studentId, {
        parentId,
        relationship: info.relationship as "FATHER" | "MOTHER" | "GUARDIAN" | "OTHER",
        isPrimaryContact: info.isPrimaryContact,
        isFinancialResponsible: info.isFinancialResponsible
      });
      setAddingNew(false);
      setAccount({ newAccount: { username: "", email: "", fullName: "", phone: "", password: "" } });
      load();
      showToast("Đã liên kết phụ huynh thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Liên kết phụ huynh thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleUnlink = async (link: ParentStudentResponse) => {
    if (!window.confirm(`Gỡ liên kết phụ huynh "${link.parentFullName}" khỏi học sinh này?`)) return;
    try {
      await unlinkParent(studentId, link.id);
      load();
      showToast("Đã gỡ liên kết phụ huynh thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gỡ liên kết thất bại.");
    }
  };

  return (
    <div className="space-y-4">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : links.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa liên kết phụ huynh nào.</p>
      ) : (
        <div className="space-y-2">
          {links.map((l) => (
            <div key={l.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="font-bold text-slate-800">{l.parentFullName}</span>
                <Badge variant="info">{relationshipLabels[l.relationship]}</Badge>
                {l.isPrimaryContact && <Badge variant="brand">Liên hệ chính</Badge>}
                {l.isFinancialResponsible && <Badge variant="warning">Chịu trách nhiệm tài chính</Badge>}
              </div>
              <button onClick={() => handleUnlink(l)} className="text-rose-500 hover:text-rose-700">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}

      {addingNew ? (
        <form onSubmit={handleAdd} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
          <AccountSelector value={account} onChange={setAccount} submitAttempted={submitAttempted} />
          <div className="grid grid-cols-3 gap-2 items-end">
            <select value={info.relationship} onChange={(e) => setInfo({ ...info, relationship: e.target.value })} className={inputClass}>
              <option value="FATHER">Bố</option>
              <option value="MOTHER">Mẹ</option>
              <option value="GUARDIAN">Người giám hộ</option>
              <option value="OTHER">Khác</option>
            </select>
            <label className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-700 pb-2.5">
              <input type="checkbox" checked={info.isPrimaryContact} onChange={(e) => setInfo({ ...info, isPrimaryContact: e.target.checked })} />
              Liên hệ chính
            </label>
            <label className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-700 pb-2.5">
              <input type="checkbox" checked={info.isFinancialResponsible} onChange={(e) => setInfo({ ...info, isFinancialResponsible: e.target.checked })} />
              Chịu trách nhiệm tài chính
            </label>
          </div>
          <div className="flex gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => setAddingNew(false)}>
              Hủy
            </Button>
            <Button type="submit" variant="primary" size="sm" disabled={submitting}>
              {submitting ? "Đang lưu..." : "Liên kết"}
            </Button>
          </div>
        </form>
      ) : (
        <Button variant="secondary" size="sm" onClick={() => setAddingNew(true)}>
          <UserPlus className="w-3.5 h-3.5" />
          Liên kết phụ huynh
        </Button>
      )}
    </div>
  );
}

function TransferTab({ studentId, onChanged, showToast }: { studentId: number; onChanged: () => void; showToast: (msg: string) => void }) {
  const [history, setHistory] = useState<StudentTransferHistoryResponse[]>([]);
  const [sites, setSites] = useState<SiteOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ transferType: "SITE_CHANGE", toSiteId: "", fromClassId: "", toClassId: "", effectiveDate: "", reason: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    listTransferHistory(studentId)
      .then(setHistory)
      .finally(() => setLoading(false));
  };
  useEffect(load, [studentId]);
  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.effectiveDate) {
      setError("Vui lòng chọn ngày hiệu lực.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: RecordTransferRequest = {
        transferType: form.transferType as RecordTransferRequest["transferType"],
        toSiteId: form.toSiteId ? Number(form.toSiteId) : undefined,
        fromClassId: form.fromClassId ? Number(form.fromClassId) : undefined,
        toClassId: form.toClassId ? Number(form.toClassId) : undefined,
        effectiveDate: form.effectiveDate,
        reason: form.reason.trim() || undefined
      };
      await recordTransfer(studentId, request);
      setForm({ transferType: "SITE_CHANGE", toSiteId: "", fromClassId: "", toClassId: "", effectiveDate: "", reason: "" });
      load();
      onChanged();
      showToast("Đã ghi nhận chuyển lớp/điểm trường thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Ghi nhận chuyển lớp/điểm trường thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  const needsSite = form.transferType === "SITE_CHANGE" || form.transferType === "BOTH";
  const needsClass = form.transferType === "CLASS_CHANGE" || form.transferType === "BOTH";

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
        <select value={form.transferType} onChange={(e) => setForm({ ...form, transferType: e.target.value })} className={inputClass}>
          <option value="SITE_CHANGE">Chuyển điểm trường</option>
          <option value="CLASS_CHANGE">Chuyển lớp</option>
          <option value="BOTH">Chuyển cả điểm trường lẫn lớp</option>
        </select>
        <div className="grid grid-cols-2 gap-3">
          {needsSite && (
            <select value={form.toSiteId} onChange={(e) => setForm({ ...form, toSiteId: e.target.value })} className={inputClass}>
              <option value="">-- Chọn điểm trường mới --</option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          )}
          {needsClass && (
            <>
              <input
                value={form.fromClassId}
                onChange={(e) => setForm({ ...form, fromClassId: e.target.value.replace(/[^0-9]/g, "") })}
                placeholder="ID lớp đang học (fromClassId) *"
                className={inputClass}
              />
              <input
                value={form.toClassId}
                onChange={(e) => setForm({ ...form, toClassId: e.target.value.replace(/[^0-9]/g, "") })}
                placeholder="ID lớp mới (toClassId) *"
                className={inputClass}
              />
            </>
          )}
          <div>
            <label className={labelClass}>Ngày hiệu lực *</label>
            <DatePicker value={form.effectiveDate} onChange={(v) => setForm({ ...form, effectiveDate: v })} />
          </div>
          <input value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} placeholder="Lý do" className={inputClass} />
        </div>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Ghi nhận chuyển"}
        </Button>
      </form>

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : history.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa có lịch sử chuyển lớp/điểm trường.</p>
      ) : (
        <div className="space-y-2">
          {history.map((h) => (
            <div key={h.id} className="border border-slate-200 rounded-lg p-3 text-xs">
              <Badge variant="info">{h.transferType}</Badge>
              <span className="text-slate-500 ml-2">{h.effectiveDate}</span>
              {h.reason && <span className="text-slate-400 ml-2">— {h.reason}</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function StatusTab({ student, onChanged, showToast }: { student: StudentResponse; onChanged: () => void; showToast: (msg: string) => void }) {
  const [history, setHistory] = useState<StudentStatusHistoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ newStatus: student.status, reason: "", effectiveDate: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    listStatusHistory(student.id)
      .then(setHistory)
      .finally(() => setLoading(false));
  };
  useEffect(load, [student.id]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.effectiveDate) {
      setError("Vui lòng chọn ngày hiệu lực.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await updateStudentStatus(student.id, { newStatus: form.newStatus, reason: form.reason.trim() || undefined, effectiveDate: form.effectiveDate });
      load();
      onChanged();
      showToast("Đã đổi trạng thái học tập thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đổi trạng thái thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <select value={form.newStatus} onChange={(e) => setForm({ ...form, newStatus: e.target.value as StudentResponse["status"] })} className={inputClass}>
            {Object.entries(studentStatusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <DatePicker value={form.effectiveDate} onChange={(v) => setForm({ ...form, effectiveDate: v })} />
          <input value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} placeholder="Lý do" className={`${inputClass} col-span-2`} />
        </div>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Đổi trạng thái"}
        </Button>
      </form>

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : history.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa có lịch sử đổi trạng thái.</p>
      ) : (
        <div className="space-y-2">
          {history.map((h) => (
            <div key={h.id} className="border border-slate-200 rounded-lg p-3 text-xs">
              <Badge variant={studentStatusVariants[h.oldStatus]}>{studentStatusLabels[h.oldStatus] ?? h.oldStatus}</Badge>
              <span className="mx-1.5 text-slate-400">→</span>
              <Badge variant={studentStatusVariants[h.newStatus]}>{studentStatusLabels[h.newStatus] ?? h.newStatus}</Badge>
              <span className="text-slate-400 ml-2">{h.effectiveDate}</span>
              {h.reason && <span className="text-slate-400 ml-2">— {h.reason}</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
