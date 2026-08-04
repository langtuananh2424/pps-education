/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Khớp đúng 11 mã vai trò thật của backend (V4__seed_roles_and_permissions.sql) — KHÔNG tự thêm/bịa mã khác.
export enum UserRole {
  STUDENT = "STUDENT", // Học sinh
  PARENT = "PARENT", // Phụ huynh
  TEACHER = "TEACHER", // Giáo viên
  HEAD_ACADEMIC = "HEAD_ACADEMIC", // Trưởng phòng đào tạo
  SITE_MANAGER = "SITE_MANAGER", // Quản lý điểm trường
  HR_MANAGER = "HR_MANAGER", // Quản lý nhân sự
  STAFF = "STAFF", // Nhân viên (tuyển sinh/CSKH/giáo vụ)
  SYS_ADMIN = "SYS_ADMIN", // Quản trị viên
  SUPER_ADMIN = "SUPER_ADMIN", // Siêu quản trị viên
  PARTNER_REP = "PARTNER_REP", // Đại diện trường liên kết
  OPS_MANAGER = "OPS_MANAGER", // Quản lý vận hành
  EXECUTIVE = "EXECUTIVE" // Ban giám đốc
}

export interface Permission {
  id: string;
  code: string; // e.g. "class.create", "grade.approve"
  name: string;
  module: string;
  description: string;
  apiEndpoints?: string[]; // Granular APIs bound to this permission
  status?: 'active' | 'inactive';
}

export interface RolePermission {
  role: UserRole | string;
  permissions: string[]; // List of permission codes
}

export interface PermissionAuditLog {
  id: string;
  actorName: string;
  targetName: string;
  action: "ROLE_GRANTED" | "ROLE_REVOKED" | "PERM_OVERRIDE_ADDED" | "PERM_OVERRIDE_REMOVED";
  details: string;
  createdAt: string;
}

export interface Task {
  id: string;
  title: string;
  description: string;
  assignedTo: string; // Employee ID or name
  assignedBy: string; // Employee ID or name
  departmentId: string;
  deadline: string;
  status: "TODO" | "IN_PROGRESS" | "WAITING_APPROVAL" | "COMPLETED";
  attachments?: string[];
  comments?: TaskComment[];
  createdAt: string;
}

export interface TaskComment {
  id: string;
  authorName: string;
  content: string;
  createdAt: string;
}

export interface Employee {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  role: UserRole;
  campusIds: string[]; // multi-campus support
  degrees: string[];
  certificates: string[];
  contracts: Contract[];
  rewards: string[];
  disciplines: string[];
  joinedDate: string;
  status: "ACTIVE" | "INACTIVE";
}

export interface Contract {
  id: string;
  type: "LABOR" | "COLLABORATOR" | "PROBATION";
  startDate: string;
  endDate?: string;
  baseSalary: number;
}

export interface AttendanceLog {
  id: string;
  employeeId: string;
  employeeName: string;
  time: string;
  method: "FINGERPRINT" | "FACE" | "GPS" | "MANUAL";
  campusId: string;
  status: "SUCCESS" | "OUT_OF_BOUNDS" | "FAILED";
}

export interface LeaveRequest {
  id: string;
  employeeId: string;
  employeeName: string;
  departmentName: string;
  type: "LEAVE" | "LATE" | "EARLY";
  startDate: string;
  endDate: string;
  reason: string;
  steps: ApprovalStep[];
  currentStepIndex: number;
  status: "DRAFT" | "PENDING" | "APPROVED" | "REJECTED";
  createdAt: string;
  isPublicSubmitted?: boolean;
}

export interface ApprovalStep {
  role: UserRole;
  approverName?: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  notes?: string;
  updatedAt?: string;
}

export interface Student {
  id: string; // Mã ID duy nhất
  fullName: string;
  birthDate: string;
  avatarUrl?: string;
  phone: string;
  parentName: string;
  parentPhone: string;
  campusId: string; // Điểm trường gán
  classIds: string[];
  status: "STUDYING" | "RESERVED" | "SUSPENDED" | "GRADUATED";
  history: string[]; // History logs
}

export interface Classroom {
  id: string;
  name: string;
  type: "AFFILIATED" | "CENTER"; // Lớp liên kết trường / Lớp mở tại trung tâm
  campusId: string; // Điểm trường (Trường liên kết / Cơ sở tự vận hành)
  courseId: string; // Gán khóa học tương ứng
  teacherId: string; // Giáo viên được điều phối
  roomName: string; // Tên phòng học
  maxStudents: number;
  schedule: string; // e.g. "T2, T4 (18:00 - 19:30)"
}

