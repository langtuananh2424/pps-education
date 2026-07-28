import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Calendar, FileSpreadsheet, FileText, Save, Search, Sparkles, UserPlus, Users, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import { searchUsers, UserListItemResponse } from "@/features/system-admin/api";
import { listStudents, StudentResponse } from "@/features/student/api";
import { RoomResponse, listRoomsBySite } from "@/features/facility/api";
import {
  AssignTeacherRequest,
  ClassEnrollmentResponse,
  ClassResponse,
  ClassSessionResponse,
  ClassTeacherResponse,
  CreateClassSessionRequest,
  assignClassTeacher,
  cancelClassSession,
  createClassSession,
  enrollStudent,
  getAttendanceSession,
  listClassEnrollments,
  listClassSessions,
  listClassTeachers,
  updateClass,
  withdrawEnrollment
} from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { classStatusLabels, classStatusVariants } from "./ClassListPanel";
import BulkGenerateSessionsForm from "./BulkGenerateSessionsForm";
import ImportScheduleForm from "./ImportScheduleForm";
import ClassGradeSheetPanel from "./ClassGradeSheetPanel";
import StudentInfoModal from "./StudentInfoModal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import DatePicker from "@/components/ui/DatePicker";

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
        {tab === "sessions" && <SessionsTab classId={schoolClass.id} siteId={schoolClass.siteId} canManage={canManage} showToast={showToast} />}
        {tab === "grades" && <ClassGradeSheetPanel classId={schoolClass.id} curriculumId={schoolClass.curriculumId} readOnly={isSiteManagerRole} />}
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
        semester: form.semester.trim() || undefined,
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
          <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as ClassResponse["status"] })} className={inputClass}>
            {Object.entries(classStatusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
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
        <div>
          <label className={labelClass}>Học kỳ</label>
          <input value={form.semester} onChange={(e) => setForm({ ...form, semester: e.target.value })} className={inputClass} />
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
    semester: c.semester ?? "",
    status: c.status
  };
}

const teacherRoleLabels: Record<ClassTeacherResponse["teacherRole"], string> = { PRIMARY: "Chính", ASSISTANT: "Trợ giảng", SUBSTITUTE: "Dạy thay" };

