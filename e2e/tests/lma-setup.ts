import { APIRequestContext, expect } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";
import { BACKEND_URL, authHeaders, createAndEnrollStudent, findUsableClass, login } from "./api";

/**
 * Dựng dữ liệu cho test "Nghe trắc nghiệm nhiều đáp án" hoàn toàn qua API thật (không SQL):
 * Đề (Exam) → IMPORT Excel NGHE_NHIEU_DAP_AN → Bài (Exercise) → Publish → giao BTVN cho cả lớp
 * (POST .../comments/apply-homework, cùng đường đi GV dùng ở "Nhận xét học viên") → 1 học sinh mới
 * đã ghi danh lớp đó để đăng nhập Portal làm bài.
 */
export const LMA_STUDENT_PASSWORD = "E2eTest@123456";

async function json(res: import("@playwright/test").APIResponse, what: string) {
  expect(res.ok(), `${what} thất bại: ${res.status()} ${await res.text()}`).toBeTruthy();
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export interface LmaFixture {
  studentUsername: string;
  exerciseTitle: string;
  examId: number;
  exerciseId: number;
  classId: number;
  importResult: { totalRows: number; successRows: number; failedRows: number; status: string; errorSummary?: Array<{ reason?: string }> };
  questions: Array<{ id: number; content: string; questionType: string; skill: string; choices: Array<{ choiceLabel: string; isCorrect: boolean }> }>;
}

export async function setupListeningMultiAnswerHomework(request: APIRequestContext): Promise<LmaFixture> {
  const token = await login(request, "sysadmin");
  const h = authHeaders(token);
  const ts = Date.now();

  const { classId, classSessionId } = await findUsableClass(request, token);
  const sessions = await (await request.get(`${BACKEND_URL}/api/classes/${classId}/sessions`, { headers: h })).json();
  const session = (sessions as Array<{ id: number; teacherType?: string }>).find((s) => s.id === classSessionId);
  const teacherType = session?.teacherType === "VIETNAMESE" ? "VIETNAMESE" : "FOREIGN";
  const skillCategory = teacherType === "FOREIGN" ? "LISTENING" : "VOCAB_GRAMMAR";

  const curricula = await json(await request.get(`${BACKEND_URL}/api/curriculums`, { headers: h }), "GET curriculums");
  const exam = await json(
    await request.post(`${BACKEND_URL}/api/exams`, {
      headers: h,
      data: { code: `E2E-LMA-${ts}`, title: `E2E Nghe nhiều đáp án ${ts}`, curriculumId: curricula[0].id, teacherType, examType: "HOMEWORK" }
    }),
    "POST exams"
  );
  await json(await request.post(`${BACKEND_URL}/api/exams/${exam.id}/classes/${classId}`, { headers: h }), "gán Đề cho lớp");

  const buffer = fs.readFileSync(path.join(__dirname, "fixtures", "nghe-nhieu-dap-an.xlsx"));
  const importResult = await json(
    await request.post(`${BACKEND_URL}/api/exams/${exam.id}/questions/import`, {
      headers: h,
      multipart: { file: { name: "nghe-nhieu-dap-an.xlsx", mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buffer } }
    }),
    "import Excel"
  );

  const questions = (await json(await request.get(`${BACKEND_URL}/api/exams/${exam.id}/questions`, { headers: h }), "GET questions")) as LmaFixture["questions"];

  const exercise = await json(
    await request.post(`${BACKEND_URL}/api/exercises`, {
      headers: h,
      data: {
        code: `E2E-LMA-EX-${ts}`, title: `E2E Nghe nhiều đáp án ${ts}`, examId: exam.id, exerciseType: "ASSIGNED",
        totalPoints: 4, allowRetake: false, showCorrectAnswers: true, skillCategory
      }
    }),
    "POST exercises"
  );
  let order = 1;
  for (const q of questions) {
    await json(
      await request.post(`${BACKEND_URL}/api/exercises/${exercise.id}/questions`, { headers: h, data: { questionId: q.id, displayOrder: order++, points: 2 } }),
      "thêm câu vào Bài"
    );
  }
  await json(await request.post(`${BACKEND_URL}/api/exercises/${exercise.id}/publish`, { headers: h }), "publish Bài");

  // Học sinh mới phải ghi danh TRƯỚC khi giao bài (apply-homework giao cho mọi học sinh ACTIVE lúc đó).
  const studentId = await createAndEnrollStudent(request, token, classId);
  const students = await json(await request.get(`${BACKEND_URL}/api/students/${studentId}`, { headers: h }), "GET student");
  const studentUsername = (students.username ?? students.user?.username ?? `e2e_stu_${students.studentCode?.split("-").pop()}`) as string;

  await json(
    await request.post(`${BACKEND_URL}/api/class-sessions/${classSessionId}/comments/apply-homework`, {
      headers: h,
      data: { grammarExamId: exam.id, grammarExerciseIds: [exercise.id], lateSubmissionAllowed: true }
    }),
    "giao BTVN (apply-homework)"
  );

  return { studentUsername, exerciseTitle: exercise.title as string, examId: exam.id, exerciseId: exercise.id, classId, importResult, questions };
}
