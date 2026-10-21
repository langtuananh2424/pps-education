import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { BACKEND_URL, DEV_PASSWORD, authHeaders, createAndEnrollStudent, findUsableClass, login } from "./api";

/**
 * Chấm lần 2 câu ESSAY của Bài tập Writing do AI chấm (2026-10-20) — popup "Lịch sử chấm AI" trong chi tiết học sinh
 * ở trang Thống kê BTVN theo lớp: bản chấm hiện hành mang cờ "Điểm chính thức", bản AI cũ "Không tính", giáo viên
 * chỉnh điểm + nhận xét ở bản hiện hành (điểm AI gốc được giữ).
 *
 * Dựng dữ liệu qua API thật: Đề → câu ESSAY → Bài → giao BTVN → học sinh làm + nộp bài.
 * NGOẠI LỆ so với nguyên tắc "không insert SQL" của bộ e2e: máy dev không có khoá AI nên KHÔNG có cách để AI chấm
 * thật — chỉ riêng bản chấm AI (student_answer_grading, grading_source='AI') được chèn bằng SQL, đúng hình dạng
 * ExerciseAttemptService#gradeAndFinalize ghi ra. Việc chấm lại của giáo viên đi qua API/UI thật.
 */
function sql(statement: string): string {
  return execFileSync("docker", ["exec", "pps-education-db", "psql", "-U", "pps_app", "-d", process.env.E2E_DB ?? "pps_education", "-At", "-c", statement], {
    encoding: "utf8"
  }).trim();
}
const firstLine = (out: string) => out.split("\n")[0];

interface Fixture {
  assignmentId: number;
  studentId: number;
  studentName: string;
  answerId: number;
  attemptId: number;
}
let fx: Fixture;

