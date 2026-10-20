import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { BACKEND_URL, DEV_PASSWORD, authHeaders, createAndEnrollStudent, login } from "./api";

/**
 * Chấm lần 2 bài Video phản xạ do AI chấm (2026-10-19) — ngay trong popup "Lịch sử chấm AI" của trang
 * Thống kê BTVN theo lớp: cờ "Điểm chính thức" ở lần nộp mới nhất của từng bước, giáo viên chỉnh điểm ở đó.
 *
 * NGOẠI LỆ so với nguyên tắc "không insert SQL" của bộ e2e: máy dev không có khoá AI nên KHÔNG có cách nào
 * để AI chấm thật qua API. Học sinh vẫn tạo/ghi danh qua API thật; chỉ Bộ Video, bản giao và kết quả AI đã
 * chấm (reflex_question_progress + lịch sử) được chèn bằng SQL — đúng hình dạng ReflexSequentialGradingService ghi ra.
 */
function sql(statement: string): string {
  return execFileSync("docker", ["exec", "pps-education-db", "psql", "-U", "pps_app", "-d", "pps_education", "-At", "-c", statement], {
    encoding: "utf8"
  }).trim();
}
const firstLine = (out: string) => out.split("\n")[0];

interface Fixture {
  classId: number;
  assignmentId: number;
  studentId: number;
  studentName: string;
  progressId: number;
}
let fx: Fixture;

test.beforeAll(async ({ request }) => {
  const res = await request.post(`${BACKEND_URL}/api/auth/login`, { data: { usernameOrEmail: "teacher", password: DEV_PASSWORD, confirm: true } });
  expect(res.ok(), "Backend :8080 chưa chạy").toBeTruthy();

  const token = await login(request, "sysadmin");
  const ts = Date.now();
  const teacherId = Number(sql("select id from users where username='teacher'"));
  const classId = Number(sql("select cs.class_id from class_sessions cs join classes c on c.id=cs.class_id where cs.status<>'CANCELLED' and c.status='IN_PROGRESS' order by cs.id limit 1"));
  if (sql(`select count(*) from class_teachers where class_id=${classId} and teacher_user_id=${teacherId} and assigned_to is null`) === "0") {
    sql(`insert into class_teachers (class_id, teacher_user_id, teacher_role, assigned_by, assigned_from) values (${classId}, ${teacherId}, 'PRIMARY', 1, now())`);
  }
  const studentId = await createAndEnrollStudent(request, token, classId);
  const stamp = String((await (await request.get(`${BACKEND_URL}/api/students/${studentId}`, { headers: authHeaders(token) })).json()).studentCode).split("-").pop();
  const studentName = `E2E Student ${stamp}`;

  const curriculumId = Number(sql("select id from curriculums order by id limit 1"));
  const setId = Number(firstLine(sql(`insert into review_video_sets (code, title, video_type, curriculum_id, created_by, teacher_type, status) values ('E2E-AI2-${ts}', 'Bộ E2E AI2', 'REFLEX', ${curriculumId}, 1, 'VIETNAMESE', 'PUBLISHED') returning id`)));
  const videoId = Number(firstLine(sql(`insert into review_videos (review_video_set_id, title, source_type, file_url, duration_seconds) values (${setId}, 'Video E2E', 'R2_VIDEO', 'https://example.test/v.mp4', 60) returning id`)));
  const questionId = Number(firstLine(sql(`insert into review_video_questions (review_video_id, timestamp_seconds, max_recording_seconds, prompt) values (${videoId}, 10, 30, 'Describe your weekend') returning id`)));
  const assignmentId = Number(firstLine(sql(`insert into review_video_assignments (review_video_set_id, class_id, assigned_by) values (${setId}, ${classId}, 1) returning id`)));

  // Bước Viết đã nộp 2 lần (lần 2 = điểm chính thức 40), bước Nói nộp 1 lần (55).
  const progressId = Number(firstLine(sql(
    `insert into reflex_question_progress (review_video_question_id, student_id, review_video_assignment_id, answer_text, writing_score, writing_max_score, writing_feedback, writing_marked_answer, writing_graded_at, writing_attempt_count, ` +
      `audio_url, speaking_score, speaking_max_score, speaking_feedback, speaking_transcript, speaking_graded_at, speaking_attempt_count) ` +
      `values (${questionId}, ${studentId}, ${assignmentId}, 'I plays football', 40, 100, 'AI: ngữ pháp còn lỗi', 'I {{err}}plays{{/err}} football', now(), 2, ` +
      `'https://example.test/a.mp3', 55, 100, 'AI: nói chưa rõ ý', 'I {{err}}plays{{/err}} football on weekend', now(), 1) returning id`
  )));
  const history = (type: string, n: number, score: number) =>
    sql(
      `insert into reflex_question_progress_history (reflex_question_progress_id, review_video_question_id, student_id, review_video_assignment_id, attempt_type, attempt_number, score, max_score, feedback, graded_at) ` +
        `values (${progressId}, ${questionId}, ${studentId}, ${assignmentId}, '${type}', ${n}, ${score}, 100, 'AI lần ${n}', now())`
    );
  history("WRITING", 1, 30);
  history("WRITING", 2, 40);
  history("SPEAKING", 1, 55);

  fx = { classId, assignmentId, studentId, studentName, progressId };
});

