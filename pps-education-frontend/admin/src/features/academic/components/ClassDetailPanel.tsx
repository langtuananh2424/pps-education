import React, { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Calendar, CalendarClock, Download, FileSpreadsheet, FileText, Save, Search, Sparkles, UploadCloud, UserPlus, Users, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import { UserListItemResponse } from "@/features/system-admin/api";
import UserSearchCombobox from "@/features/system-admin/components/UserSearchCombobox";
import { listStudents, StudentResponse } from "@/features/student/api";
import { RoomResponse, listRoomsBySite } from "@/features/facility/api";
import {
  AssignTeacherRequest,
  ClassEnrollmentBatchImportResponse,
  ClassEnrollmentResponse,
  ClassResponse,
  ClassSessionResponse,
  ClassTeacherResponse,
  CreateClassSessionRequest,
  RescheduleClassSessionRequest,
  assignClassTeacher,
  cancelClassSession,
  createClassSession,
  downloadEnrollmentImportTemplate,
  endClassTeacherAssignment,
  enrollStudent,
  getAttendanceSession,
  importClassEnrollments,
  listCancelledSessionsPendingMakeup,
  listClassEnrollments,
  listClassSessions,
  listClassTeachers,
  rescheduleClassSession,
  updateClass,
  withdrawEnrollment
} from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { useDialog } from "@/components/ui/DialogProvider";
import { classStatusLabels, classStatusVariants } from "./ClassListPanel";
import BulkGenerateSessionsForm from "./BulkGenerateSessionsForm";
import ImportScheduleForm from "./ImportScheduleForm";
import ClassGradeSheetPanel from "./ClassGradeSheetPanel";
import StudentInfoModal from "./StudentInfoModal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

type Tab = "profile" | "teachers" | "students" | "sessions" | "grades";

interface ClassDetailPanelProps {
  schoolClass: ClassResponse;
  onChanged: () => void;
}

export default function ClassDetailPanel({ schoolClass, onChanged }: ClassDetailPanelProps) {
  const [tab, setTab] = useState<Tab>("profile");
  const { hasPermission, currentUser } = useApp();
  const canManage = hasPermission("academic.class.manage");
  // SITE_MANAGER thấy được tab "Sổ điểm" (đủ quyền quản trị lớp) nhưng KHÔNG được tự nhập/sửa điểm
  // thay giáo viên ở đây — chỉ xem, khớp đúng hành vi readOnly đã có sẵn ở trang Sổ điểm hệ thống cũ.
  const isSiteManagerRole = currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false;
  // TEACHER cũng có sẵn academic.class.manage (dùng chung cho các thao tác khác trong tab này) nhưng
  // KHÔNG được tự xếp/sinh/nhập lịch buổi học (việc này thuộc Trưởng phòng đào tạo/Nhân viên/Quản trị
  // viên) — ẩn riêng 3 nút ở tab "Buổi học & Điểm danh", không đụng canManage dùng chung cho các tab
  // khác (đã xác nhận với người dùng 2026-07-30).
  // Dùng allow-list (role NÀO được thấy) thay vì loại trừ theo "có role TEACHER" — tài khoản test
  // "superadmin" được gán CẢ 8 role (kể cả TEACHER) để test full quyền, loại trừ theo TEACHER sẽ ẩn
  // luôn cả tài khoản này dù nó cũng có SYS_ADMIN/HEAD_ACADEMIC (phát hiện qua QA 2026-07-30). Theo DB
  // role_permissions, academic.class.manage hiện gán cho đúng 3 role: HEAD_ACADEMIC/STAFF/TEACHER —
  // chỉ 2 role đầu (+ SYS_ADMIN, luôn coi là đủ quyền quản trị) được thấy nút xếp lịch.
  const canScheduleAdminRole =
    (currentUser?.roleCodes?.includes(UserRole.HEAD_ACADEMIC) ||
      currentUser?.roleCodes?.includes(UserRole.STAFF) ||
      currentUser?.roleCodes?.includes(UserRole.SYS_ADMIN)) ??
    false;
  const { message: toastMessage, showToast } = useToast();

  return (
    <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col">
      <div className="p-5 border-b border-slate-200 space-y-3 bg-slate-50/20">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-brand-red bg-orange-50 border border-orange-100 px-2 py-0.5 rounded-md">
              {schoolClass.classCode}
            </span>
            <h2 className="text-sm font-bold text-slate-800 mt-1">{schoolClass.name}</h2>
            <p className="text-[10px] text-slate-400 mt-0.5">{schoolClass.siteName} · {schoolClass.curriculumCode}</p>
          </div>
          <Badge variant={classStatusVariants[schoolClass.status]}>{classStatusLabels[schoolClass.status]}</Badge>
        </div>

        <div className="flex border-b border-slate-200 pt-1 gap-5 overflow-x-auto">
          {(
            [
              ["profile", "Hồ sơ", FileText],
              ["teachers", "Giáo viên", Users],
              ["students", "Học sinh", Users],
              ["sessions", "Buổi học & Điểm danh", Calendar],
              ["grades", "Sổ điểm", FileSpreadsheet]
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
        {tab === "profile" && <ProfileTab schoolClass={schoolClass} onChanged={onChanged} canManage={canManage} showToast={showToast} />}
        {tab === "teachers" && <TeachersTab classId={schoolClass.id} canManage={canManage} showToast={showToast} />}
        {tab === "students" && (
          <StudentsTab
            classId={schoolClass.id}
            curriculumId={schoolClass.curriculumId}
            siteId={schoolClass.siteId}
            siteName={schoolClass.siteName}
            canManage={canManage}
            showToast={showToast}
          />
        )}
        {tab === "sessions" && (
          <SessionsTab
            classId={schoolClass.id}
            siteId={schoolClass.siteId}
            canManage={canManage}
            canCreateSessions={canManage && canScheduleAdminRole}
            showToast={showToast}
          />
        )}
        {tab === "grades" && <ClassGradeSheetPanel classId={schoolClass.id} siteId={schoolClass.siteId} readOnly={isSiteManagerRole} />}
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

function ProfileTab({
  schoolClass,
  onChanged,
  canManage,
  showToast
}: {
  schoolClass: ClassResponse;
  onChanged: () => void;
  canManage: boolean;
  showToast: (msg: string) => void;
}) {
  const [form, setForm] = useState(() => toForm(schoolClass));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nameTouched, setNameTouched] = useState(false);
  const [maxTouched, setMaxTouched] = useState(false);
  const nameInvalid = nameTouched && !form.name.trim();
  const maxInvalid = maxTouched && !form.maxStudents;

  useEffect(() => setForm(toForm(schoolClass)), [schoolClass]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setNameTouched(true);
    setMaxTouched(true);
    if (!form.name.trim() || !form.maxStudents) return;
    setSaving(true);
    setError(null);
    try {
      await updateClass(schoolClass.id, {
        name: form.name.trim(),
        maxStudents: Number(form.maxStudents),
        minStudents: form.minStudents ? Number(form.minStudents) : undefined,
        startDate: form.startDate,
        endDate: form.endDate || undefined,
        academicYear: form.academicYear.trim() || undefined,
        status: form.status as ClassResponse["status"]
      });
      onChanged();
      showToast("Đã lưu hồ sơ lớp học thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật lớp học thất bại.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {!canManage && (
        <div className="text-[11px] text-slate-500 bg-slate-50 border border-slate-200 p-2.5 rounded-lg">
          Tài khoản không có quyền "Xếp lớp & gán khóa học" — chỉ xem, không sửa được hồ sơ lớp.
        </div>
      )}
      <fieldset disabled={!canManage || saving} className="grid grid-cols-2 gap-3">
        <div className="col-span-2">
          <label className={labelClass}>Tên lớp *</label>
          <input
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            onBlur={() => setNameTouched(true)}
            className={nameInvalid ? inputErrorClass : inputClass}
          />
          {nameInvalid && <p className="text-[10px] text-rose-600 mt-1">Vui lòng nhập Tên lớp.</p>}
        </div>
        <div>
          <label className={labelClass}>Trạng thái *</label>
          <Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as ClassResponse["status"] })} className={inputClass}>
            {Object.entries(classStatusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>Sĩ số tối đa *</label>
          <input
            type="number"
            min={1}
            value={form.maxStudents}
            onChange={(e) => setForm({ ...form, maxStudents: e.target.value })}
            onBlur={() => setMaxTouched(true)}
            className={maxInvalid ? inputErrorClass : inputClass}
          />
          {maxInvalid && <p className="text-[10px] text-rose-600 mt-1">Vui lòng nhập Sĩ số tối đa.</p>}
        </div>
        <div>
          <label className={labelClass}>Sĩ số tối thiểu</label>
          <input type="number" min={0} value={form.minStudents} onChange={(e) => setForm({ ...form, minStudents: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Ngày khai giảng *</label>
          <DatePicker value={form.startDate} onChange={(v) => setForm({ ...form, startDate: v })} max={form.endDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>Ngày kết thúc</label>
          <DatePicker value={form.endDate} onChange={(v) => setForm({ ...form, endDate: v })} min={form.startDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>Năm học</label>
          <input value={form.academicYear} onChange={(e) => setForm({ ...form, academicYear: e.target.value })} className={inputClass} />
        </div>
      </fieldset>
      {canManage && (
        <Button type="submit" variant="primary" size="sm" disabled={saving}>
          <Save className="w-3.5 h-3.5" />
          {saving ? "Đang lưu..." : "Lưu hồ sơ"}
        </Button>
      )}
    </form>
  );
}

function toForm(c: ClassResponse) {
  return {
    name: c.name,
    maxStudents: String(c.maxStudents),
    minStudents: c.minStudents != null ? String(c.minStudents) : "",
    startDate: c.startDate,
    endDate: c.endDate ?? "",
    academicYear: c.academicYear ?? "",
    status: c.status
  };
}

const teacherRoleLabels: Record<ClassTeacherResponse["teacherRole"], string> = { PRIMARY: "Chính", ASSISTANT: "Trợ giảng", SUBSTITUTE: "Dạy thay" };

function TeachersTab({ classId, canManage, showToast }: { classId: number; canManage: boolean; showToast: (msg: string) => void }) {
  const [teachers, setTeachers] = useState<ClassTeacherResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [endingId, setEndingId] = useState<number | null>(null);
  const { confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listClassTeachers(classId)
      .then(setTeachers)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách giáo viên."))
      .finally(() => setLoading(false));
  };
  useEffect(load, [classId]);

  const handleEndAssignment = async (t: ClassTeacherResponse) => {
    if (!(await confirmDialog(`Kết thúc phụ trách của ${t.teacherFullName} với lớp này?`))) return;
    setEndingId(t.id);
    try {
      await endClassTeacherAssignment(classId, t.id, { assignedTo: new Date().toISOString().slice(0, 10) });
      load();
      showToast("Đã kết thúc phụ trách!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Kết thúc phụ trách thất bại.");
    } finally {
      setEndingId(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">Giáo viên phụ trách ({teachers.length})</span>
        {canManage && !assigning && (
          <Button size="sm" variant="secondary" onClick={() => setAssigning(true)}>
            <UserPlus className="w-3.5 h-3.5" />
            Gán giáo viên
          </Button>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : teachers.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa gán giáo viên nào.</p>
      ) : (
        <div className="space-y-2">
          {teachers.map((t) => (
            <div key={t.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="font-bold text-slate-800">{t.teacherFullName}</span>
                <Badge variant="info">{teacherRoleLabels[t.teacherRole]}</Badge>
                {t.assignedTo && <Badge variant="neutral">Đã kết thúc {t.assignedTo}</Badge>}
              </div>
              {canManage && !t.assignedTo && (
                <button
                  onClick={() => handleEndAssignment(t)}
                  disabled={endingId === t.id}
                  className="text-rose-500 hover:text-rose-700 text-[11px] font-semibold disabled:opacity-50"
                >
                  {endingId === t.id ? "Đang xử lý..." : "Kết thúc phụ trách"}
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {assigning && (
        <AssignTeacherForm
          classId={classId}
          onDone={() => {
            setAssigning(false);
            load();
            showToast("Đã gán giáo viên thành công!");
          }}
          onCancel={() => setAssigning(false)}
        />
      )}
    </div>
  );
}

function AssignTeacherForm({ classId, onDone, onCancel }: { classId: number; onDone: () => void; onCancel: () => void }) {
  const [selected, setSelected] = useState<UserListItemResponse | null>(null);
  const [teacherRole, setTeacherRole] = useState<AssignTeacherRequest["teacherRole"]>("PRIMARY");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selected) {
      setError("Vui lòng chọn tài khoản giáo viên.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await assignClassTeacher(classId, { teacherUserId: selected.id, teacherRole });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gán giáo viên thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <UserSearchCombobox value={selected} onChange={setSelected} roleFilter="TEACHER" placeholder="Bấm để xem danh sách hoặc gõ để tìm giáo viên..." />

      <Select value={teacherRole} onChange={(e) => setTeacherRole(e.target.value as AssignTeacherRequest["teacherRole"])} className={inputClass}>
        <option value="PRIMARY">Giáo viên chính</option>
        <option value="ASSISTANT">Trợ giảng</option>
        <option value="SUBSTITUTE">Dạy thay</option>
      </Select>

      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Gán giáo viên"}
        </Button>
      </div>
    </form>
  );
}

function StudentsTab({
  classId,
  curriculumId,
  siteId,
  siteName,
  canManage,
  showToast
}: {
  classId: number;
  curriculumId: number;
  siteId: number;
  siteName: string;
  canManage: boolean;
  showToast: (msg: string) => void;
}) {
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [enrolling, setEnrolling] = useState(false);
  const [viewingEnrollment, setViewingEnrollment] = useState<ClassEnrollmentResponse | null>(null);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<ClassEnrollmentBatchImportResponse | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { promptDialog, confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listClassEnrollments(classId)
      .then(setEnrollments)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách học sinh."))
      .finally(() => setLoading(false));
  };
  useEffect(load, [classId]);

  const handleWithdraw = async (enrollmentId: number) => {
    const reason = (await promptDialog("Lý do rút lớp (không bắt buộc):")) ?? "";
    if (!(await confirmDialog("Xác nhận rút học sinh khỏi lớp này?"))) return;
    try {
      await withdrawEnrollment(classId, enrollmentId, { withdrawnDate: new Date().toISOString().slice(0, 10), reason: reason.trim() || undefined });
      load();
      showToast("Đã rút học sinh khỏi lớp thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Rút lớp thất bại.");
    }
  };

  /** UC-65 (bổ sung ngoài SDD gốc): ghi danh hàng loạt qua Excel — mã học sinh phải khớp học sinh đã tồn tại sẵn. */
  const handleDownloadTemplate = async () => {
    setDownloadingTemplate(true);
    setError(null);
    try {
      const blob = await downloadEnrollmentImportTemplate(classId);
      downloadBlob(blob, `mau-ghi-danh-lop-${classId}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tải file mẫu thất bại.");
    } finally {
      setDownloadingTemplate(false);
    }
  };

  const handleImportFile = async (file: File | null) => {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      setError("Chỉ hỗ trợ file .xlsx.");
      return;
    }
    setImporting(true);
    setError(null);
    setImportResult(null);
    try {
      const res = await importClassEnrollments(classId, file);
      setImportResult(res);
      if (res.successRows > 0) {
        load();
        showToast(`Đã ghi danh ${res.successRows} học sinh từ file Excel!`);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nhập từ Excel thất bại.");
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <span className="text-[10px] font-bold uppercase text-slate-500">Học sinh đã ghi danh ({enrollments.length})</span>
        {canManage && (
          <div className="flex items-center gap-2 flex-wrap">
            <Button size="sm" variant="secondary" onClick={() => setEnrolling(true)}>
              <UserPlus className="w-3.5 h-3.5" />
              Ghi danh học sinh
            </Button>
            <button
              type="button"
              onClick={handleDownloadTemplate}
              disabled={downloadingTemplate}
              className="flex items-center gap-1.5 border border-dashed border-slate-300 rounded-lg px-3 py-1.5 text-[11px] font-semibold text-slate-600 hover:bg-white disabled:opacity-50"
            >
              <Download className="w-3.5 h-3.5" />
              {downloadingTemplate ? "Đang tải..." : "Tải mẫu Excel"}
            </button>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={importing}
              className="flex items-center gap-1.5 border-2 border-dashed border-slate-200 rounded-lg px-3 py-1.5 text-[11px] font-semibold text-slate-600 hover:border-brand-orange hover:bg-orange-50/30 disabled:opacity-50"
            >
              <UploadCloud className="w-3.5 h-3.5 text-brand-orange" />
              {importing ? "Đang nhập..." : "Ghi danh theo lô (.xlsx)"}
            </button>
            <input ref={fileInputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleImportFile(e.target.files?.[0] ?? null)} />
          </div>
        )}
      </div>

      {importResult && (
        <div className="w-full flex flex-wrap items-center gap-2 text-[11px]">
          <span className="bg-slate-100 border border-slate-200 text-slate-700 font-semibold px-2 py-1 rounded-lg">
            Tổng: {importResult.totalRows ?? "—"}
          </span>
          <span className="bg-emerald-50 border border-emerald-100 text-emerald-600 font-semibold px-2 py-1 rounded-lg">
            Thành công: {importResult.successRows}
          </span>
          <span className="bg-rose-50 border border-rose-100 text-rose-600 font-semibold px-2 py-1 rounded-lg">
            Lỗi: {importResult.failedRows}
          </span>
          {importResult.errorSummary.length > 0 && (
            <div className="w-full border border-rose-100 rounded-lg overflow-hidden">
              <div className="max-h-40 overflow-y-auto divide-y divide-slate-100">
                {importResult.errorSummary.map((e, i) => (
                  <div key={i} className="px-3 py-1.5 flex gap-2 bg-white">
                    <span className="font-mono font-bold text-slate-400 shrink-0">Dòng {String(e.row)}</span>
                    <span className="text-slate-600">{String(e.reason)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : enrollments.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa ghi danh học sinh nào.</p>
      ) : (
        <div className="space-y-2">
          {enrollments.map((en) => (
            <div key={en.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between">
              <div className="flex items-center gap-2 flex-wrap">
                <button
                  type="button"
                  onClick={() => setViewingEnrollment(en)}
                  className="font-bold text-slate-800 hover:text-brand-red hover:underline"
                >
                  {en.studentFullName}
                </button>
                <span className="font-mono text-slate-400">{en.studentCode}</span>
                <Badge variant={en.status === "ACTIVE" ? "success" : "neutral"}>{en.status}</Badge>
              </div>
              {canManage && en.status === "ACTIVE" && (
                <button onClick={() => handleWithdraw(en.id)} className="text-rose-500 hover:text-rose-700">
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <Modal open={enrolling} onClose={() => setEnrolling(false)} title="Ghi danh học sinh" size="lg">
        <EnrollStudentForm
          classId={classId}
          siteId={siteId}
          siteName={siteName}
          existingStudentIds={new Set(enrollments.filter((en) => en.status === "ACTIVE").map((en) => en.studentId))}
          onDone={() => {
            setEnrolling(false);
            load();
            showToast("Đã ghi danh học sinh thành công!");
          }}
          onCancel={() => setEnrolling(false)}
        />
      </Modal>

      {viewingEnrollment && (
        <StudentInfoModal
          enrollment={viewingEnrollment}
          classId={classId}
          onClose={() => setViewingEnrollment(null)}
        />
      )}
    </div>
  );
}

/** Ghi danh hàng loạt: lấy sẵn danh sách từ Quản lý hồ sơ học sinh, tích chọn nhiều em cùng lúc thay vì gõ tìm từng người. */
function EnrollStudentForm({
  classId,
  siteId,
  siteName,
  existingStudentIds,
  onDone,
  onCancel
}: {
  classId: number;
  siteId: number;
  siteName: string;
  existingStudentIds: Set<number>;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [allStudents, setAllStudents] = useState<StudentResponse[]>([]);
  const [loadingStudents, setLoadingStudents] = useState(true);
  const [query, setQuery] = useState("");
  const [showOtherSites, setShowOtherSites] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [enrolledDate, setEnrolledDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listStudents()
      .then(setAllStudents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách học sinh."))
      .finally(() => setLoadingStudents(false));
  }, []);

  const available = allStudents.filter((s) => !existingStudentIds.has(s.id));
  const otherSitesCount = available.filter((s) => s.primarySiteId != null && s.primarySiteId !== siteId).length;
  const siteMatched = showOtherSites ? available : available.filter((s) => s.primarySiteId == null || s.primarySiteId === siteId);
  const filtered = siteMatched.filter(
    (s) => !query.trim() || s.fullName.toLowerCase().includes(query.toLowerCase()) || s.studentCode.toLowerCase().includes(query.toLowerCase())
  );
  const allFilteredSelected = filtered.length > 0 && filtered.every((s) => selectedIds.has(s.id));

  const toggleOne = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleAllFiltered = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allFilteredSelected) {
        filtered.forEach((s) => next.delete(s.id));
      } else {
        filtered.forEach((s) => next.add(s.id));
      }
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedIds.size === 0) {
      setError("Vui lòng tích chọn ít nhất 1 học sinh.");
      return;
    }
    setSubmitting(true);
    setError(null);
    const ids = Array.from(selectedIds);
    const results = await Promise.allSettled(ids.map((studentId) => enrollStudent(classId, { studentId, enrolledDate })));
    const failed = results.filter((r) => r.status === "rejected");
    setSubmitting(false);
    if (failed.length > 0) {
      setError(`Ghi danh thành công ${ids.length - failed.length}/${ids.length} học sinh — ${failed.length} em bị lỗi (kiểm tra lại sĩ số tối đa hoặc trạng thái lớp).`);
    }
    onDone();
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="relative">
        <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Lọc theo họ tên / mã học sinh..." className={`${inputClass} pl-8`} />
      </div>

      <label className="flex items-center gap-2 text-[11px] text-slate-500">
        <input type="checkbox" checked={showOtherSites} onChange={(e) => setShowOtherSites(e.target.checked)} />
        Hiện cả học sinh điểm trường khác {siteName ? `(mặc định chỉ hiện học sinh của ${siteName})` : ""}
        {!showOtherSites && otherSitesCount > 0 && <span className="text-slate-400">— đang ẩn {otherSitesCount} em</span>}
      </label>

      <div className="border border-slate-200 rounded-lg bg-white max-h-64 overflow-y-auto">
        {loadingStudents ? (
          <p className="text-xs text-slate-500 p-3">Đang tải danh sách học sinh...</p>
        ) : filtered.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-3">Không còn học sinh nào để ghi danh (đã lọc hết hoặc đã ghi danh đủ).</p>
        ) : (
          <>
            <label className="flex items-center gap-2 px-3 py-2 border-b border-slate-100 bg-slate-50 text-[11px] font-bold text-slate-600 cursor-pointer">
              <input type="checkbox" checked={allFilteredSelected} onChange={toggleAllFiltered} />
              Chọn tất cả ({filtered.length})
            </label>
            <div className="divide-y divide-slate-100">
              {filtered.map((s) => (
                <label key={s.id} className="flex items-center gap-2 px-3 py-2 text-xs hover:bg-slate-50 cursor-pointer">
                  <input type="checkbox" checked={selectedIds.has(s.id)} onChange={() => toggleOne(s.id)} />
                  <span className="font-bold text-slate-800">{s.fullName}</span>
                  <span className="font-mono text-slate-400">{s.studentCode}</span>
                </label>
              ))}
            </div>
          </>
        )}
      </div>

      <div>
        <label className={labelClass}>Ngày ghi danh (áp dụng cho tất cả học sinh đã chọn)</label>
        <DatePicker value={enrolledDate} onChange={setEnrolledDate} />
      </div>

      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] text-slate-500">Đã chọn: {selectedIds.size}</span>
        <div className="flex gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" size="sm" disabled={submitting || selectedIds.size === 0}>
            {submitting ? "Đang ghi danh..." : `Ghi danh ${selectedIds.size || ""} học sinh`}
          </Button>
        </div>
      </div>
    </form>
  );
}

export const sessionStatusVariants: Record<string, "success" | "warning" | "danger" | "info" | "neutral" | "brand"> = {
  SCHEDULED: "info",
  COMPLETED: "success",
  CANCELLED: "danger",
  RESCHEDULED: "warning"
};

const teacherTypeLabels: Record<string, string> = { VIETNAMESE: "GV Việt Nam", FOREIGN: "GV nước ngoài" };

const attendanceStatusLabels: Record<string, string> = { DRAFT: "Đã lưu nháp", SUBMITTED: "Đã nộp", LOCKED: "Đã khóa" };
const attendanceStatusVariants: Record<string, "success" | "warning" | "danger" | "info" | "neutral" | "brand"> = {
  DRAFT: "warning",
  SUBMITTED: "success",
  LOCKED: "success"
};

/** UC-15 "Sự kiện kích hoạt": chỉ điểm danh được từ khi buổi học bắt đầu — chặn bấm sớm cho buổi tương lai. */
function hasSessionStarted(s: ClassSessionResponse): boolean {
  return new Date(`${s.sessionDate}T${s.startTime}`) <= new Date();
}

/** V45: GV chỉ điểm danh/sửa đúng NGÀY diễn ra buổi học (StudentAttendanceService.requireCanWriteAttendance). */
function isToday(s: ClassSessionResponse): boolean {
  return s.sessionDate === new Date().toISOString().slice(0, 10);
}

function SessionsTab({
  classId,
  siteId,
  canManage,
  canCreateSessions,
  showToast
}: {
  classId: number;
  siteId: number;
  canManage: boolean;
  canCreateSessions: boolean;
  showToast: (msg: string) => void;
}) {
  const navigate = useNavigate();
  const { hasPermission } = useApp();
  const { promptDialog } = useDialog();
  const hasAttendanceOverride = hasPermission("academic.attendance.create") || hasPermission("academic.attendance.update");
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [attendanceStatusBySession, setAttendanceStatusBySession] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState<"single" | "bulk" | "excel" | null>(null);
  const [reschedulingSession, setReschedulingSession] = useState<ClassSessionResponse | null>(null);

  const load = () => {
    setLoading(true);
    listClassSessions(classId)
      .then(async (res) => {
        setSessions(res);
        const results = await Promise.allSettled(res.map((s) => getAttendanceSession(s.id)));
        const map: Record<number, string> = {};
        results.forEach((r, i) => {
          if (r.status === "fulfilled") map[res[i].id] = r.value.status;
        });
        setAttendanceStatusBySession(map);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách buổi học."))
      .finally(() => setLoading(false));
  };
  useEffect(load, [classId]);

  const handleCancel = async (sessionId: number) => {
    const reason = await promptDialog("Lý do hủy buổi học:", { required: true });
    if (!reason?.trim()) return;
    try {
      await cancelClassSession(classId, sessionId, reason.trim());
      load();
      showToast("Đã hủy buổi học thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Hủy buổi học thất bại.");
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <span className="text-[10px] font-bold uppercase text-slate-500">Buổi học ({sessions.length})</span>
        {canCreateSessions && (
          <div className="flex items-center gap-1.5">
            <Button size="sm" variant="secondary" onClick={() => setCreating("single")}>
              <UserPlus className="w-3.5 h-3.5" />
              Xếp buổi học mới
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setCreating("bulk")}>
              <Sparkles className="w-3.5 h-3.5" />
              Sinh lịch hàng loạt
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setCreating("excel")}>
              <FileSpreadsheet className="w-3.5 h-3.5" />
              Nhập lịch từ Excel
            </Button>
          </div>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : sessions.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa xếp buổi học nào.</p>
      ) : (
        <div className="space-y-2">
          {sessions.map((s) => (
            <div key={s.id} className="border border-slate-200 rounded-lg p-3 text-xs space-y-1.5">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-slate-400 font-mono">Buổi {s.sessionNumber}</span>
                  <span className="font-bold text-slate-800">{s.sessionDate}</span>
                  <span className="text-slate-500">{s.startTime}–{s.endTime}</span>
                  <Badge variant={sessionStatusVariants[s.status] ?? "neutral"}>{s.status}</Badge>
                  {attendanceStatusBySession[s.id] ? (
                    <Badge variant={attendanceStatusVariants[attendanceStatusBySession[s.id]] ?? "neutral"}>
                      {attendanceStatusLabels[attendanceStatusBySession[s.id]] ?? attendanceStatusBySession[s.id]}
                    </Badge>
                  ) : (
                    <Badge variant="neutral">Chưa điểm danh</Badge>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  {!attendanceStatusBySession[s.id] && !hasSessionStarted(s) ? (
                    <Button size="sm" variant="secondary" disabled title="Chưa tới giờ học — chỉ điểm danh được từ khi buổi học bắt đầu.">
                      Chưa tới giờ học
                    </Button>
                  ) : !attendanceStatusBySession[s.id] && !hasAttendanceOverride && !isToday(s) ? (
                    <Button size="sm" variant="secondary" disabled title="Chỉ điểm danh được trong ngày diễn ra buổi học — cần quyền quản trị điểm danh để điểm danh buổi khác ngày.">
                      Đã qua ngày điểm danh
                    </Button>
                  ) : (
                    <Button size="sm" variant="secondary" onClick={() => navigate(`/student/attendance?classId=${classId}&sessionId=${s.id}`)}>
                      {attendanceStatusBySession[s.id] ? "Xem điểm danh" : "Điểm danh"}
                    </Button>
                  )}
                  {canManage && s.status !== "CANCELLED" && (
                    <button onClick={() => setReschedulingSession(s)} title="Dời lịch" className="text-slate-500 hover:text-slate-800">
                      <CalendarClock className="w-3.5 h-3.5" />
                    </button>
                  )}
                  {canManage && s.status !== "CANCELLED" && (
                    <button onClick={() => handleCancel(s.id)} title="Hủy buổi" className="text-rose-500 hover:text-rose-700">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
              <p className="text-slate-400">
                GV: {s.primaryTeacherName}
                {s.teacherType && ` (${teacherTypeLabels[s.teacherType]})`} · Phòng: {s.roomName ?? "Chưa gán"} · {s.sessionType}
              </p>
              {s.status === "CANCELLED" && s.cancellationReason && <p className="text-rose-500">Lý do hủy: {s.cancellationReason}</p>}
              {s.makeupForSessionId != null &&
                (() => {
                  const target = sessions.find((x) => x.id === s.makeupForSessionId);
                  return (
                    <p className="text-amber-600">
                      Bù cho Buổi {target?.sessionNumber ?? "?"}{target ? ` (${target.sessionDate})` : ` (id=${s.makeupForSessionId})`}
                    </p>
                  );
                })()}
            </div>
          ))}
        </div>
      )}

      <Modal open={creating === "single"} onClose={() => setCreating(null)} title="Xếp buổi học mới" size="lg">
        <CreateSessionForm
          classId={classId}
          siteId={siteId}
          onDone={() => {
            setCreating(null);
            load();
            showToast("Đã xếp buổi học thành công!");
          }}
          onCancel={() => setCreating(null)}
        />
      </Modal>
      <Modal open={creating === "bulk"} onClose={() => setCreating(null)} title="Sinh lịch hàng loạt" size="lg">
        <BulkGenerateSessionsForm
          classId={classId}
          siteId={siteId}
          onDone={() => {
            setCreating(null);
            load();
            showToast("Đã sinh lịch hàng loạt thành công!");
          }}
          onCancel={() => setCreating(null)}
        />
      </Modal>
      <Modal open={creating === "excel"} onClose={() => setCreating(null)} title="Nhập lịch từ Excel">
        <ImportScheduleForm
          classId={classId}
          onDone={() => {
            setCreating(null);
            load();
            showToast("Đã nhập lịch từ Excel thành công!");
          }}
          onCancel={() => setCreating(null)}
        />
      </Modal>
      <Modal open={reschedulingSession != null} onClose={() => setReschedulingSession(null)} title="Dời lịch buổi học" size="lg">
        {reschedulingSession && (
          <RescheduleSessionForm
            classId={classId}
            siteId={siteId}
            session={reschedulingSession}
            onDone={() => {
              setReschedulingSession(null);
              load();
              showToast("Đã dời lịch buổi học thành công!");
            }}
            onCancel={() => setReschedulingSession(null)}
          />
        )}
      </Modal>
    </div>
  );
}

function RescheduleSessionForm({
  classId,
  siteId,
  session,
  onDone,
  onCancel
}: {
  classId: number;
  siteId: number;
  session: ClassSessionResponse;
  onDone: () => void;
  onCancel: () => void;
}) {
  // UserSearchCombobox cần cả username (không chỉ id/fullName) để hiện đúng — ClassSessionResponse
  // không trả username của GV phụ trách, nên không dựng sẵn được object giả — để trống, GV/GV vụ tự
  // chọn lại (kể cả không đổi người, chỉ đổi ngày/giờ/phòng) thay vì hiện sai "undefined".
  const [teacher, setTeacher] = useState<UserListItemResponse | null>(null);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [roomId, setRoomId] = useState(session.roomId != null ? String(session.roomId) : "");
  const [form, setForm] = useState({ sessionDate: session.sessionDate, startTime: session.startTime, endTime: session.endTime, reason: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listRoomsBySite(siteId).then(setRooms).catch(() => undefined);
  }, [siteId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!teacher || !form.sessionDate || !form.startTime || !form.endTime) {
      setError("Vui lòng điền đủ ngày/giờ mới và chọn giáo viên phụ trách.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: RescheduleClassSessionRequest = {
        newSessionDate: form.sessionDate,
        newStartTime: form.startTime,
        newEndTime: form.endTime,
        newRoomId: roomId ? Number(roomId) : undefined,
        newPrimaryTeacherId: teacher.id,
        reason: form.reason.trim() || undefined
      };
      await rescheduleClassSession(classId, session.id, request);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dời lịch buổi học thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <p className="text-[11px] text-slate-500">
        Buổi {session.sessionNumber} — lịch hiện tại: <span className="font-bold text-slate-700">{session.sessionDate} {session.startTime}–{session.endTime}</span>
      </p>

      <div className="grid grid-cols-3 gap-2">
        <div>
          <label className={labelClass}>Ngày học mới</label>
          <DatePicker value={form.sessionDate} onChange={(v) => setForm({ ...form, sessionDate: v })} />
        </div>
        <div>
          <label className={labelClass}>Giờ bắt đầu mới</label>
          <input type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} className={inputClass} required />
        </div>
        <div>
          <label className={labelClass}>Giờ kết thúc mới</label>
          <input type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} className={inputClass} required />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2">
        <div>
          <label className={labelClass}>Phòng học mới</label>
          <Select value={roomId} onChange={(e) => setRoomId(e.target.value)} className={inputClass}>
            <option value="">-- Không gán --</option>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>
                {r.code} — {r.name}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>Giáo viên phụ trách mới (hiện tại: {session.primaryTeacherName})</label>
          <UserSearchCombobox value={teacher} onChange={setTeacher} roleFilter="TEACHER" placeholder="Bấm để chọn lại giáo viên (kể cả giữ nguyên người cũ)..." />
        </div>
      </div>

      <div>
        <label className={labelClass}>Lý do dời lịch (không bắt buộc)</label>
        <input value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} className={inputClass} placeholder="VD: Phòng học bị trùng lịch" />
      </div>

      <div className="flex justify-end gap-2 pt-1">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" size="sm" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Dời lịch"}
        </Button>
      </div>
    </form>
  );
}

function CreateSessionForm({ classId, siteId, onDone, onCancel }: { classId: number; siteId: number; onDone: () => void; onCancel: () => void }) {
  const [teacher, setTeacher] = useState<UserListItemResponse | null>(null);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [roomId, setRoomId] = useState("");
  const [form, setForm] = useState({ sessionDate: "", startTime: "", endTime: "", sessionType: "REGULAR", teacherType: "" });
  const [makeupForSessionId, setMakeupForSessionId] = useState("");
  const [cancelledPendingMakeup, setCancelledPendingMakeup] = useState<ClassSessionResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listRoomsBySite(siteId).then(setRooms).catch(() => undefined);
  }, [siteId]);

  // V61 (2026-07-29): tạo buổi MAKEUP bắt buộc chỉ định buổi CANCELLED nó bù cho — chỉ tải danh sách
  // khi thực sự cần (chọn "Học bù"), tránh gọi API thừa cho các loại buổi khác.
  useEffect(() => {
    setMakeupForSessionId("");
    if (form.sessionType !== "MAKEUP") {
      setCancelledPendingMakeup([]);
      return;
    }
    listCancelledSessionsPendingMakeup(classId)
      .then(setCancelledPendingMakeup)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách buổi đã hủy."));
  }, [form.sessionType, classId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!teacher || !form.sessionDate || !form.startTime || !form.endTime) {
      setError("Vui lòng điền đủ ngày/giờ và chọn giáo viên phụ trách.");
      return;
    }
    if (form.sessionType === "MAKEUP" && !makeupForSessionId) {
      setError("Buổi Học bù bắt buộc chọn buổi đã hủy mà nó bù cho.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateClassSessionRequest = {
        sessionDate: form.sessionDate,
        startTime: form.startTime,
        endTime: form.endTime,
        roomId: roomId ? Number(roomId) : undefined,
        primaryTeacherId: teacher.id,
        sessionType: form.sessionType,
        teacherType: form.teacherType ? (form.teacherType as "VIETNAMESE" | "FOREIGN") : undefined,
        makeupForSessionId: form.sessionType === "MAKEUP" ? Number(makeupForSessionId) : undefined
      };
      await createClassSession(classId, request);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xếp buổi học thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-3 gap-2">
        <div>
          <label className={labelClass}>Ngày học</label>
          <DatePicker value={form.sessionDate} onChange={(v) => setForm({ ...form, sessionDate: v })} />
        </div>
        <div>
          <label className={labelClass}>Giờ bắt đầu</label>
          <input type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} className={inputClass} required />
        </div>
        <div>
          <label className={labelClass}>Giờ kết thúc</label>
          <input type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} className={inputClass} required />
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2">
        <div>
          <label className={labelClass}>Loại buổi học</label>
          <Select value={form.sessionType} onChange={(e) => setForm({ ...form, sessionType: e.target.value })} className={inputClass}>
            <option value="REGULAR">Buổi học thường</option>
            <option value="REVIEW">Ôn tập</option>
            <option value="EXAM">Kiểm tra</option>
            <option value="MAKEUP">Học bù</option>
          </Select>
        </div>
        <div>
          <label className={labelClass}>Loại giáo viên</label>
          <Select value={form.teacherType} onChange={(e) => setForm({ ...form, teacherType: e.target.value })} className={inputClass}>
            <option value="">-- Chưa xác định --</option>
            <option value="VIETNAMESE">GV Việt Nam</option>
            <option value="FOREIGN">GV nước ngoài</option>
          </Select>
        </div>
        <div>
          <label className={labelClass}>Phòng học</label>
          <Select value={roomId} onChange={(e) => setRoomId(e.target.value)} className={inputClass}>
            <option value="">-- Không gán --</option>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>
                {r.code} — {r.name}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {form.sessionType === "MAKEUP" && (
        <div>
          <label className={labelClass}>Bù cho buổi đã hủy nào? *</label>
          <Select value={makeupForSessionId} onChange={(e) => setMakeupForSessionId(e.target.value)} className={inputClass}>
            <option value="">-- Chọn buổi đã hủy --</option>
            {cancelledPendingMakeup.map((s) => (
              <option key={s.id} value={s.id}>
                Buổi {s.sessionNumber} — {s.sessionDate} ({s.startTime}–{s.endTime})
              </option>
            ))}
          </Select>
          {cancelledPendingMakeup.length === 0 && (
            <p className="text-[10px] text-slate-400 italic mt-1">Lớp này chưa có buổi nào bị hủy mà chưa có buổi bù.</p>
          )}
        </div>
      )}

      <div>
        <label className={labelClass}>Giáo viên phụ trách buổi này</label>
        <UserSearchCombobox value={teacher} onChange={setTeacher} roleFilter="TEACHER" placeholder="Bấm để xem danh sách hoặc gõ để tìm giáo viên..." />
      </div>

      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Xếp buổi học"}
        </Button>
      </div>
    </form>
  );
}
