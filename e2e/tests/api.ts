import { APIRequestContext, expect } from "@playwright/test";

/**
 * Helper gọi thẳng REST API backend (localhost:8080) để dựng dữ liệu test — KHÔNG insert SQL trực
 * tiếp (theo yêu cầu người dùng 2026-09-23). Mọi tài khoản/bản ghi test đi qua đúng luồng nghiệp vụ
 * thật (POST /api/students, /api/class-sessions/{id}/attendance, /api/grades/decision...) nên tạo
 * ra thông báo với entityType/entityId/metadata giống hệt khi thao tác qua UI thật — Playwright chỉ
 * verify lại đúng luồng CLICK THÔNG BÁO qua trình duyệt thật (mục đích chính của bộ test này).
 *
 * Tài khoản demo cố định (DevUserSeeder, password DEV_PASSWORD) — xem
 * pps-education-backend/.../config/DevUserSeeder.java. Không đoán mật khẩu tài khoản khác.
 */
export const BACKEND_URL = "http://localhost:8080";
export const DEV_PASSWORD = "Dev@123456";

export async function login(request: APIRequestContext, usernameOrEmail: string, password = DEV_PASSWORD): Promise<string> {
  const res = await request.post(`${BACKEND_URL}/api/auth/login`, { data: { usernameOrEmail, password } });
  expect(res.ok(), `login thất bại cho ${usernameOrEmail}: ${res.status()} ${await res.text()}`).toBeTruthy();
  const body = await res.json();
  return body.accessToken as string;
}

