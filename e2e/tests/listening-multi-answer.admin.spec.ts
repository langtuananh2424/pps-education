import { test, expect } from "@playwright/test";
import { BACKEND_URL, DEV_PASSWORD, authHeaders, login } from "./api";

/**
 * Nghe trắc nghiệm nhiều đáp án (LISTENING_MULTIPLE_ANSWER, 2026-10-19) — form soạn tay ở
 * "Ngân hàng câu hỏi" phải có loại mới, bắt buộc audio, cho tích NHIỀU đáp án đúng.
 * KHÔNG upload audio thật (tránh đụng R2): chỉ kiểm tra UI + validate "thiếu audio".
 */
test.beforeAll(async ({ request }) => {
  // Không dùng /actuator/health: khi tắt SMTP (MAIL_HOST rỗng) health tổng báo DOWN dù app vẫn chạy.
  const res = await request.post(`${BACKEND_URL}/api/auth/login`, { data: { usernameOrEmail: "sysadmin", password: DEV_PASSWORD, confirm: true } });
  expect(res.ok(), "Backend :8080 chưa chạy — docker compose up -d backend").toBeTruthy();
});

/** Dựng sẵn 1 ngân hàng câu hỏi (qua API) cho khung chương trình đầu tiên nếu chưa có — modal tạo câu hỏi cần ngân hàng. */
async function ensureQuestionBank(request: import("@playwright/test").APIRequestContext) {
  const token = await login(request, "sysadmin");
  const curriculums = await (await request.get(`${BACKEND_URL}/api/curriculums`, { headers: authHeaders(token) })).json();
  const curriculumId = curriculums[0].id as number;
  const banks = await (await request.get(`${BACKEND_URL}/api/curriculums/${curriculumId}/question-banks`, { headers: authHeaders(token) })).json();
  if (banks.length === 0) {
    const res = await request.post(`${BACKEND_URL}/api/question-banks`, {
      headers: authHeaders(token),
      data: { code: "E2E-QB-LMA", name: "E2E Ngân hàng nghe", curriculumId }
    });
    expect(res.ok(), `tạo ngân hàng thất bại: ${res.status()} ${await res.text()}`).toBeTruthy();
  }
}

test("Form soạn tay: loại 'Nghe trắc nghiệm nhiều đáp án' — audio bắt buộc + tích nhiều đáp án đúng", async ({ page, request }) => {
  await ensureQuestionBank(request);
  await page.goto("/login");
  await page.locator('input[type="text"]').first().fill("sysadmin");
  await page.locator('input[type="password"]').fill(DEV_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });

  await page.goto("/lms/question-banks");
  await page.getByRole("button", { name: "Thêm câu hỏi mới" }).click();
  const modal = page.locator("div.fixed.inset-0.z-50").last();
  await expect(modal).toBeVisible();
  await page.screenshot({ path: "screenshots/lma-1-modal.png" });

  // Mở nhóm "Trắc nghiệm" rồi chọn loại mới
  await modal.getByRole("button", { name: /^Trắc nghiệm/i }).first().click();
  const kindBtn = modal.getByRole("button", { name: "Nghe trắc nghiệm nhiều đáp án" });
  await expect(kindBtn).toBeVisible();
  await kindBtn.click();

  // Có khối upload audio (bắt buộc cho kind Nghe)
  await expect(modal.getByText("File Audio bài nghe")).toBeVisible();

  // 4 đáp án, mặc định A,B được tích đúng → bấm thêm C: phải có 3 dấu tích
  const optionInputs = modal.getByPlaceholder(/^Đáp án [A-D]/);
  await expect(optionInputs).toHaveCount(4);
  await modal.locator("textarea[required]").first().fill("Which TWO reasons does Irina give?");
  for (let i = 0; i < 4; i++) await optionInputs.nth(i).fill(`option ${i + 1}`);
  const ticks = modal.locator("button.bg-emerald-500");
  await expect(ticks).toHaveCount(2);
  await modal.locator("button", { hasText: /^C$/ }).first().click();
  await expect(ticks).toHaveCount(3);
  await page.screenshot({ path: "screenshots/lma-2-form.png" });

  // Lưu khi chưa có audio → báo lỗi đúng thông điệp mới
  await modal.getByRole("button", { name: /Lưu|Tạo/ }).last().click();
  await expect(page.getByText("Nghe trắc nghiệm nhiều đáp án cần có URL audio bài nghe.")).toBeVisible();
  await page.screenshot({ path: "screenshots/lma-3-error.png" });
});
