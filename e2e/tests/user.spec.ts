import { test, expect, request as playwrightRequest, Page } from "@playwright/test";
import {
  BACKEND_URL,
  createParentWithTwoChildren,
  enterAndPublishGrade,
  findRecentClassSession,
  findUsableClass,
  login,
  markAndSubmitAbsence,
  writeSubmitAndApproveAttitudeComment
} from "./api";

/**
 * Portal Học sinh/Phụ huynh — Plan link hoá thông báo (2026-09-22): bấm 1 thông báo ở quả chuông
 * phải đổi ĐÚNG CON trước khi đổi tab (Phụ huynh nhiều con — DoD mục 8 "đang xem con A, bấm thông
 * báo của con B"). Dữ liệu test (Phụ huynh mới + 2 con mới, điểm danh, điểm số) dựng qua API thật —
 * xem tests/api.ts. Test tự login qua FORM đăng nhập thật của Portal.
 */

async function loginPortal(page: Page, username: string, password: string) {
  await test.step(`Đăng nhập Portal: ${username}`, async () => {
    await page.goto("/login");
    await page.locator("#usernameOrEmail").fill(username);
    await page.locator("#password").fill(password);
    await page.locator('button[type="submit"]').click();
    // App.tsx không dùng react-router cho Portal — chỉ 1 ternary isLoggedIn ở gốc (LoginPage/PortalPage
    // render cùng URL "/login"), nên KHÔNG có URL đổi để chờ; chờ chuông thông báo (chỉ Portal mới có) xuất hiện.
    await expect(page.locator("button:has(svg.lucide-bell)")).toBeVisible({ timeout: 15_000 });
  });
}

/** Chuyển con đang xem qua PortalDropdown (icon Users) — dùng để CHỦ ĐỘNG set trạng thái ban đầu khác con mục tiêu trước khi bấm thông báo, đúng kịch bản DoD "đang xem con A, bấm thông báo của con B". */
async function selectChild(page: Page, childName: string) {
  await test.step(`Chuyển sang xem con: ${childName}`, async () => {
    const trigger = page.locator("button:has(svg.lucide-users)").first();
    await trigger.click();
    // Header cũng có 1 nút "Học viên {tên con}" chứa cùng tên (mở ProfileModal, không phải chọn con)
    // — scope đúng vào panel option list (div.absolute.z-20 do PortalDropdown tự vẽ) để không khớp nhầm.
    await page.locator("div.absolute.z-20 button", { hasText: childName }).first().click();
    await expect(trigger).toContainText(childName);
  });
}

async function openNotification(page: Page, matchText: string) {
  await page.locator('button:has(svg.lucide-bell)').click();
  // Panel thông báo là role="dialog" (NotificationBell.tsx) — scope vào đây để không khớp nhầm nút khác
  // trên trang có cùng text (VD tên học sinh cũng xuất hiện ở header/ProfileModal).
  const row = page.getByRole("dialog").locator("button", { hasText: matchText }).first();
  await expect(row).toBeVisible({ timeout: 10_000 });
  await row.click();
}

test.beforeAll(async () => {
  const res = await (await playwrightRequest.newContext()).get(`${BACKEND_URL}/api/notifications?page=0&size=1`);
  expect([200, 401, 403]).toContain(res.status());
});

test.describe("Đợt 1 — Portal Phụ huynh: bấm thông báo đổi đúng con + đúng tab", () => {
  test("ATTENDANCE_MARK — đang xem con A, bấm thông báo vắng học của con B → đổi đúng con + tab Lịch học", async ({
    page,
    request
  }) => {
    const sysadminToken = await login(request, "sysadmin");
    const usable = await findUsableClass(request, sysadminToken);
    const fixture = await createParentWithTwoChildren(request, sysadminToken, usable.classId);
    await markAndSubmitAbsence(request, sysadminToken, usable.classSessionId, fixture.studentBId);

    await loginPortal(page, fixture.parentUsername, fixture.parentPassword);
    await selectChild(page, fixture.studentAName);

    await openNotification(page, fixture.studentBName);

    await expect(page.locator("button:has(svg.lucide-users)").first()).toContainText(fixture.studentBName, { timeout: 10_000 });
    await expect(page.locator("button", { hasText: "Lịch học & Chuyên cần" })).toHaveClass(/bg-teal/);
  });

  test("GRADE_PUBLISHED — đang xem con B, bấm thông báo điểm của con A → đổi đúng con + tab Điểm số", async ({
    page,
    request
  }) => {
    const sysadminToken = await login(request, "sysadmin");
    const usable = await findUsableClass(request, sysadminToken);
    const fixture = await createParentWithTwoChildren(request, sysadminToken, usable.classId);
    await enterAndPublishGrade(request, sysadminToken, usable.classId, usable.gradeEvaluationComponentId, fixture.studentAId, 6.5);

    await loginPortal(page, fixture.parentUsername, fixture.parentPassword);
    await selectChild(page, fixture.studentBName);

    await openNotification(page, fixture.studentAName);

    await expect(page.locator("button:has(svg.lucide-users)").first()).toContainText(fixture.studentAName, { timeout: 10_000 });
    await expect(page.locator("button", { hasText: "Khảo thí & Điểm số" })).toHaveClass(/bg-teal/);
  });
});

test.describe("Đợt 2 — Portal Phụ huynh: bấm thông báo đổi đúng con + đúng buổi", () => {
  test("STUDENT_ATTITUDE_ALERT — đang xem con A, bấm thông báo thái độ của con B → đổi đúng con + mở đúng buổi ở tab Quá trình học tập", async ({
    page,
    request
  }) => {
    const sysadminToken = await login(request, "sysadmin");
    const teacherToken = await login(request, "teacher");
    const smToken = await login(request, "sitemanager");
    const usable = await findUsableClass(request, sysadminToken);
    const fixture = await createParentWithTwoChildren(request, sysadminToken, usable.classId);
    const session = await findRecentClassSession(request, sysadminToken, usable.classId);

    const commentContent = `E2E attitude comment ${Date.now()}`;
    // Duyệt (không phải chỉ gửi) mới bắn STUDENT_ATTITUDE_ALERT — xem StudentAttitudeAlertTrackingService,
    // chỉ trigger lúc StudentCommentService gọi evaluateAndNotify() trong luồng decideComments APPROVED.
    await writeSubmitAndApproveAttitudeComment(
      request,
      teacherToken,
      smToken,
      usable.classId,
      session.id,
      session.sessionDate,
      fixture.studentBId,
      commentContent
    );

    await loginPortal(page, fixture.parentUsername, fixture.parentPassword);
    await selectChild(page, fixture.studentAName);

    await openNotification(page, fixture.studentBName);

    await expect(page.locator("button:has(svg.lucide-users)").first()).toContainText(fixture.studentBName, { timeout: 10_000 });
    await expect(page.locator("button", { hasText: "Quá trình học tập" })).toHaveClass(/bg-teal/);
    // selectedSessionId được set đúng buổi → bảng chỉ còn hiện đúng 1 dòng, chứa nội dung nhận xét vừa viết.
    await expect(page.getByText(`"${commentContent}"`)).toBeVisible({ timeout: 10_000 });
  });
});