function TeachersTab({ classId, canManage, showToast }: { classId: number; canManage: boolean; showToast: (msg: string) => void }) {
  const [teachers, setTeachers] = useState<ClassTeacherResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [assigning, setAssigning] = useState(false);

  const load = () => {
    setLoading(true);
    listClassTeachers(classId)
      .then(setTeachers)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách giáo viên."))
      .finally(() => setLoading(false));
  };
  useEffect(load, [classId]);

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
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [selected, setSelected] = useState<UserListItemResponse | null>(null);
  const [teacherRole, setTeacherRole] = useState<AssignTeacherRequest["teacherRole"]>("PRIMARY");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSearch = (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    searchUsers({ keyword: q.trim() }, 0, 8).then((res) => setResults(res.content));
  };

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
      {selected ? (
        <div className="flex items-center justify-between bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-semibold px-3 py-2 rounded-lg">
          <span>{selected.fullName} ({selected.username})</span>
          <button type="button" onClick={() => setSelected(null)} className="text-emerald-600 hover:text-rose-600">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      ) : (
        <div className="relative">
          <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
          <input value={query} onChange={(e) => handleSearch(e.target.value)} placeholder="Tìm theo họ tên / email / username..." className={`${inputClass} pl-8`} />
          {results.length > 0 && (
            <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
              {results.map((u) => (
                <button
                  key={u.id}
                  type="button"
                  onClick={() => {
                    setSelected(u);
                    setResults([]);
                  }}
                  className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
                >
                  {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      <select value={teacherRole} onChange={(e) => setTeacherRole(e.target.value as AssignTeacherRequest["teacherRole"])} className={inputClass}>
        <option value="PRIMARY">Giáo viên chính</option>
        <option value="ASSISTANT">Trợ giảng</option>
        <option value="SUBSTITUTE">Dạy thay</option>
      </select>

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

  const load = () => {
    setLoading(true);
    listClassEnrollments(classId)
      .then(setEnrollments)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách học sinh."))
      .finally(() => setLoading(false));
  };
  useEffect(load, [classId]);

  const handleWithdraw = async (enrollmentId: number) => {
    const reason = window.prompt("Lý do rút lớp (không bắt buộc):") ?? "";
    if (!window.confirm("Xác nhận rút học sinh khỏi lớp này?")) return;
    try {
      await withdrawEnrollment(classId, enrollmentId, { withdrawnDate: new Date().toISOString().slice(0, 10), reason: reason.trim() || undefined });
      load();
      showToast("Đã rút học sinh khỏi lớp thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Rút lớp thất bại.");
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">Học sinh đã ghi danh ({enrollments.length})</span>
        {canManage && !enrolling && (
          <Button size="sm" variant="secondary" onClick={() => setEnrolling(true)}>
            <UserPlus className="w-3.5 h-3.5" />
            Ghi danh học sinh
          </Button>
        )}
      </div>

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

      {enrolling && (
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
      )}

      {viewingEnrollment && (
        <StudentInfoModal
          enrollment={viewingEnrollment}
          classId={classId}
          curriculumId={curriculumId}
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
  showToast
}: {
  classId: number;
  siteId: number;
  canManage: boolean;
  showToast: (msg: string) => void;
}) {
  const navigate = useNavigate();
  const { hasPermission } = useApp();
  const hasAttendanceOverride = hasPermission("academic.attendance.create") || hasPermission("academic.attendance.update");
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [attendanceStatusBySession, setAttendanceStatusBySession] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState<"single" | "bulk" | "excel" | null>(null);

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
    const reason = window.prompt("Lý do hủy buổi học:") ?? "";
    if (!reason.trim()) return;
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
        {canManage && !creating && (
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
                    <button onClick={() => handleCancel(s.id)} className="text-rose-500 hover:text-rose-700">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
              <p className="text-slate-400">GV: {s.primaryTeacherName} · Phòng: {s.roomName ?? "Chưa gán"} · {s.sessionType}</p>
              {s.status === "CANCELLED" && s.cancellationReason && <p className="text-rose-500">Lý do hủy: {s.cancellationReason}</p>}
            </div>
          ))}
        </div>
      )}

      {creating === "single" && (
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
      )}
      {creating === "bulk" && (
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
      )}
      {creating === "excel" && (
        <ImportScheduleForm
          classId={classId}
          onDone={() => {
            setCreating(null);
            load();
            showToast("Đã nhập lịch từ Excel thành công!");
          }}
          onCancel={() => setCreating(null)}
        />
      )}
    </div>
  );
}

function CreateSessionForm({ classId, siteId, onDone, onCancel }: { classId: number; siteId: number; onDone: () => void; onCancel: () => void }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [teacher, setTeacher] = useState<UserListItemResponse | null>(null);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [roomId, setRoomId] = useState("");
  const [form, setForm] = useState({ sessionDate: "", startTime: "", endTime: "", sessionType: "REGULAR" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listRoomsBySite(siteId).then(setRooms).catch(() => undefined);
  }, [siteId]);

  const handleSearch = (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    searchUsers({ keyword: q.trim() }, 0, 8).then((res) => setResults(res.content));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!teacher || !form.sessionDate || !form.startTime || !form.endTime) {
      setError("Vui lòng điền đủ ngày/giờ và chọn giáo viên phụ trách.");
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
        sessionType: form.sessionType
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

      <div className="grid grid-cols-2 gap-2">
        <div>
          <label className={labelClass}>Loại buổi học</label>
          <select value={form.sessionType} onChange={(e) => setForm({ ...form, sessionType: e.target.value })} className={inputClass}>
            <option value="REGULAR">Buổi học thường</option>
            <option value="REVIEW">Ôn tập</option>
            <option value="EXAM">Kiểm tra</option>
            <option value="MAKEUP">Học bù</option>
          </select>
        </div>
        <div>
          <label className={labelClass}>Phòng học</label>
          <select value={roomId} onChange={(e) => setRoomId(e.target.value)} className={inputClass}>
            <option value="">-- Không gán --</option>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>
                {r.code} — {r.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div>
        <label className={labelClass}>Giáo viên phụ trách buổi này</label>
        {teacher ? (
          <div className="flex items-center justify-between bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-semibold px-3 py-2 rounded-lg">
            <span>{teacher.fullName} ({teacher.username})</span>
            <button type="button" onClick={() => setTeacher(null)} className="text-emerald-600 hover:text-rose-600">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        ) : (
          <div className="relative">
            <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
            <input value={query} onChange={(e) => handleSearch(e.target.value)} placeholder="Tìm theo họ tên / email / username..." className={`${inputClass} pl-8`} />
            {results.length > 0 && (
              <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
                {results.map((u) => (
                  <button
                    key={u.id}
                    type="button"
                    onClick={() => {
                      setTeacher(u);
                      setResults([]);
                    }}
                    className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
                  >
                    {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
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
