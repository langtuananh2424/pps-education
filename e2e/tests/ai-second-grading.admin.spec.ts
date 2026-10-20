import { test, expect, APIRequestContext } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { BACKEND_URL, DEV_PASSWORD, authHeaders, createAndEnrollStudent, login } from "./api";

/**
 * Chấm lần 2 bài AI đã chấm (2026-10-19) — tab "AI đã chấm — Chấm lần 2" ở trang Hàng chờ chấm bài.
 *
 * NGOẠI LỆ so với nguyên tắc "không insert SQL" của bộ e2e: máy dev không có khoá AI nên KHÔNG có cách nào
 * để AI chấm thật qua API. Bài tập/lượt làm/câu trả lời vẫn dựng qua API thật; chỉ bản chấm của AI
 * (student_answer_grading nguồn AI) và dữ liệu Video phản xạ đã-AI-chấm (reflex_question_progress) được chèn
 * bằng SQL — đúng hình dạng dữ liệu mà ReflexSequentialGradingService / ExerciseAttemptService ghi ra.
 */
const STUDENT_PASSWORD = "E2eTest@123456";

function sql(statement: string): string {
  return execFileSync("docker", ["exec", "pps-education-db", "psql", "-U", "pps_app", "-d", "pps_education", "-At", "-c", statement], {
    encoding: "utf8"
  }).trim();
}

