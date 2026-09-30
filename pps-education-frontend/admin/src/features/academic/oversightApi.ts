import { apiRequest, apiRequestBlob } from "@/lib/apiClient";
import type { Page } from "@/types";

/**
 * API cho các chức năng giám sát đào tạo của Trưởng phòng đào tạo (V203 — bổ sung ngoài SDD gốc, xác nhận
 * với người dùng 2026-09-30): lịch sử thay đổi dữ liệu, hồ sơ giáo viên, thống kê giảng dạy theo giáo viên,
 * dashboard số liệu thật.
 */

// ===================== Lịch sử thay đổi dữ liệu =====================

export type ChangeHistoryEntityType =
  | "CLASS"
  | "CLASS_SESSION"
  | "CLASS_ENROLLMENT"
  | "CLASS_TEACHER"
  | "STUDENT"
  | "EMPLOYEE";

export interface ChangeHistoryItem {
  id: string;
  entityType: ChangeHistoryEntityType;
  entityId: number;
  action: "CREATED" | "UPDATED";
  classId: number | null;
  className: string | null;
  classCode: string | null;
  studentId: number | null;
  subjectName: string | null;
  subjectCode: string | null;
  sessionDate: string | null;
  details: Record<string, unknown> | null;
  previousDetails: Record<string, unknown> | null;
  /** Nhãn cho giá trị là khoá tham chiếu, khoá dạng "tênTrường:giáTrị". */
  valueLabels: Record<string, string>;
  changedById: number;
  changedByName: string;
  changedAt: string;
}

export interface ChangeHistoryFilter {
  entityType?: ChangeHistoryEntityType | "";
  fromDate?: string;
  toDate?: string;
  siteId?: number;
  classId?: number;
  studentId?: number;
  keyword?: string;
  page: number;
  size: number;
}

export function searchChangeHistory(filter: ChangeHistoryFilter): Promise<Page<ChangeHistoryItem>> {
  const qs = new URLSearchParams({ page: String(filter.page), size: String(filter.size) });
  if (filter.entityType) qs.set("entityType", filter.entityType);
  if (filter.fromDate) qs.set("fromDate", filter.fromDate);
  if (filter.toDate) qs.set("toDate", filter.toDate);
  if (filter.siteId) qs.set("siteId", String(filter.siteId));
  if (filter.classId) qs.set("classId", String(filter.classId));
  if (filter.studentId) qs.set("studentId", String(filter.studentId));
  if (filter.keyword?.trim()) qs.set("keyword", filter.keyword.trim());
  return apiRequest<Page<ChangeHistoryItem>>(`/change-history?${qs.toString()}`);
}

// ===================== Hồ sơ giáo viên =====================

export interface TeacherProfileSummary {
  employeeId: number;
  userId: number;
  employeeCode: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  portraitUrl: string | null;
  positionName: string | null;
  departmentName: string | null;
  status: "ACTIVE" | "ON_LEAVE" | "TERMINATED";
  hireDate: string | null;
  siteNames: string[];
  activeClassCount: number;
}

export interface TeacherQualification {
  id: number;
  qualificationType: "DEGREE" | "PEDAGOGY_CERT" | "LANGUAGE_CERT" | "OTHER";
  title: string;
  issuer: string | null;
  issuedDate: string | null;
  expiryDate: string | null;
  fileUrl: string | null;
}

export interface TeacherCommendation {
  id: number;
  recordType: "COMMENDATION" | "DISCIPLINE";
  recordDate: string | null;
  title: string;
}

export interface TeacherClassAssignment {
  classId: number;
  classCode: string;
  className: string;
  siteName: string;
  classStatus: string;
  teacherRole: string;
  teacherType: string | null;
  assignedFrom: string | null;
}

export interface TeacherProfileDetail {
  profile: TeacherProfileSummary;
  qualifications: TeacherQualification[];
  commendations: TeacherCommendation[];
  classes: TeacherClassAssignment[];
}

export function listTeacherProfiles(params?: { query?: string; siteId?: number }): Promise<TeacherProfileSummary[]> {
  const qs = new URLSearchParams();
  if (params?.query?.trim()) qs.set("query", params.query.trim());
  if (params?.siteId) qs.set("siteId", String(params.siteId));
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  return apiRequest<TeacherProfileSummary[]>(`/teacher-profiles${suffix}`);
}

export function getTeacherProfile(employeeId: number): Promise<TeacherProfileDetail> {
  return apiRequest<TeacherProfileDetail>(`/teacher-profiles/${employeeId}`);
}

// ===================== Thống kê giảng dạy theo giáo viên =====================

export interface TeacherTeachingStatsRow {
  teacherUserId: number | null;
  teacherName: string;
  employeeCode: string | null;
  classCount: number;
  scheduledSessions: number;
  heldSessions: number;
  cancelledSessions: number;
  taughtPeriods: number;
  onTimeCheckIns: number;
  lateCheckIns: number;
  missingCheckIns: number;
  onTimeRate: number | null;
}

export interface TeachingStatsResponse {
  fromDate: string;
  toDate: string;
  siteId: number | null;
  siteName: string | null;
  teachers: TeacherTeachingStatsRow[];
  totals: TeacherTeachingStatsRow;
}

function teachingStatsQuery(params: { siteId?: number; fromDate: string; toDate: string }): string {
  const qs = new URLSearchParams({ fromDate: params.fromDate, toDate: params.toDate });
  if (params.siteId) qs.set("siteId", String(params.siteId));
  return qs.toString();
}

export function getTeachingStats(params: { siteId?: number; fromDate: string; toDate: string }): Promise<TeachingStatsResponse> {
  return apiRequest<TeachingStatsResponse>(`/reports/teaching-stats?${teachingStatsQuery(params)}`);
}

export function exportTeachingStats(params: { siteId?: number; fromDate: string; toDate: string }): Promise<Blob> {
  return apiRequestBlob(`/reports/teaching-stats/export?${teachingStatsQuery(params)}`);
}

// ===================== Dashboard Trưởng phòng đào tạo =====================

export type DashboardCheckInState = "CANCELLED" | "ON_TIME" | "LATE" | "MISSING" | "NOT_STARTED";

export interface AcademicDashboardSession {
  sessionId: number;
  classId: number;
  className: string;
  classCode: string;
  siteName: string;
  teacherName: string;
  startTime: string;
  endTime: string;
  checkInState: DashboardCheckInState;
}

export interface AcademicDashboardResponse {
  siteId: number | null;
  siteName: string | null;
  plannedClasses: number;
  openEnrollmentClasses: number;
  inProgressClasses: number;
  activeStudents: number;
  activeTeachers: number;
  attendanceFromDate: string;
  attendanceToDate: string;
  attendanceTotalMarks: number;
  attendancePresentCount: number;
  attendanceLateCount: number;
  attendanceEarlyLeaveCount: number;
  attendanceExcusedCount: number;
  attendanceAbsentCount: number;
  attendanceRate: number | null;
  today: string;
  todaySessions: AcademicDashboardSession[];
  teacherAlerts: TeacherTeachingStatsRow[];
}

export function getAcademicDashboard(siteId?: number): Promise<AcademicDashboardResponse> {
  return apiRequest<AcademicDashboardResponse>(`/dashboard/academic-overview${siteId ? `?siteId=${siteId}` : ""}`);
}
