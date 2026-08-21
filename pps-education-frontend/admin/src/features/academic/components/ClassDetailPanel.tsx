import React, { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Calendar, CalendarClock, Download, FileSpreadsheet, FileText, Save, Search, Sparkles, UploadCloud, UserPlus, Users, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import { formatDateTime } from "@/lib/i18nFormat";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import { UserListItemResponse } from "@/features/system-admin/api";
import UserSearchCombobox from "@/features/system-admin/components/UserSearchCombobox";
import { listStudents, StudentResponse } from "@/features/student/api";
import { RoomResponse, listRoomsBySite } from "@/features/facility/api";
import {
  AcademicYearResponse,
  AssignTeacherRequest,
  ChangeTeacherRequest,
  ClassEnrollmentBatchImportResponse,
  ClassEnrollmentResponse,
  ClassResponse,
  ClassSessionResponse,
  ClassTeacherHistoryResponse,
  ClassTeacherResponse,
  CreateClassSessionRequest,
  RescheduleClassSessionRequest,
  assignClassTeacher,
  cancelClassSession,
  changeClassTeacher,
  createClassSession,
  downloadEnrollmentImportTemplate,
  endClassTeacherAssignment,
  enrollStudent,
  getAttendanceSession,
  importClassEnrollments,
  listAcademicYears,
  listCancelledSessionsPendingMakeup,
  listClassEnrollments,
  listClassSessions,
  listClassTeacherHistory,
  listClassTeachers,
  rescheduleClassSession,
  updateClass,
  withdrawEnrollment
} from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { useDialog } from "@/components/ui/DialogProvider";
import { classStatusLabel, classStatusValues, classStatusVariants } from "./ClassListPanel";
import BulkGenerateSessionsForm from "./BulkGenerateSessionsForm";
import ImportScheduleForm from "./ImportScheduleForm";
import ClassGradeSheetPanel from "./ClassGradeSheetPanel";
import StudentNameLink from "@/features/reports/components/StudentNameLink";
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
  const { t } = useTranslation("academic-classes");
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
          <Badge variant={classStatusVariants[schoolClass.status]}>{classStatusLabel(t, schoolClass.status)}</Badge>
        </div>

        <div className="flex border-b border-slate-200 pt-1 gap-5 overflow-x-auto">
          {(
            [
              ["profile", t("classDetail.tabs.profile"), FileText],
              ["teachers", t("classDetail.tabs.teachers"), Users],
              ["students", t("classDetail.tabs.students"), Users],
              ["sessions", t("classDetail.tabs.sessions"), Calendar],
              ["grades", t("classDetail.tabs.grades"), FileSpreadsheet]
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
            canManage={canManage && schoolClass.status !== "COMPLETED" && schoolClass.status !== "CANCELLED" && schoolClass.status !== "PLANNED"}
            showToast={showToast}
          />
        )}
        {tab === "sessions" && (
          <SessionsTab
            classId={schoolClass.id}
            siteId={schoolClass.siteId}
            canManage={canManage}
            canCreateSessions={canManage && canScheduleAdminRole && schoolClass.status !== "COMPLETED" && schoolClass.status !== "CANCELLED"}
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
  const { t } = useTranslation("academic-classes");
  const [form, setForm] = useState(() => toForm(schoolClass));
  const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nameTouched, setNameTouched] = useState(false);
  const [maxTouched, setMaxTouched] = useState(false);
  const nameInvalid = nameTouched && !form.name.trim();
  const maxInvalid = maxTouched && !form.maxStudents;

  useEffect(() => setForm(toForm(schoolClass)), [schoolClass]);
  useEffect(() => {
    listAcademicYears().then(setAcademicYears).catch(() => undefined);
  }, []);

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
        academicYearId: form.academicYearId ? Number(form.academicYearId) : undefined,
        status: form.status as ClassResponse["status"]
      });
      onChanged();
      showToast(t("classDetail.profile.saveSuccess"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.profile.saveError"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {!canManage && (
        <div className="text-[11px] text-slate-500 bg-slate-50 border border-slate-200 p-2.5 rounded-lg">
          {t("classDetail.profile.readOnlyNotice")}
        </div>
      )}
      <fieldset disabled={!canManage || saving} className="grid grid-cols-2 gap-3">
        <div className="col-span-2">
          <label className={labelClass}>{t("classDetail.profile.nameLabel")}</label>
          <input
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            onBlur={() => setNameTouched(true)}
            className={nameInvalid ? inputErrorClass : inputClass}
          />
          {nameInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("classDetail.profile.nameRequired")}</p>}
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.profile.statusLabel")}</label>
          <Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as ClassResponse["status"] })} className={inputClass}>
            {classStatusValues.map((status) => (
              <option key={status} value={status}>
                {classStatusLabel(t, status)}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.profile.maxStudentsLabel")}</label>
          <input
            type="number"
            min={1}
            value={form.maxStudents}
            onChange={(e) => setForm({ ...form, maxStudents: e.target.value })}
            onBlur={() => setMaxTouched(true)}
            className={maxInvalid ? inputErrorClass : inputClass}
          />
          {maxInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("classDetail.profile.maxStudentsRequired")}</p>}
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.profile.minStudentsLabel")}</label>
          <input type="number" min={0} value={form.minStudents} onChange={(e) => setForm({ ...form, minStudents: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.profile.startDateLabel")}</label>
          <DatePicker value={form.startDate} onChange={(v) => setForm({ ...form, startDate: v })} max={form.endDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.profile.endDateLabel")}</label>
          <DatePicker value={form.endDate} onChange={(v) => setForm({ ...form, endDate: v })} min={form.startDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.profile.academicYearLabel")}</label>
          <Select value={form.academicYearId} onChange={(e) => setForm({ ...form, academicYearId: e.target.value })} className={inputClass}>
            <option value="">{t("classDetail.profile.academicYearPlaceholder")}</option>
            {academicYears.map((y) => (
              <option key={y.id} value={y.id}>
                {y.code} — {y.name}
              </option>
            ))}
          </Select>
        </div>
      </fieldset>
      {canManage && (
        <Button type="submit" variant="primary" size="sm" disabled={saving}>
          <Save className="w-3.5 h-3.5" />
          {saving ? t("common.saving") : t("classDetail.profile.saveButton")}
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
    academicYearId: c.academicYearId != null ? String(c.academicYearId) : "",
    status: c.status
  };
}

/** Nhãn vai trò/loại giáo viên/loại buổi học/trạng thái điểm danh dịch qua i18next namespace
 * "academic-classes" (`enums.teacherRole.*`, `enums.teacherType.*`, `enums.sessionType.*`,
 * `enums.attendanceStatus.*`) — dùng các hàm `xLabel(t, value)` thay vì map tĩnh cũ. teacherTypeLabel
 * export để MyTeachingSchedulePage.tsx dùng chung (đã import sessionStatusVariants từ file này sẵn). */
function teacherRoleLabel(t: (key: string, options?: Record<string, unknown>) => string, role: ClassTeacherResponse["teacherRole"]): string {
  return t(`enums.teacherRole.${role}`, { defaultValue: role });
}
export function teacherTypeLabel(t: (key: string, options?: Record<string, unknown>) => string, type: string): string {
  return t(`enums.teacherType.${type}`, { defaultValue: type });
}
function sessionTypeLabel(t: (key: string, options?: Record<string, unknown>) => string, type: string): string {
  return t(`enums.sessionType.${type}`, { defaultValue: type });
}
function attendanceStatusLabel(t: (key: string, options?: Record<string, unknown>) => string, status: string): string {
  return t(`enums.attendanceStatus.${status}`, { defaultValue: status });
}

function TeachersTab({ classId, canManage, showToast }: { classId: number; canManage: boolean; showToast: (msg: string) => void }) {
  const { t } = useTranslation("academic-classes");
  const [teachers, setTeachers] = useState<ClassTeacherResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [endingId, setEndingId] = useState<number | null>(null);
  const [changingId, setChangingId] = useState<number | null>(null);
  const [showHistory, setShowHistory] = useState(false);
  const { confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listClassTeachers(classId)
      .then(setTeachers)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("classDetail.teachers.loadError")))
      .finally(() => setLoading(false));
  };
  useEffect(load, [classId]);

  const handleEndAssignment = async (teacher: ClassTeacherResponse) => {
    if (!(await confirmDialog(t("classDetail.teachers.endConfirm", { name: teacher.teacherFullName })))) return;
    setEndingId(teacher.id);
    try {
      await endClassTeacherAssignment(classId, teacher.id, { assignedTo: new Date().toISOString().slice(0, 10) });
      load();
      showToast(t("classDetail.teachers.endSuccess"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.teachers.endError"));
    } finally {
      setEndingId(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("classDetail.teachers.sectionTitle", { count: teachers.length })}</span>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="secondary" onClick={() => setShowHistory(true)}>
            {t("classDetail.teachers.historyButton")}
          </Button>
          {canManage && !assigning && (
            <Button size="sm" variant="secondary" onClick={() => setAssigning(true)}>
              <UserPlus className="w-3.5 h-3.5" />
              {t("classDetail.teachers.assignButton")}
            </Button>
          )}
        </div>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">{t("common.loading")}</p>
      ) : teachers.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("classDetail.teachers.empty")}</p>
      ) : (
        <div className="space-y-2">
          {teachers.map((teacher) => (
            <div key={teacher.id} className="border border-slate-200 rounded-lg p-3 text-xs">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-bold text-slate-800">{teacher.teacherFullName}</span>
                  <Badge variant="info">{teacherRoleLabel(t, teacher.teacherRole)}</Badge>
                  {teacher.teacherType && <Badge variant="neutral">{teacherTypeLabel(t, teacher.teacherType)}</Badge>}
                  {teacher.assignedTo && <Badge variant="neutral">{t("classDetail.teachers.endedBadge", { date: teacher.assignedTo })}</Badge>}
                </div>
                {canManage && !teacher.assignedTo && (
                  <div className="flex items-center gap-3">
                    {teacher.teacherRole === "PRIMARY" && (
                      <button
                        onClick={() => setChangingId(changingId === teacher.id ? null : teacher.id)}
                        className="text-sky-600 hover:text-sky-800 text-[11px] font-semibold"
                      >
                        {t("classDetail.teachers.changeTeacherAction")}
                      </button>
                    )}
                    <button
                      onClick={() => handleEndAssignment(teacher)}
                      disabled={endingId === teacher.id}
                      className="text-rose-500 hover:text-rose-700 text-[11px] font-semibold disabled:opacity-50"
                    >
                      {endingId === teacher.id ? t("classDetail.teachers.endingInProgress") : t("classDetail.teachers.endAssignmentButton")}
                    </button>
                  </div>
                )}
              </div>
              {changingId === teacher.id && (
                <ChangeTeacherForm
                  classId={classId}
                  classTeacherId={teacher.id}
                  onDone={() => {
                    setChangingId(null);
                    load();
                    showToast(t("classDetail.teachers.changeSuccess"));
                  }}
                  onCancel={() => setChangingId(null)}
                />
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
            showToast(t("classDetail.teachers.assignSuccess"));
          }}
          onCancel={() => setAssigning(false)}
        />
      )}

      <Modal open={showHistory} onClose={() => setShowHistory(false)} title={t("classDetail.teachers.historyModalTitle")} size="lg">
        <TeacherHistoryPanel classId={classId} />
      </Modal>
    </div>
  );
}

/** UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): timeline lịch sử thay đổi giáo viên phụ trách của cả lớp. */
function TeacherHistoryPanel({ classId }: { classId: number }) {
  const { t, i18n } = useTranslation("academic-classes");
  const [history, setHistory] = useState<ClassTeacherHistoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    listClassTeacherHistory(classId)
      .then(setHistory)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("classDetail.teacherHistory.loadError")))
      .finally(() => setLoading(false));
  }, [classId]);

  const describe = (h: ClassTeacherHistoryResponse) => {
    if (h.action === "CREATED") {
      const role = h.details.teacherRole ? teacherRoleLabel(t, h.details.teacherRole as ClassTeacherResponse["teacherRole"]) : "?";
      const type = h.details.teacherType ? ` (${teacherTypeLabel(t, h.details.teacherType as string)})` : "";
      return t("classDetail.teacherHistory.describeCreated", { name: h.teacherFullName, role, type });
    }
    return t("classDetail.teacherHistory.describeEnded", { name: h.teacherFullName, date: h.details.assignedTo ?? "?" });
  };

  if (loading) return <p className="text-xs text-slate-500">{t("common.loading")}</p>;
  if (error) return <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>;
  if (history.length === 0) return <p className="text-xs text-slate-400 italic">{t("classDetail.teacherHistory.empty")}</p>;

  return (
    <div className="space-y-2 max-h-96 overflow-y-auto">
      {history.map((h) => (
        <div key={h.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between gap-2">
          <div>
            <p className="text-slate-800">{describe(h)}</p>
            <p className="text-[10px] text-slate-400 mt-0.5">
              {t("classDetail.teacherHistory.byLabel", { name: h.changedByName, date: formatDateTime(h.createdAt, i18n.language) })}
            </p>
          </div>
          <Badge variant={h.action === "CREATED" ? "success" : "neutral"}>
            {h.action === "CREATED" ? t("classDetail.teacherHistory.createdBadge") : t("classDetail.teacherHistory.endedBadge")}
          </Badge>
        </div>
      ))}
    </div>
  );
}

function AssignTeacherForm({ classId, onDone, onCancel }: { classId: number; onDone: () => void; onCancel: () => void }) {
  const { t } = useTranslation("academic-classes");
  const [selected, setSelected] = useState<UserListItemResponse | null>(null);
  const [teacherRole, setTeacherRole] = useState<AssignTeacherRequest["teacherRole"]>("PRIMARY");
  const [teacherType, setTeacherType] = useState<"" | "VIETNAMESE" | "FOREIGN">("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selected) {
      setError(t("classDetail.assignTeacherForm.selectRequired"));
      return;
    }
    if (teacherRole === "PRIMARY" && !teacherType) {
      setError(t("classDetail.assignTeacherForm.primaryTypeRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await assignClassTeacher(classId, {
        teacherUserId: selected.id,
        teacherRole,
        teacherType: teacherRole === "PRIMARY" ? (teacherType as "VIETNAMESE" | "FOREIGN") : undefined
      });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.assignTeacherForm.submitError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <UserSearchCombobox
        value={selected}
        onChange={setSelected}
        roleFilter="TEACHER"
        placeholder={t("classDetail.assignTeacherForm.comboboxPlaceholder")}
      />

      <div className="grid grid-cols-2 gap-2">
        <Select
          value={teacherRole}
          onChange={(e) => setTeacherRole(e.target.value as AssignTeacherRequest["teacherRole"])}
          className={inputClass}
        >
          <option value="PRIMARY">{t("classDetail.assignTeacherForm.roleOptionPrimary")}</option>
          <option value="CM">{teacherRoleLabel(t, "CM")}</option>
          <option value="ASSISTANT">{teacherRoleLabel(t, "ASSISTANT")}</option>
          <option value="SUBSTITUTE">{teacherRoleLabel(t, "SUBSTITUTE")}</option>
        </Select>
        {teacherRole === "PRIMARY" && (
          <Select value={teacherType} onChange={(e) => setTeacherType(e.target.value as "" | "VIETNAMESE" | "FOREIGN")} className={inputClass}>
            <option value="">{t("common.teacherTypePlaceholder")}</option>
            <option value="VIETNAMESE">{teacherTypeLabel(t, "VIETNAMESE")}</option>
            <option value="FOREIGN">{teacherTypeLabel(t, "FOREIGN")}</option>
          </Select>
        )}
      </div>
      {teacherRole === "PRIMARY" && (
        <p className="text-[10px] text-slate-400 italic">{t("classDetail.assignTeacherForm.primaryTypeHint")}</p>
      )}

      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("common.cancelButton")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? t("common.saving") : t("classDetail.teachers.assignButton")}
        </Button>
      </div>
    </form>
  );
}

/** UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): đổi giáo viên chính — cascade tự động sang các buổi SCHEDULED tương lai cùng loại giáo viên. */
function ChangeTeacherForm({
  classId,
  classTeacherId,
  onDone,
  onCancel
}: {
  classId: number;
  classTeacherId: number;
  onDone: () => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation("academic-classes");
  const [selected, setSelected] = useState<UserListItemResponse | null>(null);
  const [effectiveDate, setEffectiveDate] = useState(new Date().toISOString().slice(0, 10));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selected || !effectiveDate) {
      setError(t("classDetail.changeTeacherForm.validationError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: ChangeTeacherRequest = { newTeacherUserId: selected.id, effectiveDate };
      await changeClassTeacher(classId, classTeacherId, request);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.changeTeacherForm.submitError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-sky-50 border border-sky-200 rounded-lg p-3 mt-2 space-y-2">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      <p className="text-[10px] text-slate-500">{t("classDetail.changeTeacherForm.cascadeHint")}</p>
      <UserSearchCombobox
        value={selected}
        onChange={setSelected}
        roleFilter="TEACHER"
        placeholder={t("classDetail.changeTeacherForm.comboboxPlaceholder")}
      />
      <div>
        <label className={labelClass}>{t("classDetail.changeTeacherForm.effectiveDateLabel")}</label>
        <DatePicker value={effectiveDate} onChange={setEffectiveDate} />
      </div>
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("common.cancelButton")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? t("common.saving") : t("classDetail.teachers.changeTeacherAction")}
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
  const { t } = useTranslation("academic-classes");
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [enrolling, setEnrolling] = useState(false);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<ClassEnrollmentBatchImportResponse | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { promptDialog, confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listClassEnrollments(classId)
      .then(setEnrollments)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("classDetail.students.loadError")))
      .finally(() => setLoading(false));
  };
  useEffect(load, [classId]);

  const handleWithdraw = async (enrollmentId: number) => {
    const reason = (await promptDialog(t("classDetail.students.withdrawReasonPrompt"))) ?? "";
    if (!(await confirmDialog(t("classDetail.students.withdrawConfirm")))) return;
    try {
      await withdrawEnrollment(classId, enrollmentId, { withdrawnDate: new Date().toISOString().slice(0, 10), reason: reason.trim() || undefined });
      load();
      showToast(t("classDetail.students.withdrawSuccess"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.students.withdrawError"));
    }
  };

  /** UC-65 (bổ sung ngoài SDD gốc): ghi danh hàng loạt qua Excel — mã học sinh phải khớp học sinh đã tồn tại sẵn. */
  const handleDownloadTemplate = async () => {
    setDownloadingTemplate(true);
    setError(null);
    try {
      const blob = await downloadEnrollmentImportTemplate(classId);
      downloadBlob(blob, `${t("classDetail.students.templateFileName")}-${classId}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.students.downloadTemplateError"));
    } finally {
      setDownloadingTemplate(false);
    }
  };

  /** Xuất danh sách học sinh đang ghi danh (đúng danh sách đang hiển thị bên dưới) ra Excel. */
  const handleExportStudents = () => {
    const headers = [
      t("classDetail.students.exportHeaders.index"),
      t("classDetail.students.exportHeaders.studentCode"),
      t("classDetail.students.exportHeaders.fullName"),
      t("classDetail.students.exportHeaders.dateOfBirth"),
      t("classDetail.students.exportHeaders.enrolledDate"),
      t("classDetail.students.exportHeaders.withdrawnDate"),
      t("classDetail.students.exportHeaders.status"),
      t("classDetail.students.exportHeaders.academicYear")
    ];
    const rows = enrollments.map((en, idx) => [
      String(idx + 1),
      en.studentCode,
      en.studentFullName,
      en.studentDateOfBirth ?? "",
      en.enrolledDate,
      en.withdrawnDate ?? "",
      en.status,
      en.academicYear ?? ""
    ]);
    const blob = buildXlsxTemplateBlob(headers, rows);
    downloadBlob(blob, `${t("classDetail.students.exportFileName")}-${classId}.xlsx`);
  };

  const handleImportFile = async (file: File | null) => {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      setError(t("classDetail.students.invalidFileType"));
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
        showToast(t("classDetail.students.importSuccessToast", { count: res.successRows }));
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.students.importError"));
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("classDetail.students.sectionTitle", { count: enrollments.length })}</span>
        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            onClick={handleExportStudents}
            disabled={enrollments.length === 0}
            title={t("classDetail.students.exportButtonTitle")}
            className="flex items-center gap-1.5 border border-dashed border-slate-300 rounded-lg px-3 py-1.5 text-[11px] font-semibold text-slate-600 hover:bg-white disabled:opacity-50"
          >
            <Download className="w-3.5 h-3.5" />
            {t("classDetail.students.exportButton")}
          </button>
          {canManage && (
            <>
              <Button size="sm" variant="secondary" onClick={() => setEnrolling(true)}>
                <UserPlus className="w-3.5 h-3.5" />
                {t("classDetail.students.enrollButton")}
              </Button>
              <button
                type="button"
                onClick={handleDownloadTemplate}
                disabled={downloadingTemplate}
                className="flex items-center gap-1.5 border border-dashed border-slate-300 rounded-lg px-3 py-1.5 text-[11px] font-semibold text-slate-600 hover:bg-white disabled:opacity-50"
              >
                <Download className="w-3.5 h-3.5" />
                {downloadingTemplate ? t("classDetail.students.downloading") : t("classDetail.students.downloadTemplateButton")}
              </button>
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={importing}
                className="flex items-center gap-1.5 border-2 border-dashed border-slate-200 rounded-lg px-3 py-1.5 text-[11px] font-semibold text-slate-600 hover:border-brand-orange hover:bg-orange-50/30 disabled:opacity-50"
              >
                <UploadCloud className="w-3.5 h-3.5 text-brand-orange" />
                {importing ? t("classDetail.students.importing") : t("classDetail.students.importBatchButton")}
              </button>
              <input ref={fileInputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleImportFile(e.target.files?.[0] ?? null)} />
            </>
          )}
        </div>
      </div>

      {importResult && (
        <div className="w-full flex flex-wrap items-center gap-2 text-[11px]">
          <span className="bg-slate-100 border border-slate-200 text-slate-700 font-semibold px-2 py-1 rounded-lg">
            {t("classDetail.students.importResultTotal", { count: importResult.totalRows ?? "—" })}
          </span>
          <span className="bg-emerald-50 border border-emerald-100 text-emerald-600 font-semibold px-2 py-1 rounded-lg">
            {t("classDetail.students.importResultSuccess", { count: importResult.successRows })}
          </span>
          <span className="bg-rose-50 border border-rose-100 text-rose-600 font-semibold px-2 py-1 rounded-lg">
            {t("classDetail.students.importResultFailed", { count: importResult.failedRows })}
          </span>
          {importResult.errorSummary.length > 0 && (
            <div className="w-full border border-rose-100 rounded-lg overflow-hidden">
              <div className="max-h-40 overflow-y-auto divide-y divide-slate-100">
                {importResult.errorSummary.map((e, i) => (
                  <div key={i} className="px-3 py-1.5 flex gap-2 bg-white">
                    <span className="font-mono font-bold text-slate-400 shrink-0">{t("classDetail.students.importErrorRow", { row: String(e.row) })}</span>
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
        <p className="text-xs text-slate-500">{t("common.loading")}</p>
      ) : enrollments.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("classDetail.students.empty")}</p>
      ) : (
        <div className="space-y-2">
          {enrollments.map((en) => (
            <div key={en.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between">
              <div className="flex items-center gap-2 flex-wrap">
                <StudentNameLink
                  studentId={en.studentId}
                  name={en.studentFullName}
                  className="font-bold text-slate-800 hover:text-brand-red hover:underline"
                />
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

      <Modal open={enrolling} onClose={() => setEnrolling(false)} title={t("classDetail.students.enrollModalTitle")} size="lg">
        <EnrollStudentForm
          classId={classId}
          siteId={siteId}
          siteName={siteName}
          existingStudentIds={new Set(enrollments.filter((en) => en.status === "ACTIVE").map((en) => en.studentId))}
          onDone={() => {
            setEnrolling(false);
            load();
            showToast(t("classDetail.students.enrollSuccess"));
          }}
          onCancel={() => setEnrolling(false)}
        />
      </Modal>

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
  const { t } = useTranslation("academic-classes");
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("classDetail.enrollForm.loadError")))
      .finally(() => setLoadingStudents(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
      setError(t("classDetail.enrollForm.selectAtLeastOne"));
      return;
    }
    setSubmitting(true);
    setError(null);
    const ids = Array.from(selectedIds);
    const results = await Promise.allSettled(ids.map((studentId) => enrollStudent(classId, { studentId, enrolledDate })));
    const failed = results.filter((r) => r.status === "rejected");
    setSubmitting(false);
    if (failed.length > 0) {
      setError(t("classDetail.enrollForm.partialFailure", { success: ids.length - failed.length, total: ids.length, failed: failed.length }));
    }
    onDone();
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="relative">
        <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t("classDetail.enrollForm.searchPlaceholder")}
          className={`${inputClass} pl-8`}
        />
      </div>

      <label className="flex items-center gap-2 text-[11px] text-slate-500">
        <input type="checkbox" checked={showOtherSites} onChange={(e) => setShowOtherSites(e.target.checked)} />
        {t("classDetail.enrollForm.showOtherSitesLabel")} {siteName ? t("classDetail.enrollForm.showOtherSitesHint", { siteName }) : ""}
        {!showOtherSites && otherSitesCount > 0 && (
          <span className="text-slate-400">{t("classDetail.enrollForm.hiddenCount", { count: otherSitesCount })}</span>
        )}
      </label>

      <div className="border border-slate-200 rounded-lg bg-white max-h-64 overflow-y-auto">
        {loadingStudents ? (
          <p className="text-xs text-slate-500 p-3">{t("classDetail.enrollForm.loadingStudents")}</p>
        ) : filtered.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-3">{t("classDetail.enrollForm.noneAvailable")}</p>
        ) : (
          <>
            <label className="flex items-center gap-2 px-3 py-2 border-b border-slate-100 bg-slate-50 text-[11px] font-bold text-slate-600 cursor-pointer">
              <input type="checkbox" checked={allFilteredSelected} onChange={toggleAllFiltered} />
              {t("classDetail.enrollForm.selectAll", { count: filtered.length })}
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
        <label className={labelClass}>{t("classDetail.enrollForm.enrolledDateLabel")}</label>
        <DatePicker value={enrolledDate} onChange={setEnrolledDate} />
      </div>

      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] text-slate-500">{t("classDetail.enrollForm.selectedCount", { count: selectedIds.size })}</span>
        <div className="flex gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
            {t("common.cancelButton")}
          </Button>
          <Button type="submit" variant="primary" size="sm" disabled={submitting || selectedIds.size === 0}>
            {submitting ? t("classDetail.enrollForm.submitting") : t("classDetail.enrollForm.submitButton", { count: selectedIds.size })}
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

/**
 * UC-71 "Nhận lớp" (bổ sung ngoài SDD gốc, xác nhận 2026-08-18) — nhãn/màu
 * trạng thái nhận lớp TÍNH RA (ClassSessionCheckInService#listEffectiveStatus).
 * Dùng chung cho MyTeachingSchedulePage (GV tự xem) và EmployeeSchedulePage
 * (roster HR/Quản lý điểm trường).
 */
/** `t` dịch qua i18next namespace "common" (key `checkInStatus.*`) — xem src/i18n/locales/{vi,en}/common.json. */
export function checkInStatusLabel(t: (key: string) => string, status: string): string {
  return t(`checkInStatus.${status}`);
}
export const checkInStatusVariants: Record<string, "success" | "warning" | "danger" | "info" | "neutral" | "brand"> = {
  NOT_YET_OPEN: "neutral",
  PENDING: "warning",
  ON_TIME: "success",
  LATE: "warning",
  ABSENT: "danger"
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

/**
 * Sửa đổi 2026-08-18 (thay thế rule V45 "đúng ngày"): GV thường chỉ điểm
 * danh/sửa được TRONG khung giờ buổi học [startTime, endTime] — đồng bộ
 * StudentAttendanceService.isWithinSessionWindow / AttendancePage.tsx.
 */
function hasSessionEnded(s: ClassSessionResponse): boolean {
  return new Date(`${s.sessionDate}T${s.endTime}`) < new Date();
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
  const { t } = useTranslation("academic-classes");
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("classDetail.sessions.loadError")))
      .finally(() => setLoading(false));
  };
  useEffect(load, [classId]);

  const handleCancel = async (sessionId: number) => {
    const reason = await promptDialog(t("classDetail.sessions.cancelReasonPrompt"), { required: true });
    if (!reason?.trim()) return;
    try {
      await cancelClassSession(classId, sessionId, reason.trim());
      load();
      showToast(t("classDetail.sessions.cancelSuccess"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.sessions.cancelError"));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("classDetail.sessions.sectionTitle", { count: sessions.length })}</span>
        {canCreateSessions && (
          <div className="flex items-center gap-1.5">
            <Button size="sm" variant="secondary" onClick={() => setCreating("single")}>
              <UserPlus className="w-3.5 h-3.5" />
              {t("classDetail.sessions.createSingleButton")}
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setCreating("bulk")}>
              <Sparkles className="w-3.5 h-3.5" />
              {t("classDetail.sessions.createBulkButton")}
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setCreating("excel")}>
              <FileSpreadsheet className="w-3.5 h-3.5" />
              {t("classDetail.sessions.createExcelButton")}
            </Button>
          </div>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">{t("common.loading")}</p>
      ) : sessions.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("classDetail.sessions.empty")}</p>
      ) : (
        <div className="space-y-2">
          {sessions.map((s) => (
            <div key={s.id} className="border border-slate-200 rounded-lg p-3 text-xs space-y-1.5">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-slate-400 font-mono">{t("classDetail.sessions.sessionNumber", { number: s.sessionNumber })}</span>
                  <span className="font-bold text-slate-800">{s.sessionDate}</span>
                  <span className="text-slate-500">{s.startTime}–{s.endTime}</span>
                  <Badge variant={sessionStatusVariants[s.status] ?? "neutral"}>{s.status}</Badge>
                  {attendanceStatusBySession[s.id] ? (
                    <Badge variant={attendanceStatusVariants[attendanceStatusBySession[s.id]] ?? "neutral"}>
                      {attendanceStatusLabel(t, attendanceStatusBySession[s.id])}
                    </Badge>
                  ) : (
                    <Badge variant="neutral">{t("classDetail.sessions.notAttendanceYet")}</Badge>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  {!attendanceStatusBySession[s.id] && !hasSessionStarted(s) ? (
                    <Button size="sm" variant="secondary" disabled title={t("classDetail.sessions.notYetTimeTitle")}>
                      {t("classDetail.sessions.notYetTimeButton")}
                    </Button>
                  ) : !attendanceStatusBySession[s.id] && !hasAttendanceOverride && hasSessionEnded(s) ? (
                    <Button size="sm" variant="secondary" disabled title={t("classDetail.sessions.pastDateTitle")}>
                      {t("classDetail.sessions.pastDateButton")}
                    </Button>
                  ) : (
                    <Button size="sm" variant="secondary" onClick={() => navigate(`/student/attendance?classId=${classId}&sessionId=${s.id}`)}>
                      {attendanceStatusBySession[s.id] ? t("classDetail.sessions.viewAttendanceButton") : t("classDetail.sessions.takeAttendanceButton")}
                    </Button>
                  )}
                  {canManage && s.status !== "CANCELLED" && (
                    <button onClick={() => setReschedulingSession(s)} title={t("classDetail.sessions.rescheduleTitle")} className="text-slate-500 hover:text-slate-800">
                      <CalendarClock className="w-3.5 h-3.5" />
                    </button>
                  )}
                  {canManage && s.status !== "CANCELLED" && (
                    <button onClick={() => handleCancel(s.id)} title={t("classDetail.sessions.cancelSessionTitle")} className="text-rose-500 hover:text-rose-700">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
              <p className="text-slate-400">
                {t("classDetail.sessions.teacherLine", {
                  teacher: s.primaryTeacherName,
                  type: s.teacherType ? ` (${teacherTypeLabel(t, s.teacherType)})` : "",
                  room: s.roomName ?? t("classDetail.sessions.roomUnassigned"),
                  sessionType: s.sessionType
                })}
              </p>
              {s.status === "CANCELLED" && s.cancellationReason && (
                <p className="text-rose-500">{t("classDetail.sessions.cancellationReason", { reason: s.cancellationReason })}</p>
              )}
              {s.makeupForSessionId != null &&
                (() => {
                  const target = sessions.find((x) => x.id === s.makeupForSessionId);
                  return (
                    <p className="text-amber-600">
                      {target
                        ? t("classDetail.sessions.makeupForWithDate", { number: target.sessionNumber ?? "?", date: target.sessionDate })
                        : t("classDetail.sessions.makeupForWithId", { number: "?", id: s.makeupForSessionId })}
                    </p>
                  );
                })()}
            </div>
          ))}
        </div>
      )}

      <Modal open={creating === "single"} onClose={() => setCreating(null)} title={t("classDetail.sessions.createSessionModalTitle")} size="lg">
        <CreateSessionForm
          classId={classId}
          siteId={siteId}
          onDone={() => {
            setCreating(null);
            load();
            showToast(t("classDetail.sessions.createSessionSuccess"));
          }}
          onCancel={() => setCreating(null)}
        />
      </Modal>
      <Modal open={creating === "bulk"} onClose={() => setCreating(null)} title={t("classDetail.sessions.bulkGenerateModalTitle")} size="lg">
        <BulkGenerateSessionsForm
          classId={classId}
          siteId={siteId}
          onDone={() => {
            setCreating(null);
            load();
            showToast(t("classDetail.sessions.bulkGenerateSuccess"));
          }}
          onCancel={() => setCreating(null)}
        />
      </Modal>
      <Modal open={creating === "excel"} onClose={() => setCreating(null)} title={t("classDetail.sessions.importExcelModalTitle")}>
        <ImportScheduleForm
          classId={classId}
          onDone={() => {
            setCreating(null);
            load();
            showToast(t("classDetail.sessions.importExcelSuccess"));
          }}
          onCancel={() => setCreating(null)}
        />
      </Modal>
      <Modal open={reschedulingSession != null} onClose={() => setReschedulingSession(null)} title={t("classDetail.sessions.rescheduleModalTitle")} size="lg">
        {reschedulingSession && (
          <RescheduleSessionForm
            classId={classId}
            siteId={siteId}
            session={reschedulingSession}
            onDone={() => {
              setReschedulingSession(null);
              load();
              showToast(t("classDetail.sessions.rescheduleSuccess"));
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
  const { t } = useTranslation("academic-classes");
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
    if (!form.sessionDate || !form.startTime || !form.endTime) {
      setError(t("classDetail.rescheduleForm.dateTimeRequired"));
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
        reason: form.reason.trim() || undefined
      };
      await rescheduleClassSession(classId, session.id, request);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.rescheduleForm.submitError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <p className="text-[11px] text-slate-500">
        {t("classDetail.rescheduleForm.currentSchedulePrefix", { number: session.sessionNumber })}{" "}
        <span className="font-bold text-slate-700">{session.sessionDate} {session.startTime}–{session.endTime}</span>
      </p>

      <div className="grid grid-cols-3 gap-2">
        <div>
          <label className={labelClass}>{t("classDetail.rescheduleForm.newDateLabel")}</label>
          <DatePicker value={form.sessionDate} onChange={(v) => setForm({ ...form, sessionDate: v })} />
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.rescheduleForm.newStartTimeLabel")}</label>
          <input type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} className={inputClass} required />
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.rescheduleForm.newEndTimeLabel")}</label>
          <input type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} className={inputClass} required />
        </div>
      </div>

      <div>
        <label className={labelClass}>{t("classDetail.rescheduleForm.newRoomLabel")}</label>
        <Select value={roomId} onChange={(e) => setRoomId(e.target.value)} className={inputClass}>
          <option value="">{t("common.noneOption")}</option>
          {rooms.map((r) => (
            <option key={r.id} value={r.id}>
              {r.code} — {r.name}
            </option>
          ))}
        </Select>
      </div>
      {/* Bổ sung ngoài SDD gốc, xác nhận 2026-08-13: giáo viên phụ trách buổi mới KHÔNG còn chọn tay
          — hệ thống tự động suy ra lại từ giáo viên chính đang phụ trách lớp cùng loại giáo viên
          (VN/nước ngoài) của buổi cũ. */}
      <p className="text-[11px] text-slate-500">
        {t("classDetail.rescheduleForm.teacherAutoHint", {
          teacher: session.primaryTeacherName,
          type: session.teacherType ? ` — ${teacherTypeLabel(t, session.teacherType)}` : ""
        })}
      </p>

      <div>
        <label className={labelClass}>{t("classDetail.rescheduleForm.reasonLabel")}</label>
        <input
          value={form.reason}
          onChange={(e) => setForm({ ...form, reason: e.target.value })}
          className={inputClass}
          placeholder={t("classDetail.rescheduleForm.reasonPlaceholder")}
        />
      </div>

      <div className="flex justify-end gap-2 pt-1">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("common.cancelButton")}
        </Button>
        <Button type="submit" size="sm" disabled={submitting}>
          {submitting ? t("common.saving") : t("classDetail.rescheduleForm.submitButton")}
        </Button>
      </div>
    </form>
  );
}

function CreateSessionForm({ classId, siteId, onDone, onCancel }: { classId: number; siteId: number; onDone: () => void; onCancel: () => void }) {
  const { t } = useTranslation("academic-classes");
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("classDetail.sessions.loadError")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form.sessionType, classId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.sessionDate || !form.startTime || !form.endTime) {
      setError(t("classDetail.createSessionForm.dateTimeRequired"));
      return;
    }
    if (!form.teacherType) {
      setError(t("classDetail.createSessionForm.teacherTypeRequired"));
      return;
    }
    if (form.sessionType === "MAKEUP" && !makeupForSessionId) {
      setError(t("classDetail.createSessionForm.makeupRequired"));
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
        sessionType: form.sessionType,
        teacherType: form.teacherType as "VIETNAMESE" | "FOREIGN",
        makeupForSessionId: form.sessionType === "MAKEUP" ? Number(makeupForSessionId) : undefined
      };
      await createClassSession(classId, request);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classDetail.createSessionForm.submitError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-3 gap-2">
        <div>
          <label className={labelClass}>{t("classDetail.createSessionForm.sessionDateLabel")}</label>
          <DatePicker value={form.sessionDate} onChange={(v) => setForm({ ...form, sessionDate: v })} />
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.createSessionForm.startTimeLabel")}</label>
          <input type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} className={inputClass} required />
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.createSessionForm.endTimeLabel")}</label>
          <input type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} className={inputClass} required />
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2">
        <div>
          <label className={labelClass}>{t("classDetail.createSessionForm.sessionTypeLabel")}</label>
          <Select value={form.sessionType} onChange={(e) => setForm({ ...form, sessionType: e.target.value })} className={inputClass}>
            <option value="REGULAR">{sessionTypeLabel(t, "REGULAR")}</option>
            <option value="REVIEW">{sessionTypeLabel(t, "REVIEW")}</option>
            <option value="EXAM">{sessionTypeLabel(t, "EXAM")}</option>
            <option value="MAKEUP">{sessionTypeLabel(t, "MAKEUP")}</option>
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.createSessionForm.teacherTypeLabel")}</label>
          <Select value={form.teacherType} onChange={(e) => setForm({ ...form, teacherType: e.target.value })} className={inputClass}>
            <option value="">{t("common.teacherTypePlaceholder")}</option>
            <option value="VIETNAMESE">{teacherTypeLabel(t, "VIETNAMESE")}</option>
            <option value="FOREIGN">{teacherTypeLabel(t, "FOREIGN")}</option>
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("classDetail.createSessionForm.roomLabel")}</label>
          <Select value={roomId} onChange={(e) => setRoomId(e.target.value)} className={inputClass}>
            <option value="">{t("common.noneOption")}</option>
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
          <label className={labelClass}>{t("classDetail.createSessionForm.makeupForLabel")}</label>
          <Select value={makeupForSessionId} onChange={(e) => setMakeupForSessionId(e.target.value)} className={inputClass}>
            <option value="">{t("classDetail.createSessionForm.makeupForPlaceholder")}</option>
            {cancelledPendingMakeup.map((s) => (
              <option key={s.id} value={s.id}>
                {t("classDetail.createSessionForm.makeupOption", { number: s.sessionNumber, date: s.sessionDate, start: s.startTime, end: s.endTime })}
              </option>
            ))}
          </Select>
          {cancelledPendingMakeup.length === 0 && (
            <p className="text-[10px] text-slate-400 italic mt-1">{t("classDetail.createSessionForm.noCancelledSessions")}</p>
          )}
        </div>
      )}

      {/* Bổ sung ngoài SDD gốc, xác nhận 2026-08-13: giáo viên phụ trách KHÔNG còn chọn tay — hệ
          thống tự động lấy giáo viên chính (PRIMARY) đang phụ trách lớp cùng loại giáo viên đã chọn. */}
      <p className="text-[11px] text-slate-500">{t("classDetail.createSessionForm.teacherAutoHint")}</p>

      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("common.cancelButton")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? t("common.saving") : t("classDetail.createSessionForm.submitButton")}
        </Button>
      </div>
    </form>
  );
}