async function json(res: import("@playwright/test").APIResponse, what: string) {
  expect(res.ok(), `${what} thất bại: ${res.status()} ${await res.text()}`).toBeTruthy();
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

interface Fixture {
  classId: number;
  classLabel: string;
  studentName: string;
  essayAnswerId: number;
  progressId: number;
}

let fx: Fixture;

async function setup(request: APIRequestContext): Promise<Fixture> {
  const token = await login(request, "sysadmin");
  const h = authHeaders(token);
  const ts = Date.now();
  // Giao Bài Writing chỉ áp dụng cho buổi do giáo viên Việt Nam dạy (teacherType=VIETNAMESE).
  const [classSessionId, classId] = sql(
    "select cs.id||','||cs.class_id from class_sessions cs join classes c on c.id=cs.class_id " +
      "where cs.teacher_type='VIETNAMESE' and cs.status<>'CANCELLED' and c.status='IN_PROGRESS' order by cs.id limit 1"
  ).split(",").map(Number);
  const teacherId = Number(sql("select id from users where username='teacher'"));
  if (sql(`select count(*) from class_teachers where class_id=${classId} and teacher_user_id=${teacherId} and assigned_to is null`) === "0") {
    sql(`insert into class_teachers (class_id, teacher_user_id, teacher_role, assigned_by, assigned_from) values (${classId}, ${teacherId}, 'PRIMARY', 1, now())`);
  }

  const studentId = await createAndEnrollStudent(request, token, classId);
  const student = await json(await request.get(`${BACKEND_URL}/api/students/${studentId}`, { headers: h }), "GET student");
  // createAndEnrollStudent đặt username/họ tên theo cùng mốc thời gian với studentCode (E2E-S-<ts>).
  const stamp = String(student.studentCode).split("-").pop();
  const studentUsername = `e2e_stu_${stamp}`;
  const studentName = `E2E Student ${stamp}`;

  // --- Bài tập Writing: Đề → câu ESSAY → Bài (WRITING) → giao BTVN ---
  const curricula = await json(await request.get(`${BACKEND_URL}/api/curriculums`, { headers: h }), "GET curriculums");
  const exam = await json(
    await request.post(`${BACKEND_URL}/api/exams`, {
      headers: h,
      data: { code: `E2E-AI2-${ts}`, title: `E2E AI2 ${ts}`, curriculumId: curricula[0].id, teacherType: "VIETNAMESE", examType: "HOMEWORK" }
    }),
    "POST exams"
  );
  await json(await request.post(`${BACKEND_URL}/api/exams/${exam.id}/classes/${classId}`, { headers: h }), "gán Đề cho lớp");
  const question = await json(
    await request.post(`${BACKEND_URL}/api/exams/${exam.id}/questions`, {
      headers: h,
      data: { questionType: "ESSAY", skill: "WRITING", content: "Write about your daily routine.", defaultPoints: 10 }
    }),
    "POST câu ESSAY"
  );
  const exercise = await json(
    await request.post(`${BACKEND_URL}/api/exercises`, {
      headers: h,
      data: { code: `E2E-AI2-EX-${ts}`, title: `E2E AI2 Writing ${ts}`, examId: exam.id, exerciseType: "ASSIGNED", totalPoints: 10, allowRetake: false, showCorrectAnswers: false, skillCategory: "WRITING" }
    }),
    "POST exercises"
  );
  await json(await request.post(`${BACKEND_URL}/api/exercises/${exercise.id}/questions`, { headers: h, data: { questionId: question.id, displayOrder: 1, points: 10 } }), "thêm câu vào Bài");
  await json(await request.post(`${BACKEND_URL}/api/exercises/${exercise.id}/publish`, { headers: h }), "publish Bài");
  await json(
    await request.post(`${BACKEND_URL}/api/class-sessions/${classSessionId}/comments/apply-homework`, {
      headers: h,
      data: { writingExamId: exam.id, writingExerciseIds: [exercise.id], lateSubmissionAllowed: true }
    }),
    "giao BTVN"
  );

  // --- Học sinh làm bài qua API thật ---
  const assignmentId = Number(sql(`select id from exercise_assignments where exercise_id=${exercise.id} and class_id=${classId} order by id desc limit 1`));
  const studentToken = await login(request, studentUsername, STUDENT_PASSWORD);
  const sh = authHeaders(studentToken);
  const attempt = await json(await request.post(`${BACKEND_URL}/api/exercises/${exercise.id}/attempts?assignmentId=${assignmentId}`, { headers: sh }), "bắt đầu làm bài");
  await json(
    await request.post(`${BACKEND_URL}/api/attempts/${attempt.id}/answers`, { headers: sh, data: { questionId: question.id, answerText: "I goes to school every day." } }),
    "lưu câu trả lời"
  );
  await json(await request.post(`${BACKEND_URL}/api/attempts/${attempt.id}/submit`, { headers: sh }), "nộp bài");
  const essayAnswerId = Number(sql(`select id from student_answers where exercise_attempt_id=${attempt.id} and question_id=${question.id}`));
  // Máy dev chưa có khoá AI → nộp bài xong chưa có điểm AI; chèn bản chấm AI đúng như ExerciseAttemptService#gradeEssayWithAi ghi.
  sql(`delete from student_answer_grading where student_answer_id=${essayAnswerId}`);
  sql(
    `insert into student_answer_grading (student_answer_id, grader_user_id, score, max_score, feedback, marked_answer, graded_at, is_final, grading_source) ` +
      `values (${essayAnswerId}, 1, 3.5, 10, 'AI: bài thiếu ý triển khai', 'I {{G1|goes}} to school every day.', now(), true, 'AI')`
  );

  // --- Video phản xạ đã được AI chấm cả Viết lẫn Nói ---
  const setId = Number(sql(`insert into review_video_sets (code, title, video_type, curriculum_id, created_by, teacher_type) values ('E2E-AI2-${ts}', 'Bộ E2E AI2', 'REFLEX', ${curricula[0].id}, 1, 'VIETNAMESE') returning id`).split("\n")[0]);
  const videoId = Number(sql(`insert into review_videos (review_video_set_id, title, source_type, file_url, duration_seconds) values (${setId}, 'Video E2E', 'R2_VIDEO', 'https://example.test/v.mp4', 60) returning id`).split("\n")[0]);
  const questionId = Number(sql(`insert into review_video_questions (review_video_id, timestamp_seconds, max_recording_seconds, prompt) values (${videoId}, 10, 30, 'Describe your weekend') returning id`).split("\n")[0]);
  const rvAssignmentId = Number(sql(`insert into review_video_assignments (review_video_set_id, class_id, assigned_by) values (${setId}, ${classId}, 1) returning id`).split("\n")[0]);
  const progressId = Number(
    sql(
      `insert into reflex_question_progress (review_video_question_id, student_id, review_video_assignment_id, answer_text, writing_score, writing_max_score, writing_feedback, writing_marked_answer, writing_graded_at, writing_attempt_count, ` +
        `audio_url, speaking_score, speaking_max_score, speaking_feedback, speaking_transcript, speaking_graded_at, speaking_attempt_count) ` +
        `values (${questionId}, ${studentId}, ${rvAssignmentId}, 'I play football', 40, 100, 'AI: ngữ pháp còn lỗi', 'I {{err}}plays{{/err}} football', now(), 1, ` +
        `'https://example.test/a.mp3', 55, 100, 'AI: nói chưa rõ ý', 'I {{err}}plays{{/err}} football on weekend', now(), 1) returning id`
    ).split("\n")[0]
  );

  const cls = await json(await request.get(`${BACKEND_URL}/api/classes/${classId}`, { headers: h }), "GET class");
  return { classId, classLabel: `${cls.classCode}`, studentName, essayAnswerId, progressId };
}

test.beforeAll(async ({ request }) => {
  const res = await request.post(`${BACKEND_URL}/api/auth/login`, { data: { usernameOrEmail: "teacher", password: DEV_PASSWORD, confirm: true } });
  expect(res.ok(), "Backend :8080 chưa chạy").toBeTruthy();
  fx = await setup(request);
});

test("Giáo viên chấm lần 2 bài Writing + Video phản xạ do AI chấm", async ({ page }) => {
  await page.goto("/login");
  await page.locator('input[type="text"]').first().fill("teacher");
  await page.locator('input[type="password"]').fill(DEV_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });

  await page.goto("/lms/exams");
  await page.getByRole("button", { name: "AI đã chấm — Chấm lần 2" }).click();
  const classCard = page.locator("button", { hasText: fx.classLabel }).first();
  await expect(classCard).toBeVisible();
  await classCard.click();

  // Cả 3 mục (Writing, Viết, Nói) của học sinh mới tạo đều đang chờ giáo viên xem lại
  const cards = page.locator("div.border.rounded-xl", { hasText: fx.studentName });
  await expect(cards).toHaveCount(3);
  await page.screenshot({ path: "screenshots/ai2-1-list.png", fullPage: true });

  // --- ESSAY ---
  const essay = cards.filter({ hasText: "Bài tập Writing" });
  await expect(essay).toContainText("AI: bài thiếu ý triển khai");
  await expect(essay).toContainText("3.5/10");
  await essay.locator('input[type="number"]').fill("8");
  await essay.locator('input[type="text"]').fill("Cô thấy bài đủ ý");
  await essay.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(cards).toHaveCount(2); // bài đã chấm lại rời khỏi danh sách "chờ xem lại"

  // --- Video phản xạ: bước Viết ---
  const writing = cards.filter({ hasText: "Bước Viết" });
  await expect(writing).toContainText("AI: ngữ pháp còn lỗi");
  await writing.locator('input[type="number"]').fill("85");
  await writing.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(cards).toHaveCount(1);

  // Bật "Hiện cả bài đã chấm lại" → thấy cả điểm AI gốc lẫn điểm giáo viên
  await page.getByLabel("Hiện cả bài đã chấm lại").check();
  await expect(cards).toHaveCount(3);
  const essayAfter = cards.filter({ hasText: "Bài tập Writing" });
  await expect(essayAfter).toContainText("GV đã chấm lại");
  await expect(essayAfter).toContainText("3.5/10");
  await expect(essayAfter).toContainText("8/10");
  await page.screenshot({ path: "screenshots/ai2-2-reviewed.png", fullPage: true });

  // --- Dữ liệu thật trong DB ---
  expect(sql(`select grading_source||'|'||score||'|'||coalesce(marked_answer,'') from student_answer_grading where student_answer_id=${fx.essayAnswerId} and is_final`))
    .toBe("HUMAN|8.00|I {{G1|goes}} to school every day."); // điểm GV hiện hành, vẫn giữ đánh dấu lỗi của AI
  expect(sql(`select count(*) from student_answer_grading where student_answer_id=${fx.essayAnswerId} and grading_source='AI' and not is_final`)).toBe("1"); // bản AI còn làm lịch sử
  expect(sql(`select writing_score||'|'||writing_ai_score||'|'||(writing_reviewed_at is not null)||'|'||(speaking_reviewed_at is null) from reflex_question_progress where id=${fx.progressId}`))
    .toBe("85.00|40.00|true|true");
});

test("Giáo viên không đứng lớp không chấm lần 2 được (403)", async ({ request }) => {
  const token = await login(request, "opsmanager");
  const res = await request.post(`${BACKEND_URL}/api/reflex-progress/${fx.progressId}/review/SPEAKING`, {
    headers: authHeaders(token),
    data: { score: 90 }
  });
  expect([401, 403]).toContain(res.status());
  expect(sql(`select speaking_score from reflex_question_progress where id=${fx.progressId}`)).toBe("55.00");
});