test("Popup Lịch sử chấm AI: rộng, có cờ điểm chính thức và giáo viên chỉnh điểm được", async ({ page }) => {
  await page.goto("/login");
  await page.locator('input[type="text"]').first().fill("teacher");
  await page.locator('input[type="password"]').fill(DEV_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });

  await page.goto(`/academic/homework-stats/review-video/${fx.assignmentId}`);
  const row = page.locator("tr", { hasText: fx.studentName });
  await expect(row).toBeVisible();
  await row.locator("button[title]").last().click(); // icon con mắt "Xem chi tiết"

  const modal = page.locator("div.fixed.inset-0.z-50").last();
  await expect(modal.getByText("Lịch sử chấm AI")).toBeVisible();
  const box = await modal.locator("div.relative").first().boundingBox();
  expect(box!.width, "popup phải đủ rộng để chia 2 cột Viết | Ghi âm").toBeGreaterThan(1000);

  // Mỗi bước đúng 1 lần mang cờ "Điểm chính thức": Viết lần 2 (mới nhất), Ghi âm lần 1; Viết lần 1 là "Không tính".
  await expect(modal.getByText("Điểm chính thức", { exact: true })).toHaveCount(2);
  const writing1 = modal.locator("div.rounded-lg", { hasText: "Viết — lần 1" }).last();
  const writing2 = modal.locator("div.rounded-lg", { hasText: "Viết — lần 2" }).last();
  await expect(writing1).toContainText("Không tính");
  await expect(writing1.getByRole("button", { name: "Chỉnh điểm" })).toHaveCount(0); // lần cũ không chỉnh được
  await expect(writing2).toContainText("Điểm chính thức");
  await page.screenshot({ path: "screenshots/ai2-popup-1.png" });

  // Chỉnh điểm lần chính thức của bước Viết
  await writing2.getByRole("button", { name: "Chỉnh điểm" }).click();
  await writing2.locator('input[type="number"]').fill("85");
  await writing2.locator("textarea").fill("Cô thấy bài đúng ngữ pháp");
  await writing2.getByRole("button", { name: "Lưu điểm" }).click();

  await expect(writing2).toContainText("85/100");
  await expect(writing2).toContainText("AI chấm: 40");
  await expect(writing2).toContainText("GV đã chỉnh");
  await expect(writing2).toContainText("Cô thấy bài đúng ngữ pháp");
  await page.screenshot({ path: "screenshots/ai2-popup-2.png" });

  expect(sql(`select writing_score||'|'||writing_ai_score||'|'||(writing_reviewed_at is not null)||'|'||(speaking_reviewed_at is null) from reflex_question_progress where id=${fx.progressId}`))
    .toBe("85.00|40.00|true|true");
});

test("Tài khoản ngoài lớp và không có quyền quản trị bị chặn (403)", async ({ request }) => {
  const token = await login(request, "opsmanager");
  const res = await request.post(`${BACKEND_URL}/api/reflex-progress/${fx.progressId}/review/SPEAKING`, { headers: authHeaders(token), data: { score: 90 } });
  expect([401, 403]).toContain(res.status());
  expect(sql(`select speaking_score from reflex_question_progress where id=${fx.progressId}`)).toBe("55.00");
});

test("Admin không đứng lớp chấm lại được và lịch sử trả về cờ chính thức đúng", async ({ request }) => {
  const token = await login(request, "sysadmin");
  expect(sql("select count(*) from class_teachers where teacher_user_id=1 and assigned_to is null")).toBe("0");
  const res = await request.post(`${BACKEND_URL}/api/reflex-progress/${fx.progressId}/review/SPEAKING`, { headers: authHeaders(token), data: { score: 62, feedback: "Admin chấm lại" } });
  expect(res.ok(), await res.text()).toBeTruthy();

  const history = (await (await request.get(`${BACKEND_URL}/api/review-video-assignments/${fx.assignmentId}/stats/students/${fx.studentId}/reflex-history`, { headers: authHeaders(token) })).json()) as Array<{
    attemptType: string; attemptNumber: number; official: boolean; reviewed: boolean; score: number; aiScore: number | null;
  }>;
  const official = history.filter((h) => h.official).map((h) => `${h.attemptType}${h.attemptNumber}`).sort();
  expect(official).toEqual(["SPEAKING1", "WRITING2"]);
  expect(history.find((h) => h.attemptType === "SPEAKING")).toMatchObject({ reviewed: true, score: 62, aiScore: 55 });
});
