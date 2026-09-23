import { test, expect, request as playwrightRequest } from "@playwright/test";
import {
  BACKEND_URL,
  DEV_PASSWORD,
  createLeaveRequest,
  createTaskAssignedTo,
  ensureEmployeeProfile,
  login,
  me
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
