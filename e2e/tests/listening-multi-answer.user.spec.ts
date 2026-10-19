import { test, expect, Page } from "@playwright/test";
import { BACKEND_URL, authHeaders, login } from "./api";
import { LMA_STUDENT_PASSWORD, setupListeningMultiAnswerHomework } from "./lma-setup";

async function loginPortal(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.locator("#usernameOrEmail").fill(username);
  await page.locator("#password").fill(password);
  await page.locator('button[type="submit"]').click();
  const forceLogout = page.getByRole("button", { name: /đăng xuất và tiếp tục/i });
  if (await forceLogout.waitFor({ timeout: 3_000 }).then(() => true, () => false)) await forceLogout.click();
  await expect(page.locator("button:has(svg.lucide-bell)")).toBeVisible({ timeout: 15_000 });
}

test("Portal: nhóm Nghe nhiều đáp án (import Excel) — chọn nhiều đáp án, nộp, chấm từng phần", async ({ page, request }) => {
  const f = await setupListeningMultiAnswerHomework(request);

  // Import Excel: 2 dòng hợp lệ tạo thành 2 câu MULTIPLE_ANSWER/LISTENING, dòng 3 (chỉ 1 đáp án đúng) bị từ chối.
  expect(f.importResult.successRows).toBe(2);
  expect(f.importResult.failedRows).toBe(1);
  expect(f.importResult.errorSummary?.[0]?.reason).toContain("từ 2 đáp án đúng");
  expect(f.questions).toHaveLength(2);
  for (const q of f.questions) {
    expect(q.questionType).toBe("MULTIPLE_ANSWER");
    expect(q.skill).toBe("LISTENING");
    expect(q.choices.filter((c) => c.isCorrect)).toHaveLength(2);
  }

  await loginPortal(page, f.studentUsername, LMA_STUDENT_PASSWORD);
  await page.getByText("Bài tập về nhà (BTVN)").first().click();
  await page.getByText("Chưa phân loại Unit").first().click();
  await page.getByText("Ngữ pháp", { exact: true }).first().click();
  await page.getByText(`— ${f.exerciseTitle}`).locator("xpath=following::button[contains(.,'Làm bài ngay')][1]").click();

  const choice = (letter: string, text: string) => page.getByRole("button", { name: new RegExp(`^${letter}\.\s*${text}`) });
  await expect(page.getByText("Which TWO reasons does Irina give")).toBeVisible();

  // Câu 1: chọn B + D (đúng cả 2) — phải GIỮ được cả 2 lựa chọn (trước fix, bấm D sẽ thay B).
  await choice("B", "to improve her knowledge").click();
  await choice("D", "to entertain her child").click();
  await expect(choice("B", "to improve her knowledge")).toHaveClass(/bg-teal/);
  await expect(choice("D", "to entertain her child")).toHaveClass(/bg-teal/);
  // Bấm lại B → bỏ chọn, rồi chọn lại.
  await choice("B", "to improve her knowledge").click();
  await expect(choice("B", "to improve her knowledge")).not.toHaveClass(/bg-teal/);
  await choice("B", "to improve her knowledge").click();
  await expect(choice("B", "to improve her knowledge")).toHaveClass(/bg-teal/);

  // Câu 2 (đáp án đúng A,C): chọn A + C + B(sai) → 2 đúng - 1 sai = 1/2 điểm.
  await choice("A", "a calculator").click();
  await choice("C", "a camera").click();
  await choice("B", "a computer").click();
  await expect(choice("A", "a calculator")).toHaveClass(/bg-teal/);
  await expect(choice("C", "a camera")).toHaveClass(/bg-teal/);
  await expect(choice("B", "a computer")).toHaveClass(/bg-teal/);
  await page.screenshot({ path: "screenshots/lmu-6-selected.png", fullPage: true });

  await page.getByRole("button", { name: "Nộp bài" }).click();
  const confirm = page.getByRole("button", { name: /^(Nộp bài|Xác nhận|Đồng ý)/ }).last();
  if (await confirm.isVisible().catch(() => false)) await confirm.click();
  await page.waitForTimeout(2500);
  await page.screenshot({ path: "screenshots/lmu-7-submitted.png", fullPage: true });

  // Kiểm tra điểm qua API bằng chính tài khoản học sinh: Câu 1 = 2/2, Câu 2 = 1/2 → tổng 3/4.
  const studentToken = await login(request, f.studentUsername, LMA_STUDENT_PASSWORD);
  const attempts = (await (await request.get(`${BACKEND_URL}/api/exercises/${f.exerciseId}/attempts`, { headers: authHeaders(studentToken) })).json()) as Array<{ id: number; totalScore: number; status: string }>;
  expect(attempts).toHaveLength(1);
  expect(attempts[0].status).toBe("FULLY_GRADED");
  expect(Number(attempts[0].totalScore)).toBeCloseTo(3, 2);
  const answers = (await (await request.get(`${BACKEND_URL}/api/attempts/${attempts[0].id}/answers`, { headers: authHeaders(studentToken) })).json()) as Array<{ questionId: number; isCorrect: boolean; autoScore: number }>;
  const byQ = new Map(answers.map((a) => [a.questionId, a]));
  const [q1, q2] = f.questions;
  expect(byQ.get(q1.id)?.isCorrect).toBe(true);
  expect(Number(byQ.get(q1.id)?.autoScore)).toBeCloseTo(2, 2);
  expect(byQ.get(q2.id)?.isCorrect).toBe(false);
  expect(Number(byQ.get(q2.id)?.autoScore)).toBeCloseTo(1, 2);
});
