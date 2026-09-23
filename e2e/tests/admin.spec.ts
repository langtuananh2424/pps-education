import { test, expect, request as playwrightRequest } from "@playwright/test";
import {
  BACKEND_URL,
  DEV_PASSWORD,
  createLeaveRequest,
  createTaskAssignedTo,
  enterAndRejectGrade,
  ensureEmployeeProfile,
  findRecentClassSession,
  findUsableClass,
  login,
  me,
  createAndEnrollStudent,
  writeSubmitAndRejectComment
} from "./api";

/**
 * App admin — Plan link hoá thông báo (2026-09-22): bấm 1 thông báo ở quả chuông (Header.tsx) phải
 * điều hướng đúng route/modal theo resolveAdminNotificationTarget(). Dữ liệu test (task, đơn nghỉ
 * phép) tạo qua API thật (không SQL) bằng các tài khoản demo cố định của DevUserSeeder — xem
 * tests/api.ts. Test tự login qua FORM đăng nhập thật (không inject localStorage) để verify đúng
 * luồng người dùng thật sẽ đi qua.
 */

function loginAsync(page: import("@playwright/test").Page, username: string, password = DEV_PASSWORD) {
  return test.step(`Đăng nhập admin: ${username}`, async () => {
    await page.goto("/login");
    await page.locator('input[type="text"]').first().fill(username);
    await page.locator('input[type="password"]').fill(password);
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
  });
}

async function openNotification(page: import("@playwright/test").Page, matchText: string) {
  await page.locator('button:has(svg.lucide-bell)').click();
  const row = page.locator("button", { hasText: matchText }).first();
  await expect(row).toBeVisible({ timeout: 10_000 });
  await row.click();
}

test.beforeAll(async () => {
  const res = await (await playwrightRequest.newContext()).get(`${BACKEND_URL}/api/notifications?page=0&size=1`);
  expect([200, 401, 403]).toContain(res.status());
});

test.describe("Đợt 1 — App admin: bấm thông báo mở đúng đích", () => {
  test("TASK_ASSIGNED — mở đúng task + AssignmentDetailModal (Header → /task-workflow?taskId=)", async ({ page, request }) => {
    const sysadminToken = await login(request, "sysadmin");

    // opsmanager có quyền task.assign nhưng chưa chắc có hồ sơ nhân sự (actor tạo task cần employee
    // profile — xem TaskService.createTask) — đảm bảo có trước khi tạo task, idempotent.
    const opsToken = await login(request, "opsmanager");
    const opsUser = await me(request, opsToken);
    await ensureEmployeeProfile(request, sysadminToken, opsUser.id, "E2E-OPS-01");

    const teacherToken = await login(request, "teacher");
    const teacherUser = await me(request, teacherToken);

    const title = `E2E test task ${Date.now()}`;
    const task = await createTaskAssignedTo(request, opsToken, title, teacherUser.id);

    await loginAsync(page, "teacher");
    await openNotification(page, title);

    await expect(page).toHaveURL(new RegExp(`/task-workflow\\?taskId=${task.id}\\b`));
    // AssignmentDetailModal dùng chung component Modal, title = assignment.taskTitle → render <h3>.
    // Dùng getByRole('heading') vì title cũng lặp lại ở thẻ kanban phía sau modal (2 khớp nếu getByText thô).
    await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible({ timeout: 10_000 });
  });

  test("LEAVE_REQUEST_STATUS (chờ duyệt) — mở đúng dòng ở hàng chờ duyệt (Header → /hrm/leaves?leaveRequestId=)", async ({
    page,
    request
  }) => {
    const teacherToken = await login(request, "teacher");
    const reason = `E2E test leave request ${Date.now()}`;
    const leaveRequest = await createLeaveRequest(request, teacherToken, reason);

    // Bước duyệt đầu (currentStep=1, không specificApprover) luôn role-based — role OPS_MANAGER,
    // xem LeaveRequestService.notifyStepApprovers(). "opsmanager" là tài khoản demo giữ role đó.
    await loginAsync(page, "opsmanager");
    await openNotification(page, `#${leaveRequest.id} `);

    await expect(page).toHaveURL(new RegExp(`/hrm/leaves\\?leaveRequestId=${leaveRequest.id}\\b`));
    await expect(page.locator(`#leave-request-pending-${leaveRequest.id}`)).toBeVisible({ timeout: 10_000 });
  });
});

test.describe("Đợt 2 — App admin: bấm thông báo mở đúng đích", () => {
  test("GRADE_REJECTED — mở đúng lớp ở Sổ điểm (Header → /academic/grades?classId=)", async ({ page, request }) => {
    const sysadminToken = await login(request, "sysadmin");
    const smToken = await login(request, "sitemanager");
    const usable = await findUsableClass(request, sysadminToken);
    const studentId = await createAndEnrollStudent(request, sysadminToken, usable.classId);

    // sysadmin nhập điểm (academic.grade.manage) → sitemanager từ chối (academic.grade.approve) —
    // notification GRADE_REJECTED bắn cho người NHẬP (entry.getEnteredBy() = sysadmin ở đây).
    await enterAndRejectGrade(
      request,
      sysadminToken,
      smToken,
      usable.classId,
      usable.gradeEvaluationComponentId,
      studentId,
      6.5,
      `E2E reject reason ${Date.now()}`
    );

    const classInfo = await request
      .get(`${BACKEND_URL}/api/classes/${usable.classId}`, { headers: { Authorization: `Bearer ${sysadminToken}` } })
      .then((r) => r.json());

    await loginAsync(page, "sysadmin");
    await openNotification(page, "Điểm bị từ chối");

    await expect(page).toHaveURL(new RegExp(`/academic/grades\\?classId=${usable.classId}\\b`));
    // GradesPage (nhánh !isSiteManager, sysadmin không có role SITE_MANAGER) hiện "Lớp: {classCode} — {className}".
    await expect(page.getByText(`Lớp: ${classInfo.classCode} — ${classInfo.name}`)).toBeVisible({ timeout: 10_000 });
  });

  test("COMMENT_REJECTED — mở đúng lớp ở Viết nhận xét (Header → /academic/comments?writeClassId=)", async ({ page, request }) => {
    const sysadminToken = await login(request, "sysadmin");
    const teacherToken = await login(request, "teacher");
    const smToken = await login(request, "sitemanager");
    const usable = await findUsableClass(request, sysadminToken);
    const studentId = await createAndEnrollStudent(request, sysadminToken, usable.classId);
    const session = await findRecentClassSession(request, sysadminToken, usable.classId);

    await writeSubmitAndRejectComment(
      request,
      teacherToken,
      smToken,
      usable.classId,
      session.id,
      session.sessionDate,
      studentId,
      `E2E test comment ${Date.now()}`,
      "E2E sai buổi test"
    );

    const classInfo = await request
      .get(`${BACKEND_URL}/api/classes/${usable.classId}`, { headers: { Authorization: `Bearer ${sysadminToken}` } })
      .then((r) => r.json());

    await loginAsync(page, "teacher");
    await openNotification(page, "Nhận xét học sinh bị từ chối");

    await expect(page).toHaveURL(new RegExp(`/academic/comments\\?writeClassId=${usable.classId}\\b`));
    // DailyCommentPanel hiện "{className} ({classCode})" khi đã chọn đúng lớp.
    await expect(page.getByText(`${classInfo.name} (${classInfo.classCode})`)).toBeVisible({ timeout: 10_000 });
  });
});
