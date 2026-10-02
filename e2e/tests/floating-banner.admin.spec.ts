import { expect, Page, test } from "@playwright/test";
import { BACKEND_URL, DEV_PASSWORD } from "./api";

/**
 * Banner thông báo nổi (FloatingBanner, 2026-10-01) — thay cho khối lỗi nằm trong trang/modal, toast thành công
 * góc dưới phải, dải cam NotificationBanner (Nhận xét/Duyệt) và popup báo lỗi alertDialog. Chụp ảnh từng kiểu
 * thông báo thật trên app admin để review giao diện; ảnh lưu ở e2e/screenshots/floating-banner/.
 * Lỗi tải danh sách dùng page.route giả lập 500 (không có cách gây lỗi server thật mà không phá dữ liệu);
 * các thông báo còn lại là luồng thật. Phần Nhận xét tự dựng lớp/buổi học/học sinh qua API (seedCommentClass).
 */
const SHOT_DIR = "screenshots/floating-banner";

async function loginAs(page: Page, username: string) {
  const res = await page.request.post(`${BACKEND_URL}/api/auth/login`, {
    data: { usernameOrEmail: username, password: DEV_PASSWORD, confirm: true }
  });
  expect(res.ok(), `login ${username}: ${res.status()}`).toBeTruthy();
  const { accessToken, refreshToken } = await res.json();
  const meRes = await page.request.get(`${BACKEND_URL}/api/auth/me`, { headers: { Authorization: `Bearer ${accessToken}` } });
  const me = await meRes.json();
  await page.addInitScript(
    ([a, r, u]) => {
      sessionStorage.setItem("pps_access_token", a);
      sessionStorage.setItem("pps_refresh_token", r);
      sessionStorage.setItem("pps_current_user", u);
    },
    [accessToken, refreshToken, JSON.stringify(me)]
  );
}

const STACK = "#floating-banner-stack";
const banner = (page: Page, variant: "error" | "success" | "warning" | "info" = "error") =>
  page.locator(`${STACK} [data-variant=${variant}]`);

async function expectBannerAtTopCenter(page: Page) {
  const box = await page.locator(STACK).boundingBox();
  const viewport = page.viewportSize()!;
  expect(box).not.toBeNull();
  expect(box!.y).toBeLessThan(40);
  expect(Math.abs(box!.x + box!.width / 2 - viewport.width / 2)).toBeLessThan(2);
}