export function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}` };
}

/** GET /api/auth/me — dùng để lấy userId/permissions của 1 token đã login. */
export async function me(request: APIRequestContext, token: string) {
  const res = await request.get(`${BACKEND_URL}/api/auth/me`, { headers: authHeaders(token) });
  expect(res.ok(), `GET /auth/me thất bại: ${res.status()}`).toBeTruthy();
  return res.json();
}

async function postJson(request: APIRequestContext, token: string, path: string, data: unknown) {
  const res = await request.post(`${BACKEND_URL}${path}`, { headers: authHeaders(token), data });
  expect(res.ok(), `POST ${path} thất bại: ${res.status()} ${await res.text()}`).toBeTruthy();
  return res.json();
}

async function getJson(request: APIRequestContext, token: string, path: string) {
  const res = await request.get(`${BACKEND_URL}${path}`, { headers: authHeaders(token) });
  expect(res.ok(), `GET ${path} thất bại: ${res.status()} ${await res.text()}`).toBeTruthy();
  return res.json();
}

export interface E2EParentFixture {
  parentId: number;
  parentUsername: string;
  parentPassword: string;
  studentAId: number; // "con A" — dùng cho luồng GRADE_ENTRY
  studentBId: number; // "con B" — dùng cho luồng ATTENDANCE_MARK
  studentAName: string;
  studentBName: string;
  classId: number;
}

/**
 * Dựng 1 Phụ huynh + 2 con hoàn toàn mới qua API (POST /api/parents, /api/students,
 * /api/students/{id}/parents, /api/classes/{classId}/enrollments) — thay vì tái dùng tài khoản demo
 * có sẵn (ph.nguyen...) vì không biết mật khẩu thật của các tài khoản đó (tạo thủ công ngoài
 * DevUserSeeder). Ghi danh vào lớp demo có sẵn CLASS_ID_WITH_DATA (đã có session/điểm/BTVN thật).
 */
export async function createParentWithTwoChildren(
  request: APIRequestContext,
  sysadminToken: string,
  classId: number
): Promise<E2EParentFixture> {
  const ts = Date.now();
  const parentUsername = `e2e_parent_${ts}`;
  const parentPassword = "E2eTest@123456";

  const parent = await postJson(request, sysadminToken, "/api/parents", {
    newAccount: { username: parentUsername, email: `${parentUsername}@pps.edu.vn`, fullName: `E2E Parent ${ts}`, password: parentPassword }
  });

  const studentAName = `E2E Student A ${ts}`;
  const studentBName = `E2E Student B ${ts}`;
  const studentA = await postJson(request, sysadminToken, "/api/students", {
    studentCode: `E2E-A-${ts}`,
    dateOfBirth: "2013-05-01",
    enrollmentDate: "2026-09-23",
    newAccount: { username: `e2e_stu_a_${ts}`, email: `e2e_stu_a_${ts}@pps.edu.vn`, fullName: studentAName, password: "E2eTest@123456" }
  });
  const studentB = await postJson(request, sysadminToken, "/api/students", {
    studentCode: `E2E-B-${ts}`,
    dateOfBirth: "2013-05-01",
    enrollmentDate: "2026-09-23",
    newAccount: { username: `e2e_stu_b_${ts}`, email: `e2e_stu_b_${ts}@pps.edu.vn`, fullName: studentBName, password: "E2eTest@123456" }
  });

  await postJson(request, sysadminToken, `/api/students/${studentA.id}/parents`, {
    parentId: parent.id,
    relationship: "MOTHER",
    isPrimaryContact: true
  });
  await postJson(request, sysadminToken, `/api/students/${studentB.id}/parents`, {
    parentId: parent.id,
    relationship: "MOTHER",
    isPrimaryContact: true
  });

  await postJson(request, sysadminToken, `/api/classes/${classId}/enrollments`, { studentId: studentA.id, enrolledDate: "2026-09-23" });
  await postJson(request, sysadminToken, `/api/classes/${classId}/enrollments`, { studentId: studentB.id, enrolledDate: "2026-09-23" });

  return {
    parentId: parent.id,
    parentUsername,
    parentPassword,
    studentAId: studentA.id,
    studentBId: studentB.id,
    studentAName,
    studentBName,
    classId
  };
}

/**
 * Điểm danh vắng cho 1 học sinh trên 1 buổi có sẵn của lớp classId, rồi SUBMIT (notification
 * ATTENDANCE_MARK chỉ bắn lúc submit, xem StudentAttendanceService.submitAttendance — không phải
 * lúc tạo mark). Trả về id buổi (classSessionId) đã dùng để test có thể log lại nếu cần.
 */
export async function markAndSubmitAbsence(
  request: APIRequestContext,
  sysadminToken: string,
  classSessionId: number,
  studentId: number
) {
  await postJson(request, sysadminToken, `/api/class-sessions/${classSessionId}/attendance`, {
    mode: "SESSION_LEVEL",
    marks: [{ studentId, status: "ABSENT" }]
  });
  await postJson(request, sysadminToken, `/api/class-sessions/${classSessionId}/attendance/submit`, {});
}

/** Nhập điểm → submit → duyệt (publish) cho 1 học sinh — notification GRADE_PUBLISHED chỉ bắn lúc duyệt. */
export async function enterAndPublishGrade(
  request: APIRequestContext,
  sysadminToken: string,
  classId: number,
  gradeEvaluationComponentId: number,
  studentId: number,
  score: number
) {
  const entry = await postJson(request, sysadminToken, `/api/classes/${classId}/grades/components/${gradeEvaluationComponentId}`, {
    studentId,
    score
  });
  await postJson(request, sysadminToken, "/api/grades/submit", { gradeEntryIds: [entry.id] });
  await postJson(request, sysadminToken, "/api/grades/decision", { action: "APPROVE", gradeEntryIds: [entry.id] });
  return entry.id as number;
}

/**
 * Đảm bảo user (theo username) có hồ sơ nhân sự (employees) — POST /api/tasks yêu cầu ACTOR (người
 * tạo việc) có employee profile, một số tài khoản demo (VD opsmanager) có quyền task.assign nhưng
 * chưa có hồ sơ nhân sự sẵn. Idempotent: bỏ qua nếu đã tồn tại (409/400 coi như đã có).
 */
export async function ensureEmployeeProfile(request: APIRequestContext, sysadminToken: string, userId: number, employeeCode: string) {
  const existing = await getJson(request, sysadminToken, "/api/employees");
  if ((existing as Array<{ userId: number }>).some((e) => e.userId === userId)) return;
  const res = await request.post(`${BACKEND_URL}/api/employees`, {
    headers: authHeaders(sysadminToken),
    data: { userId, employeeCode, employeeType: "STAFF", dateOfBirth: "1990-01-01", hireDate: "2026-01-01" }
  });
  expect(res.ok(), `POST /api/employees thất bại: ${res.status()} ${await res.text()}`).toBeTruthy();
}

/** Tạo 1 task giao cho assigneeUserId (actor phải có employee profile + task.assign — xem ensureEmployeeProfile). */
export async function createTaskAssignedTo(request: APIRequestContext, actorToken: string, title: string, assigneeUserId: number) {
  return postJson(request, actorToken, "/api/tasks", { title, assigneeUserIds: [assigneeUserId] });
}

/** Nộp đơn nghỉ phép (actor = người nộp, phải có employee profile) — bước duyệt đầu luôn role-based (OPS_MANAGER). */
export async function createLeaveRequest(request: APIRequestContext, actorToken: string, reason: string) {
  return postJson(request, actorToken, "/api/leave-requests", {
    leaveType: "ANNUAL",
    reason,
    startDate: "2026-10-01",
    endDate: "2026-10-01"
  });
}

export { getJson, postJson };

/**
 * Dò 1 lớp demo có sẵn "IN_PROGRESS" + có buổi học (không CANCELLED) + có ít nhất 1 bộ tiêu chí điểm
 * (grade-component-setups) — dùng làm lớp ghi danh 2 con test vào (xem createParentWithTwoChildren).
 * Không hardcode classId: môi trường dev khác có thể có id khác, dò qua API để bộ test portable hơn.
 */
export async function findUsableClass(request: APIRequestContext, sysadminToken: string) {
  const classes = await getJson(request, sysadminToken, "/api/classes?query=");
  for (const cls of classes as Array<{ id: number; status: string }>) {
    if (cls.status !== "IN_PROGRESS") continue;
    const sessions = await getJson(request, sysadminToken, `/api/classes/${cls.id}/sessions`);
    const usableSession = (sessions as Array<{ id: number; status: string }>).find((s) => s.status !== "CANCELLED");
    if (!usableSession) continue;
    const setups = await getJson(request, sysadminToken, `/api/classes/${cls.id}/grade-component-setups`);
    if ((setups as unknown[]).length === 0) continue;
    const setupId = (setups as Array<{ id: number }>)[0].id;
    const components = await getJson(request, sysadminToken, `/api/grade-component-setups/${setupId}/components`);
    if ((components as unknown[]).length === 0) continue;
    return {
      classId: cls.id,
      classSessionId: usableSession.id,
      gradeEvaluationComponentId: (components as Array<{ id: number }>)[0].id
    };
  }
  throw new Error("Không tìm thấy lớp demo nào IN_PROGRESS có sẵn buổi học + bộ tiêu chí điểm — cần seed thêm dữ liệu demo trước khi chạy suite này.");
}

/**
 * Buổi học gần nhất (không CANCELLED) của lớp — dùng cho luồng nhận xét (StudentComment chỉ tạo/sửa
 * được trong vòng 7 ngày kể từ ngày buổi học, xem StudentCommentService) khác với buổi dùng cho điểm
 * danh/điểm (không giới hạn ngày) nên tách hàm riêng, không dùng chung classSessionId của findUsableClass.
 */
export async function findRecentClassSession(request: APIRequestContext, sysadminToken: string, classId: number) {
  const sessions = (await getJson(request, sysadminToken, `/api/classes/${classId}/sessions`)) as Array<{
    id: number;
    status: string;
    sessionDate: string;
  }>;
  const usable = sessions
    .filter((s) => s.status !== "CANCELLED")
    .sort((a, b) => (a.sessionDate < b.sessionDate ? 1 : -1))[0];
  if (!usable) throw new Error(`Lớp id=${classId} không có buổi học nào (không CANCELLED) để tạo nhận xét test.`);
  return usable;
}

/** Nhập điểm → submit → TỪ CHỐI (khác enterAndPublishGrade ở action REJECT) — notification GRADE_REJECTED bắn cho người nhập điểm. */
export async function enterAndRejectGrade(
  request: APIRequestContext,
  enterAsToken: string,
  rejectAsToken: string,
  classId: number,
  gradeEvaluationComponentId: number,
  studentId: number,
  score: number,
  rejectReason: string
) {
  const entry = await postJson(request, enterAsToken, `/api/classes/${classId}/grades/components/${gradeEvaluationComponentId}`, {
    studentId,
    score
  });
  await postJson(request, enterAsToken, "/api/grades/submit", { gradeEntryIds: [entry.id] });
  await postJson(request, rejectAsToken, "/api/grades/decision", {
    action: "REJECT",
    gradeEntryIds: [entry.id],
    rejectReason
  });
  return entry.id as number;
}

/**
 * Viết → gửi duyệt → TỪ CHỐI 1 nhận xét (COMMENT_REJECTED) — notification bắn cho Giáo viên đã viết
 * (teacherToken). Phải điền "lesson-content" cho buổi trước khi gửi duyệt (ràng buộc nghiệp vụ —
 * "Buổi học này chưa điền bài học hôm nay").
 */
export async function writeSubmitAndRejectComment(
  request: APIRequestContext,
  teacherToken: string,
  approverToken: string,
  classId: number,
  classSessionId: number,
  sessionDate: string,
  studentId: number,
  content: string,
  rejectReason: string
) {
  await request.put(`${BACKEND_URL}/api/class-sessions/${classSessionId}/comments/lesson-content`, {
    headers: authHeaders(teacherToken),
    data: { lessonContent: "E2E test lesson" }
  });
  const comment = await postJson(request, teacherToken, `/api/classes/${classId}/comments`, {
    studentId,
    commentDate: sessionDate,
    commentType: "DAILY",
    content,
    classSessionId
  });
  await postJson(request, teacherToken, `/api/classes/${classId}/comments/submit`, { commentIds: [comment.id] });
  await postJson(request, approverToken, "/api/comments/decision", {
    commentIds: [comment.id],
    decision: "REJECTED",
    comment: rejectReason
  });
  return comment.id as number;
}

/**
 * Viết (kèm attitude WEAK/AVERAGE) → gửi duyệt → DUYỆT 1 nhận xét (STUDENT_ATTITUDE_ALERT bắn cho
 * Phụ huynh khi duyệt — xem StudentAttitudeAlertTrackingService, chỉ trigger lúc APPROVE).
 */
export async function writeSubmitAndApproveAttitudeComment(
  request: APIRequestContext,
  teacherToken: string,
  approverToken: string,
  classId: number,
  classSessionId: number,
  sessionDate: string,
  studentId: number,
  content: string
) {
  await request.put(`${BACKEND_URL}/api/class-sessions/${classSessionId}/comments/lesson-content`, {
    headers: authHeaders(teacherToken),
    data: { lessonContent: "E2E test lesson" }
  });
  const comment = await postJson(request, teacherToken, `/api/classes/${classId}/comments`, {
    studentId,
    commentDate: sessionDate,
    commentType: "DAILY",
    content,
    classSessionId,
    attitude: "WEAK"
  });
  await postJson(request, teacherToken, `/api/classes/${classId}/comments/submit`, { commentIds: [comment.id] });
  await postJson(request, approverToken, "/api/comments/decision", { commentIds: [comment.id], decision: "APPROVED" });
  return comment.id as number;
}

/** Lấy 1 studentId ACTIVE bất kỳ đã ghi danh sẵn trong lớp — dùng cho test không cần "đúng con Phụ huynh nào", chỉ cần 1 học sinh hợp lệ trong lớp. */
export async function pickEnrolledStudentId(request: APIRequestContext, sysadminToken: string, classId: number): Promise<number> {
  const enrollments = (await getJson(request, sysadminToken, `/api/classes/${classId}/enrollments`)) as Array<{
    studentId: number;
    status: string;
  }>;
  const active = enrollments.find((e) => e.status === "ACTIVE");
  if (!active) throw new Error(`Lớp id=${classId} không có học sinh ACTIVE nào đã ghi danh.`);
  return active.studentId;
}

/** Tạo 1 học sinh mới (không kèm phụ huynh) + ghi danh vào lớp — dùng cho test không cần Phụ huynh, chỉ cần 1 học sinh CHƯA có điểm/nhận xét nào (tránh đụng dữ liệu OFFICIAL có sẵn từ lượt seed/chạy trước). */
export async function createAndEnrollStudent(request: APIRequestContext, sysadminToken: string, classId: number): Promise<number> {
  const ts = Date.now();
  const student = await postJson(request, sysadminToken, "/api/students", {
    studentCode: `E2E-S-${ts}`,
    dateOfBirth: "2013-05-01",
    enrollmentDate: "2026-09-23",
    newAccount: { username: `e2e_stu_${ts}`, email: `e2e_stu_${ts}@pps.edu.vn`, fullName: `E2E Student ${ts}`, password: "E2eTest@123456" }
  });
  await postJson(request, sysadminToken, `/api/classes/${classId}/enrollments`, { studentId: student.id, enrolledDate: "2026-09-23" });
  return student.id as number;
}