async function ok(res: import("@playwright/test").APIResponse, what: string) {
  expect(res.ok(), `${what} thất bại: ${res.status()} ${await res.text()}`).toBeTruthy();
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

test.beforeAll(async ({ request }) => {
  const res = await request.post(`${BACKEND_URL}/api/auth/login`, { data: { usernameOrEmail: "teacher", password: DEV_PASSWORD, confirm: true } });
  expect(res.ok(), "Backend :8080 chưa chạy").toBeTruthy();

  const token = await login(request, "sysadmin");
  const h = authHeaders(token);
  const ts = Date.now();
  const teacherId = Number(sql("select id from users where username='teacher'"));
  const { classId, classSessionId } = await findUsableClass(request, token);
  if (sql(`select count(*) from class_teachers where class_id=${classId} and teacher_user_id=${teacherId} and assigned_to is null`) === "0") {
    sql(`insert into class_teachers (class_id, teacher_user_id, teacher_role, assigned_by, assigned_from) values (${classId}, ${teacherId}, 'PRIMARY', 1, now())`);
  }

  const sessions = (await (await request.get(`${BACKEND_URL}/api/classes/${classId}/sessions`, { headers: h })).json()) as Array<{ id: number; teacherType?: string }>;
  const teacherType = sessions.find((s) => s.id === classSessionId)?.teacherType === "VIETNAMESE" ? "VIETNAMESE" : "FOREIGN";
  const curricula = await ok(await request.get(`${BACKEND_URL}/api/curriculums`, { headers: h }), "GET curriculums");
  const curriculumId = curricula[0].id as number;

  const exam = await ok(
    await request.post(`${BACKEND_URL}/api/exams`, { headers: h, data: { code: `E2E-ES-${ts}`, title: `E2E Essay ${ts}`, curriculumId, teacherType, examType: "HOMEWORK" } }),
    "POST exams"
  );
  await ok(await request.post(`${BACKEND_URL}/api/exams/${exam.id}/classes/${classId}`, { headers: h }), "gán Đề cho lớp");
  const question = await ok(
    await request.post(`${BACKEND_URL}/api/exams/${exam.id}/questions`, {
      headers: h,
      data: { questionType: "ESSAY", skill: "WRITING", content: `Write about your weekend ${ts}`, defaultPoints: 10 }
    }),
    "POST câu ESSAY vào Đề"
  );
  const exercise = await ok(
    await request.post(`${BACKEND_URL}/api/exercises`, {
      headers: h,
      data: { code: `E2E-ES-EX-${ts}`, title: `E2E Essay ${ts}`, examId: exam.id, exerciseType: "ASSIGNED", totalPoints: 10, allowRetake: false, showCorrectAnswers: false, skillCategory: "VOCAB_GRAMMAR" }
    }),
    "POST exercises"
  );
  await ok(await request.post(`${BACKEND_URL}/api/exercises/${exercise.id}/questions`, { headers: h, data: { questionId: question.id, displayOrder: 1, points: 10 } }), "thêm câu vào Bài");
  await ok(await request.post(`${BACKEND_URL}/api/exercises/${exercise.id}/publish`, { headers: h }), "publish Bài");

  // Học sinh mới ghi danh TRƯỚC khi giao bài (apply-homework giao cho mọi học sinh ACTIVE lúc đó).
  const studentId = await createAndEnrollStudent(request, token, classId);
  const student = await ok(await request.get(`${BACKEND_URL}/api/students/${studentId}`, { headers: h }), "GET student");
  const stamp = String(student.studentCode).split("-").pop();
  const studentName = `E2E Student ${stamp}`;
  await ok(
    await request.post(`${BACKEND_URL}/api/class-sessions/${classSessionId}/comments/apply-homework`, {
      headers: h,
      data: { grammarExamId: exam.id, grammarExerciseIds: [exercise.id], lateSubmissionAllowed: true }
    }),
    "giao BTVN (apply-homework)"
  );
  const assignmentId = Number(firstLine(sql(`select id from exercise_assignments where exercise_id=${exercise.id} order by id desc limit 1`)));

  // Học sinh làm + nộp bài qua API thật.
  const studentToken = await login(request, `e2e_stu_${stamp}`, "E2eTest@123456");
  const sh = authHeaders(studentToken);
  const attempt = await ok(await request.post(`${BACKEND_URL}/api/exercises/${exercise.id}/attempts?assignmentId=${assignmentId}`, { headers: sh }), "bắt đầu làm bài");
  await ok(
    await request.post(`${BACKEND_URL}/api/attempts/${attempt.id}/answers`, { headers: sh, data: { questionId: question.id, answerText: "I goes to the park on weekend." } }),
    "lưu câu trả lời"
  );
  await ok(await request.post(`${BACKEND_URL}/api/attempts/${attempt.id}/submit`, { headers: sh }), "nộp bài");

  const answerId = Number(firstLine(sql(`select id from student_answers where exercise_attempt_id=${attempt.id} and question_id=${question.id}`)));
  // Bản chấm AI (xem Javadoc đầu file): điểm 5/10, bài viết đánh dấu lỗi ngữ pháp.
  sql(`delete from student_answer_grading where student_answer_id=${answerId}`);
  sql(
    `insert into student_answer_grading (student_answer_id, grader_user_id, score, max_score, feedback, marked_answer, graded_at, is_final, grading_source) ` +
      `values (${answerId}, 1, 5, 10, 'AI: còn lỗi chia động từ', 'I {{gr2|goes}} to the park on weekend.', now(), true, 'AI')`
  );

  fx = { assignmentId, studentId, studentName, answerId, attemptId: attempt.id as number };
});

test("Popup Lịch sử chấm AI câu ESSAY: cờ điểm chính thức và giáo viên chấm lại được", async ({ page }) => {
  await page.goto("/login");
  await page.locator('input[type="text"]').first().fill("teacher");
  await page.locator('input[type="password"]').fill(DEV_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });

  await page.goto(`/academic/homework-stats/${fx.assignmentId}`);
  const row = page.locator("tr", { hasText: fx.studentName });
  await expect(row).toBeVisible();
  await row.locator("button[title]").last().click(); // icon con mắt "Xem chi tiết"

  await page.getByRole("button", { name: "Lịch sử chấm AI" }).click();
  const modal = page.locator("div.fixed.inset-0.z-50").last();
  await expect(modal.getByTestId("essay-history-modal")).toBeVisible();
  const box = await modal.locator("div.relative").first().boundingBox();
  expect(box!.width, "popup phải đủ rộng").toBeGreaterThan(1000);

  const official = modal.getByTestId("essay-version-official");
  await expect(official).toHaveCount(1);
  await expect(official).toContainText("AI chấm");
  await expect(official).toContainText("5/10");
  await page.screenshot({ path: "screenshots/essay2-popup-1.png" });

  await official.getByRole("button", { name: "Chỉnh điểm" }).click();
  await official.getByTestId("essay-score-input").fill("8");
  await official.getByTestId("essay-feedback-input").fill("Cô thấy bài viết tốt hơn AI đánh giá");
  await official.getByRole("button", { name: "Lưu điểm" }).click();

  // Sau khi lưu: bản giáo viên là điểm chính thức, bản AI cũ "Không tính" và vẫn hiện điểm AI gốc.
  const teacherVersion = modal.getByTestId("essay-version-official");
  await expect(teacherVersion).toContainText("Giáo viên chấm");
  await expect(teacherVersion).toContainText("8/10");
  await expect(teacherVersion).toContainText("AI chấm: 5");
  await expect(teacherVersion).toContainText("Cô thấy bài viết tốt hơn AI đánh giá");
  const old = modal.getByTestId("essay-version-old");
  await expect(old).toHaveCount(1);
  await expect(old).toContainText("Không tính");
  await expect(old).toContainText("5/10");
  await expect(old.getByRole("button", { name: "Chỉnh điểm" })).toHaveCount(0);
  await page.screenshot({ path: "screenshots/essay2-popup-2.png" });

  expect(sql(`select string_agg(grading_source||':'||score||':'||is_final, ',' order by id) from student_answer_grading where student_answer_id=${fx.answerId}`))
    .toBe("AI:5.00:false,HUMAN:8.00:true");
  // Bản AI cũ vẫn giữ bài viết đã đánh dấu lỗi, và bản giáo viên mang theo nó để học sinh vẫn thấy lỗi bị bôi.
  expect(sql(`select count(*) from student_answer_grading where student_answer_id=${fx.answerId} and marked_answer like '%{{gr2|goes}}%'`)).toBe("2");
});

test("Tài khoản ngoài lớp và không có quyền quản trị không xem được lịch sử (403)", async ({ request }) => {
  const token = await login(request, "opsmanager");
  const res = await request.get(`${BACKEND_URL}/api/exercise-assignments/${fx.assignmentId}/students/${fx.studentId}/essay-grading-history`, { headers: authHeaders(token) });
  expect([401, 403]).toContain(res.status());
});

test("Admin không đứng lớp xem được lịch sử, bản chính thức là bản chấm hiện hành", async ({ request }) => {
  const token = await login(request, "sysadmin");
  const res = await request.get(`${BACKEND_URL}/api/exercise-assignments/${fx.assignmentId}/students/${fx.studentId}/essay-grading-history`, { headers: authHeaders(token) });
  expect(res.ok(), await res.text()).toBeTruthy();
  const history = (await res.json()) as Array<{ studentAnswerId: number; versions: Array<{ gradingSource: string; official: boolean }> }>;
  expect(history).toHaveLength(1);
  expect(history[0].studentAnswerId).toBe(fx.answerId);
  expect(history[0].versions.filter((v) => v.official)).toHaveLength(1);
});