test.describe("Banner lỗi nổi — app admin", () => {
  test("01 đăng nhập để trống", async ({ page }) => {
    await page.goto("/login");
    // Ô nhập có `required` (trình duyệt tự chặn ô rỗng) — nhập toàn khoảng trắng để tới validate của app (trim).
    await page.getByPlaceholder("username hoặc email@pps.edu.vn").fill("   ");
    await page.locator("input[type=password]").fill("x");
    await page.locator("button[type=submit]").click();
    await expect(banner(page)).toHaveText("Vui lòng điền tài khoản hoặc email đăng nhập.");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/01-dang-nhap-de-trong.png` });
  });

  test("02 sai tài khoản hoặc mật khẩu (backend 401)", async ({ page }) => {
    await page.goto("/login");
    // Username không tồn tại — tránh tăng failed_login_count làm khoá tài khoản demo.
    await page.getByPlaceholder("username hoặc email@pps.edu.vn").fill("khong-ton-tai-e2e");
    await page.locator("input[type=password]").fill("sai-mat-khau");
    await page.locator("button[type=submit]").click();
    await expect(banner(page)).toHaveText("Sai tài khoản hoặc mật khẩu.");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/02-sai-mat-khau.png` });

    // Nút đóng ẩn banner.
    await banner(page).getByRole("button").click();
    await expect(banner(page)).toHaveCount(0);
  });

  test("03 lỗi tải danh sách vai trò (giả lập API 500)", async ({ page }) => {
    await loginAs(page, "sysadmin");
    await page.route("**/api/roles", (route) =>
      route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "Không tải được danh sách vai trò (lỗi máy chủ giả lập)." }) })
    );
    await page.goto("/system-admin/roles");
    await expect(banner(page)).toHaveText(/Không tải được danh sách vai trò/);
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/03-loi-tai-trang.png` });
  });

  test("04 lỗi trong modal Nhập Excel (file sai định dạng)", async ({ page }) => {
    await loginAs(page, "sysadmin");
    await page.goto("/lms/lectures");
    await page.getByRole("button", { name: "Nhập Excel" }).click();
    // Lỗi i18n đã sửa: tiêu đề modal phải là chữ đã dịch, không phải khoá "importModal.title".
    await expect(page.getByText("Nhập Excel hàng loạt Bộ video ôn tập")).toBeVisible();
    await page.locator("input[type=file]").last().setInputFiles({ name: "thu-loi.txt", mimeType: "text/plain", buffer: Buffer.from("abc") });
    await expect(banner(page)).toHaveText("Chỉ chấp nhận file .xlsx.");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/04-loi-trong-modal-import.png` });
  });

  test("05 lỗi validate trong modal người dùng", async ({ page }) => {
    await loginAs(page, "sysadmin");
    await page.goto("/system-admin/users");
    await page.locator("tbody tr").first().getByRole("button").first().click();
    const pwd = page.getByPlaceholder("Mật khẩu mới (tối thiểu 8 ký tự)");
    await pwd.fill("123");
    await page.getByRole("button", { name: "Đặt lại mật khẩu" }).click();
    await expect(banner(page)).toHaveText("Mật khẩu mới phải từ 8 ký tự trở lên.");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/05-loi-validate-modal-nguoi-dung.png` });
  });

  test("06 nhiều lỗi cùng lúc xếp chồng", async ({ page }) => {
    await loginAs(page, "sysadmin");
    await page.route("**/api/review-video-sets*", (route) =>
      route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "Không tải được Kho Video (lỗi máy chủ giả lập)." }) })
    );
    await page.goto("/lms/lectures");
    await expect(banner(page)).toHaveCount(1);
    await page.getByRole("button", { name: "Nhập Excel" }).click();
    await page.locator("input[type=file]").last().setInputFiles({ name: "thu-loi.txt", mimeType: "text/plain", buffer: Buffer.from("abc") });
    await expect(banner(page)).toHaveCount(2);
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/06-nhieu-loi-xep-chong.png` });
  });

  test("07 giao diện mobile", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/login");
    await page.getByPlaceholder("username hoặc email@pps.edu.vn").fill("khong-ton-tai-e2e");
    await page.locator("input[type=password]").fill("sai-mat-khau");
    await page.locator("button[type=submit]").click();
    await expect(banner(page)).toHaveText("Sai tài khoản hoặc mật khẩu.");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/07-mobile.png` });
  });

  test("08 tài khoản Portal vào nhầm app admin — nhãn vai trò đã dịch", async ({ page }) => {
    await loginAs(page, "partnerrep");
    await page.goto("/dashboard");
    await expect(page.getByText("Tài khoản thuộc ứng dụng Portal")).toBeVisible();
    await expect(page.getByText("Đại Diện Trường Liên Kết", { exact: true })).toBeVisible();
    await expect(page.getByText("roles.PARTNER_REP")).toHaveCount(0);
    await page.screenshot({ path: `${SHOT_DIR}/08-wrong-portal-nhan-vai-tro.png` });
  });
});

/* ------------------------------------------------------------------------------------------------
 * Thông báo SAU KHI ĐĂNG NHẬP: thành công / cảnh báo / lỗi từ popup cũ — phần Nhận xét, Quản lý người
 * dùng, Nhóm vai trò. Dữ liệu dựng qua API thật (idempotent theo mã E2E-*), mỗi lượt chạy thêm 1 học sinh
 * mới để luôn có 1 dòng chưa nhận xét (học sinh chỉ được nhận xét DAILY 1 lần/buổi).
 * ---------------------------------------------------------------------------------------------- */
async function api(page: Page, method: string, path: string, token: string | null, data?: unknown) {
  const res = await page.request.fetch(`${BACKEND_URL}/api${path}`, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data
  });
  const text = await res.text();
  expect(res.ok(), `${method} ${path}: ${res.status()} ${text}`).toBeTruthy();
  return text ? JSON.parse(text) : null;
}

async function tokenOf(page: Page, username: string) {
  return (await api(page, "POST", "/auth/login", null, { usernameOrEmail: username, password: DEV_PASSWORD, confirm: true })).accessToken as string;
}

/** Lớp E2E-CLS-<ts> mới + buổi học hôm nay (tiết 00:05–00:50, luôn "đã bắt đầu") + 1 học sinh mới chưa có nhận xét. */
async function seedCommentClass(page: Page): Promise<{ classId: number; studentName: string }> {
  const admin = await tokenOf(page, "sysadmin");
  const teacher = await api(page, "GET", "/auth/me", await tokenOf(page, "teacher"));
  const today = new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Ho_Chi_Minh" });

  let site = (await api(page, "GET", "/sites", admin)).find((s: { code: string }) => s.code === "E2E-SITE");
  if (!site) site = await api(page, "POST", "/sites", admin, { code: "E2E-SITE", name: "Cơ sở E2E", siteType: "OWNED", usedForClasses: true, usedForAttendance: true });
  if (!(await api(page, "GET", `/sites/${site.id}/period-templates`, admin)).length)
    await api(page, "POST", `/sites/${site.id}/period-templates`, admin, { dayPart: "MORNING", periodNumber: 1, label: "Tiết 1", startTime: "00:05", endTime: "00:50" });

  const curs = await api(page, "GET", "/curriculums", admin);
  let cur = (Array.isArray(curs) ? curs : curs.content ?? []).find((c: { code: string }) => c.code === "E2E-CUR");
  if (!cur) cur = await api(page, "POST", "/curriculums", admin, { code: "E2E-CUR", name: "Khung E2E", classCategory: "MAIN", totalPeriods: 40 });
  if (cur.status !== "ACTIVE") await api(page, "PUT", `/curriculums/${cur.id}`, admin, { name: cur.name, totalPeriods: 40, status: "ACTIVE", confirm: true });

  const siteTeachers = await api(page, "GET", `/sites/${site.id}/teachers`, admin);
  if (!siteTeachers.some((t: { teacherUserId: number }) => t.teacherUserId === teacher.id))
    await api(page, "POST", `/sites/${site.id}/teachers`, admin, { teacherUserId: teacher.id, assignedFrom: "2026-09-01" });

  // Lớp MỚI mỗi lượt chạy: sau khi gửi nhận xét, buổi học bị khoá ô "Bài học hôm nay" (đúng nghiệp vụ) — dùng
  // lại lớp cũ thì lượt sau không lưu được nữa.
  const ts = Date.now();
  const cls = await api(page, "POST", "/classes", admin, { classCode: `E2E-CLS-${ts}`, name: `Lớp E2E Nhận xét ${ts}`, siteId: site.id, curriculumId: cur.id, classType: "OPEN", maxStudents: 50, startDate: "2026-09-01" });
  await api(page, "POST", `/classes/${cls.id}/teachers`, admin, { teacherUserId: teacher.id, role: "PRIMARY", assignedFrom: "2026-09-01" });
  await api(page, "POST", `/classes/${cls.id}/sessions`, admin, { sessionDate: today, dayPart: "MORNING", periodNumbers: [1], sessionType: "REGULAR", teacherType: "VIETNAMESE", primaryTeacherId: teacher.id, allowTeacherOverlap: true });

  const studentName = `Học sinh E2E ${ts}`;
  const st = await api(page, "POST", "/students", admin, {
    studentCode: `E2E-S-${ts}`,
    dateOfBirth: "2014-05-01",
    enrollmentDate: "2026-09-01",
    newAccount: { username: `e2e_stu_${ts}`, email: `e2e_stu_${ts}@pps.edu.vn`, fullName: studentName, password: "E2eTest@123456" }
  });
  await api(page, "POST", `/classes/${cls.id}/enrollments`, admin, { studentId: st.id, enrolledDate: "2026-09-01" });
  return { classId: cls.id, studentName };
}

test.describe.serial("Thông báo sau khi đăng nhập — Nhận xét & các trang khác", () => {
  let classId = 0;
  let studentName = "";

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    ({ classId, studentName } = await seedCommentClass(page));
    await page.close();
  });

  async function openComments(page: Page) {
    await loginAs(page, "teacher");
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(`/academic/comments?writeClassId=${classId}`);
    await expect(page.getByPlaceholder("VD: Unit 3 - Free time activities")).toBeVisible();
  }

  const studentRow = (page: Page) => page.locator("tr", { hasText: studentName });

  test("09 Nhận xét — lưu Bài học hôm nay (thành công)", async ({ page }) => {
    await openComments(page);
    await page.getByPlaceholder("VD: Unit 3 - Free time activities").fill(`Unit 1 - Hello (${Date.now()})`);
    await page.getByRole("button", { name: "Lưu", exact: true }).first().click();
    await expect(banner(page, "success")).toHaveText("Đã lưu Bài học hôm nay.");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/09-nhan-xet-luu-bai-hoc.png` });
  });

  test("10 Nhận xét — lưu nháp (thành công)", async ({ page }) => {
    await openComments(page);
    await studentRow(page).getByPlaceholder("Viết nhận xét cho học sinh này...").fill("Con học tập chăm chỉ, phát âm tiến bộ.");
    await page.getByRole("button", { name: "Lưu nháp" }).click();
    await expect(banner(page, "success")).toContainText("Đã lưu nháp");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/10-nhan-xet-luu-nhap.png` });
  });

  test("11 Nhận xét — gửi nhận xét", async ({ page }) => {
    await openComments(page);
    const content = studentRow(page).getByPlaceholder("Viết nhận xét cho học sinh này...");
    if (!(await content.inputValue())) await content.fill("Con học tập chăm chỉ, phát âm tiến bộ.");
    await page.getByRole("button", { name: "Gửi nhận xét" }).click();
    await expect(page.getByText("Xác nhận gửi nhận xét?")).toBeVisible();
    await page.getByRole("button", { name: "Gửi nhận xét" }).last().click();
    await expect(banner(page, "success")).toContainText("Đã gửi nhận xét");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/11-nhan-xet-gui.png` });
  });

  test("12 Nhận xét — bấm học sinh đã gửi (cảnh báo vàng)", async ({ page }) => {
    await openComments(page);
    await studentRow(page).locator("td").nth(2).click(); // cột Ngày sinh — bấm Họ tên sẽ mở Hồ sơ học tập
    await expect(banner(page, "warning")).toContainText("đã có nhận xét cho buổi này");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/12-nhan-xet-da-gui-canh-bao.png` });
  });

  test("13 Quản lý người dùng — lưu hồ sơ (toast thành công cũ → banner xanh)", async ({ page }) => {
    await loginAs(page, "sysadmin");
    await page.goto("/system-admin/users");
    await page.locator("tbody tr").first().getByRole("button").first().click();
    await page.getByRole("button", { name: "Lưu hồ sơ" }).click();
    await expect(banner(page, "success")).toBeVisible();
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/13-luu-ho-so-thanh-cong.png` });
  });

  test("14 Nhóm vai trò — xoá thất bại (popup lỗi cũ → banner đỏ, giả lập 409)", async ({ page }) => {
    const admin = await tokenOf(page, "sysadmin");
    const code = `E2E_ROLE_${Date.now()}`;
    const role = await api(page, "POST", "/roles", admin, { code, name: `Vai trò E2E ${code.slice(-5)}`, description: "Tạo bởi e2e", dataScope: "ALL" });
    await loginAs(page, "sysadmin");
    await page.route(`**/api/roles/${role.id}`, (route) =>
      route.request().method() === "DELETE"
        ? route.fulfill({ status: 409, contentType: "application/json", body: JSON.stringify({ message: "Không thể xoá vai trò đang có thành viên (lỗi giả lập)." }) })
        : route.continue()
    );
    await page.goto("/system-admin/roles");
    await page.getByText(role.name).first().click();
    await page.getByRole("button", { name: "Xóa vai trò" }).click();
    await page.getByRole("button", { name: "Xác nhận", exact: true }).last().click();
    await expect(banner(page, "error")).toHaveText("Không thể xoá vai trò đang có thành viên (lỗi giả lập).");
    await expectBannerAtTopCenter(page);
    await page.screenshot({ path: `${SHOT_DIR}/14-xoa-vai-tro-loi.png` });
    await page.unrouteAll();
    await api(page, "DELETE", `/roles/${role.id}`, admin).catch(() => undefined);
  });
});