export interface Course {
  id: string;
  name: string;
  code: string;
  description?: string;
  syllabusOriginalId?: string;
}

export interface GradeEntry {
  id: string;
  studentId: string;
  studentName: string;
  classId: string;
  className: string;
  midtermScore?: number;
  finalScore?: number;
  averageScore?: number;
  batchId?: string; // Grouping ID for approval
  status: "DRAFT" | "PENDING" | "APPROVED" | "REJECTED";
  teacherComments?: string;
  notes?: string;
  updatedAt: string;
}

export interface CommentEntry {
  id: string;
  studentId: string;
  studentName: string;
  classId: string;
  className: string;
  type: "DAILY" | "MIDTERM" | "FINAL";
  content: string;
  isWarning: boolean; // Cờ cảnh báo đặc biệt
  batchId?: string;
  status: "DRAFT" | "PENDING" | "APPROVED" | "REJECTED";
  rejectionReason?: string;
  createdAt: string;
}

export interface Lecture {
  id: string;
  title: string;
  description: string;
  fileType: "VIDEO" | "PDF";
  cdnUrl: string;
  courseId: string;
  views: number;
}

export interface Exam {
  id: string;
  title: string;
  courseId: string;
  type: "QUIZ" | "ESSAY" | "SPEAKING";
  questionsCount: number;
  deadline?: string;
}

export interface StudentExamSubmission {
  id: string;
  examId: string;
  examTitle: string;
  studentId: string;
  studentName: string;
  className: string;
  score?: number;
  submittedAt: string;
  status: "SUBMITTED" | "GRADING" | "GRADED";
  teacherFeedback?: string;
  skillsScores?: {
    listening?: number;
    speaking?: number;
    reading?: number;
    writing?: number;
  };
}

export interface Invoice {
  id: string;
  studentId: string;
  studentName: string;
  parentName: string;
  amount: number;
  scholarshipApplied?: string;
  discountAmount?: number;
  finalAmount: number;
  dueDate: string;
  status: "PAID" | "PARTIAL_PAID" | "OVERDUE" | "PENDING";
  paidAmount: number;
  qrUrl: string; // Dynamic simulated banking QR
  createdAt: string;
  campusId?: string;
}

export interface Expense {
  id: string;
  category: "SALARY" | "RENT" | "LICENSE" | "CDN_INFRA" | "OTHER";
  amount: number;
  description: string;
  campusId?: string;
  createdAt: string;
}

export interface Lead {
  id: string;
  fullName: string;
  phone: string;
  email: string;
  source: string; // e.g. "Website", "Facebook", "Marketing"
  status: "NEW" | "CONTACTED" | "QUALIFIED" | "LOST" | "WON";
  counselorId?: string;
  counselorName?: string;
  history: string[]; // Logs of calls/meetings
  createdAt: string;
}

export interface Campus {
  id: string;
  name: string;
  address: string;
  type: "SELF_OPERATED" | "AFFILIATED"; // Cơ sở tự vận hành / Trường liên kết
  contactPerson?: string;
  contractStatus?: "ACTIVE" | "PENDING" | "EXPIRED" | "TERMINATED";
  contractExpiryDate?: string;
  managerId?: string;
  managerName?: string;
}

export interface ClassroomRoom {
  id: string;
  campusId: string;
  roomName: string;
  capacity: number;
  type: "THEORY" | "COMPUTER" | "LAB";
  isFlexible: boolean; // Phòng linh hoạt (loại trừ cảnh báo trùng phòng)
  status: "AVAILABLE" | "OCCUPIED" | "MAINTENANCE";
}

export interface FeedbackTicket {
  id: string;
  campusId: string;
  campusName: string;
  senderName: string;
  category: "TEACHER" | "CLASSROOM" | "OPERATION" | "OTHER";
  content: string;
  priority: "HIGH" | "MEDIUM" | "LOW";
  status: "NEW" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
  resolutionText?: string;
  history: { text: string; time: string; author: string }[];
  createdAt: string;
}

export interface SyllabusPlan {
  id: string;
  classId: string;
  className: string;
  type: "WEEKLY" | "YEARLY";
  title: string;
  content: string;
  authorName: string;
  updatedAt: string;
}

/** Khớp shape phân trang thật của Spring Data `Page<T>` — dùng chung cho mọi API GET có phân trang (xem API.md). */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // Trang hiện tại, 0-based
  size: number;
}
