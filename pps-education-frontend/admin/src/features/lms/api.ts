import { apiRequest, apiRequestBlob } from "@/lib/apiClient";
import type { ClassResponse } from "@/features/academic/api";

/**
 * Khớp MediaModule thật của backend — mỗi module quy định content-type/size limit riêng
 * (xem MediaStorageService.java). STUDENT/PARENT/EMPLOYEE (V48): chỉ nhận ảnh, dùng cho
 * ảnh đại diện — path riêng trên R2 ("profiles/students|parents|employees").
 */
export type MediaUploadModule = "LMS_QUESTION" | "CURRICULUM_DOCUMENT" | "REVIEW_VIDEO" | "STUDENT" | "PARENT" | "EMPLOYEE";

/** UC-60/UC-23a: upload file thật lên Cloudflare R2 qua API dùng chung, trả về URL public để lưu vào field fileUrl/audioUrl/imageUrl. */
export function uploadMedia(file: File, module: MediaUploadModule): Promise<{ url: string }> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("module", module);
  return apiRequest<{ url: string }>("/media/upload", { method: "POST", body: formData });
}

// ===================== Ngân hàng câu hỏi (UC-40 bước 1) =====================

export interface QuestionBankResponse {
  id: number;
  code: string;
  name: string;
  curriculumId: number | null;
  subjectId: number | null;
  level: string | null;
  isActive: boolean;
}

export interface CreateQuestionBankRequest {
  code: string;
  name: string;
  curriculumId?: number;
  subjectId?: number;
  level?: string;
}

export function listQuestionBanksByCurriculum(curriculumId: number): Promise<QuestionBankResponse[]> {
  return apiRequest<QuestionBankResponse[]>(`/curriculums/${curriculumId}/question-banks`);
}

export function createQuestionBank(request: CreateQuestionBankRequest): Promise<QuestionBankResponse> {
  return apiRequest<QuestionBankResponse>("/question-banks", { method: "POST", body: JSON.stringify(request) });
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03 — ẩn/kích hoạt lại CẢ 1 ngân hàng câu hỏi (khác archive từng câu hỏi riêng lẻ đã có sẵn). */
export function updateQuestionBankStatus(id: number, isActive: boolean): Promise<QuestionBankResponse> {
  return apiRequest<QuestionBankResponse>(`/question-banks/${id}/status`, { method: "PUT", body: JSON.stringify({ isActive }) });
}

// ===================== Câu hỏi =====================

/**
 * Khớp Question.QuestionType thật — 8 loại (choices chỉ bắt buộc với 3 loại trắc nghiệm/đúng-sai
 * đầu). WORD_BANK/SENTENCE_BUILDING (V78, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-04) dùng structuredContent thay vì choices/correctAnswerText.
 */
export type QuestionType = "MULTIPLE_CHOICE" | "MULTIPLE_ANSWER" | "TRUE_FALSE" | "ESSAY" | "SPEAKING" | "FILL_IN_BLANK" | "WORD_BANK" | "SENTENCE_BUILDING";

/**
 * V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — dữ liệu riêng theo
 * questionType: WORD_BANK dùng "blanks" (đáp án đúng theo đúng thứ tự chỗ trống trong content),
 * SENTENCE_BUILDING dùng "chunks" (khối từ/cụm theo ĐÚNG thứ tự câu hoàn chỉnh).
 */
export interface QuestionStructuredContent {
  blanks?: string[];
  chunks?: string[];
}

/** Khớp Question.Skill thật (Question.java) — KHÔNG phải free-text, backend chỉ nhận đúng 1 trong 6 giá trị này. */
export type QuestionSkill = "LISTENING" | "READING" | "WRITING" | "SPEAKING" | "GRAMMAR" | "OTHER";

/** Khớp Question.Difficulty thật. */
export type QuestionDifficulty = "EASY" | "MEDIUM" | "HARD";

export interface QuestionChoiceRequest {
  choiceLabel: string;
  content: string;
  isCorrect: boolean;
  displayOrder: number;
}

export interface QuestionChoiceResponse {
  id: number;
  choiceLabel: string;
  content: string;
  isCorrect: boolean;
  displayOrder: number;
}

export interface CreateQuestionRequest {
  questionBankId: number;
  questionType: QuestionType;
  skill?: QuestionSkill;
  difficulty?: QuestionDifficulty;
  content: string;
  audioUrl?: string;
  imageUrl?: string;
  referencePassage?: string;
  explanation?: string;
  /** V54 — chỉ dùng khi questionType=FILL_IN_BLANK, so khớp case-insensitive + trim khi tự chấm. */
  correctAnswerText?: string;
  defaultPoints?: number;
  tags?: string[];
  /** Bắt buộc khi questionType thuộc MULTIPLE_CHOICE/MULTIPLE_ANSWER/TRUE_FALSE — để trống với ESSAY/SPEAKING/FILL_IN_BLANK. */
  choices?: QuestionChoiceRequest[];
  /** V78 — bắt buộc khi questionType=WORD_BANK/SENTENCE_BUILDING. */
  structuredContent?: QuestionStructuredContent;
  /** V78 — dạng "Đọc hiểu — lưới": nhiều câu MULTIPLE_CHOICE cùng groupKey gộp hiển thị 1 referencePassage. */
  groupKey?: string;
}

export interface QuestionResponse {
  id: number;
  questionBankId: number;
  questionType: QuestionType;
  skill: QuestionSkill | null;
  difficulty: QuestionDifficulty | null;
  content: string;
  audioUrl: string | null;
  imageUrl: string | null;
  referencePassage: string | null;
  explanation: string | null;
  correctAnswerText: string | null;
  defaultPoints: number | null;
  tags: string[] | null;
  status: string;
  createdBy: number;
  choices: QuestionChoiceResponse[];
  structuredContent: QuestionStructuredContent | null;
  groupKey: string | null;
}

export function listQuestions(bankId: number): Promise<QuestionResponse[]> {
  return apiRequest<QuestionResponse[]>(`/question-banks/${bankId}/questions`);
}

export interface UpdateQuestionRequest {
  content: string;
  audioUrl?: string;
  imageUrl?: string;
  referencePassage?: string;
  explanation?: string;
  correctAnswerText?: string;
  structuredContent?: QuestionStructuredContent;
  defaultPoints?: number;
  tags?: string[];
  choices?: QuestionChoiceRequest[];
  status?: "ACTIVE" | "ARCHIVED";
}

/**
 * UC-40 (SDD "Bảo vệ khi sửa"): nếu câu hỏi đã có học viên nộp bài
 * (student_answers), backend từ chối sửa content/đáp án đúng
 * (QuestionLockedException — trả 409/400 tuỳ cấu hình) — phải soạn câu hỏi
 * MỚI, câu cũ tự chuyển ARCHIVED. Không sửa được questionType/skill/
 * difficulty/questionBankId (cố định từ lúc tạo).
 */
export function updateQuestion(id: number, request: UpdateQuestionRequest): Promise<QuestionResponse> {
  return apiRequest<QuestionResponse>(`/questions/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

/**
 * Trả về cả `choices[].isCorrect` — CHỈ dành cho GV/quản lý ngân hàng câu hỏi
 * (quyền lms.question-bank.view), dùng để GV tự xem lại đề kèm đáp án trước khi
 * giao. Học viên KHÔNG được gọi endpoint này (đáp án chỉ lộ ra cho học viên
 * qua luồng nộp bài riêng, có kiểm soát show_correct_answers).
 */
export function getQuestion(id: number): Promise<QuestionResponse> {
  return apiRequest<QuestionResponse>(`/questions/${id}`);
}

/**
 * UC-40 Main Flow bước 1: soạn câu hỏi mới vào ngân hàng. Backend hiện thiếu
 * `@Valid` lồng trên field `choices` (CreateQuestionRequest.java) nên thiếu
 * `choiceLabel` sẽ vỡ 500 ở DB thay vì 400 sạch — FE PHẢI tự validate
 * `choiceLabel` không rỗng trước khi gọi, xem ChoiceListEditor trong
 * QuestionEditorForm.tsx.
 */
export function createQuestion(request: CreateQuestionRequest): Promise<QuestionResponse> {
  return apiRequest<QuestionResponse>("/questions", { method: "POST", body: JSON.stringify(request) });
}

/** V75: request theo Đề, backend tự resolve bank nội bộ — không gửi questionBankId. */
export type CreateExamQuestionRequest = Omit<CreateQuestionRequest, "questionBankId">;

export function listExamQuestions(examId: number): Promise<QuestionResponse[]> {
  return apiRequest<QuestionResponse[]>(`/exams/${examId}/questions`);
}

export function getExamQuestion(examId: number, questionId: number): Promise<QuestionResponse> {
  return apiRequest<QuestionResponse>(`/exams/${examId}/questions/${questionId}`);
}

export function createExamQuestion(examId: number, request: CreateExamQuestionRequest): Promise<QuestionResponse> {
  return apiRequest<QuestionResponse>(`/exams/${examId}/questions`, { method: "POST", body: JSON.stringify(request) });
}

export function updateExamQuestion(examId: number, questionId: number, request: UpdateQuestionRequest): Promise<QuestionResponse> {
  return apiRequest<QuestionResponse>(`/exams/${examId}/questions/${questionId}`, { method: "PUT", body: JSON.stringify(request) });
}

// ===================== Soạn đề nhanh: import Excel/Word (UC-40, bổ sung ngoài SDD gốc) =====================

export interface QuestionImportedRow {
  id: number;
  content: string;
  defaultPoints: number;
}

export interface QuestionImportResponse {
  id: number;
  sourceFileName: string;
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: { row: number; reason: string }[];
  createdQuestions: QuestionImportedRow[];
}

/**
 * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * soạn đề nhanh — nhập hàng loạt câu hỏi vào 1 ngân hàng qua file .xlsx
 * hoặc .docx theo mẫu cứng (xem QuestionImportService — đúng 5 loại UI mà
 * QuestionEditorForm hỗ trợ, KHÔNG dùng AI/OCR nhận diện tự do).
 */
export function importQuestions(bankId: number, file: File): Promise<QuestionImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<QuestionImportResponse>(`/question-banks/${bankId}/questions/import`, { method: "POST", body: formData });
}

export function importExamQuestions(examId: number, file: File): Promise<QuestionImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<QuestionImportResponse>(`/exams/${examId}/questions/import`, { method: "POST", body: formData });
}

/** File mẫu Word generic legacy. */
export function downloadQuestionImportWordTemplate(): Promise<Blob> {
  return apiRequestBlob("/question-imports/template.docx");
}

/** V75: File mẫu Word cho luồng Giáo viên theo Đề. */
export function downloadExamQuestionImportWordTemplate(): Promise<Blob> {
  return apiRequestBlob("/exams/question-imports/template.docx");
}

// ===================== Kho đề (Exam) — "Đề" cha, VD: IELTS Grade 6 =====================
// Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30: tái cấu trúc UC-40 thành 2 cấp
// Đề (Exam)/Bài (Exercise). Đề gán 1 khung chương trình CHỈ để lọc/tìm kiếm trong Kho đề, gán được
// NHIỀU lớp (exam_class_assignments) — đây mới là điều kiện hiển thị DUY NHẤT cho học sinh xem/làm
// được các Bài thuộc Đề. Xem ExamService.java.

/** V74, đã xác nhận với người dùng 2026-08-04: Đề dành cho GV Việt Nam hay GV nước ngoài — dùng lọc khi giao bài. */
export type ExamTeacherType = "VIETNAMESE" | "FOREIGN";

/** V74, đã xác nhận với người dùng 2026-08-04: "Loại đề" — độc lập với ExerciseType, không thay thế. */
export type ExamType = "REVIEW" | "HOMEWORK";

export interface CreateExamRequest {
  code: string;
  title: string;
  curriculumId: number;
  teacherType: ExamTeacherType;
  examType: ExamType;
}

export interface UpdateExamRequest {
  title: string;
  teacherType: ExamTeacherType;
  examType: ExamType;
}

export interface ExamResponse {
  id: number;
  uuid: string;
  code: string;
  title: string;
  curriculumId: number;
  curriculumCode: string;
  createdBy: number;
  teacherType: ExamTeacherType;
  examType: ExamType;
}

export function createExam(request: CreateExamRequest): Promise<ExamResponse> {
  return apiRequest<ExamResponse>("/exams", { method: "POST", body: JSON.stringify(request) });
}

export function updateExam(id: number, request: UpdateExamRequest): Promise<ExamResponse> {
  return apiRequest<ExamResponse>(`/exams/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function getExam(id: number): Promise<ExamResponse> {
  return apiRequest<ExamResponse>(`/exams/${id}`);
}

/** Bỏ trống curriculumId/teacherType để không lọc theo tiêu chí đó. */
export function listExams(curriculumId?: number, teacherType?: ExamTeacherType): Promise<ExamResponse[]> {
  const params = new URLSearchParams();
  if (curriculumId) params.set("curriculumId", String(curriculumId));
  if (teacherType) params.set("teacherType", teacherType);
  const query = params.toString() ? `?${params.toString()}` : "";
  return apiRequest<ExamResponse[]>(`/exams${query}`);
}

export function listExercisesByExam(examId: number): Promise<ExerciseResponse[]> {
  return apiRequest<ExerciseResponse[]>(`/exams/${examId}/exercises`);
}

export function assignExamToClass(examId: number, classId: number): Promise<void> {
  return apiRequest<void>(`/exams/${examId}/classes/${classId}`, { method: "POST" });
}

export function unassignExamFromClass(examId: number, classId: number): Promise<void> {
  return apiRequest<void>(`/exams/${examId}/classes/${classId}`, { method: "DELETE" });
}

export function listExamAssignedClasses(examId: number): Promise<ClassResponse[]> {
  return apiRequest<ClassResponse[]>(`/exams/${examId}/classes`);
}

/** V80 — "Xóa Đề" (soft-delete), chỉ xóa được khi mọi Bài thuộc Đề đã lưu trữ (xem deleteExercise). */
export function deleteExam(id: number): Promise<void> {
  return apiRequest<void>(`/exams/${id}`, { method: "DELETE" });
}

// ===================== Bài (Exercise) — UC-40 bước 2-4, thuộc 1 Đề (Exam) =====================

/**
 * FE chỉ dùng các giá trị đúng phạm vi UC-40 (SDD có thêm MOCK_TEST/SKILL_PRACTICE thuộc UC khác,
 * không đưa vào đây). REFLEX_VIDEO (V77, đã rollback ở V79 — bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-08-04): Video phản xạ không còn bọc trong Exercise nữa — giao lớp trực tiếp ở Kho
 * Video Ôn tập, y hệt Video kết nối.
 */
export type ExerciseType = "SELF_PRACTICE" | "ASSIGNED";

export interface CreateExerciseRequest {
  code: string;
  title: string;
  examId: number;
  subjectId?: number;
  exerciseType: ExerciseType;
  totalPoints: number;
  timeLimitMinutes?: number;
  allowRetake: boolean;
  maxAttempts?: number;
  showCorrectAnswers: boolean;
}

export interface ExerciseResponse {
  id: number;
  /** V65 — dùng để dán trực tiếp vào Excel BTVN thay cho chọn dropdown khi danh sách quá dài. */
  uuid: string;
  code: string;
  title: string;
  examId: number;
  /** Denormalize từ Đề cha — render nhãn "Mã Đề - Tên bài" không cần round-trip thêm. */
  examCode: string;
  examTitle: string;
  subjectId: number | null;
  exerciseType: ExerciseType;
  totalPoints: number;
  timeLimitMinutes: number | null;
  allowRetake: boolean;
  maxAttempts: number | null;
  showCorrectAnswers: boolean;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  createdBy: number;
  /** true nếu đề có ít nhất 1 câu ESSAY/SPEAKING — cần chấm tay ở UC-41 sau khi học sinh nộp. */
  hasEssayOrSpeaking: boolean;
}

export function createExercise(request: CreateExerciseRequest): Promise<ExerciseResponse> {
  return apiRequest<ExerciseResponse>("/exercises", { method: "POST", body: JSON.stringify(request) });
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — sửa lại thông tin 1 Bài đã soạn (không sửa được code/examId/exerciseType). */
export interface UpdateExerciseRequest {
  title: string;
  subjectId?: number;
  totalPoints: number;
  allowRetake: boolean;
  maxAttempts?: number;
  showCorrectAnswers: boolean;
}

export function updateExercise(id: number, request: UpdateExerciseRequest): Promise<ExerciseResponse> {
  return apiRequest<ExerciseResponse>(`/exercises/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

/** V80 — "Xóa Bài" = lưu trữ (status=ARCHIVED), ẩn khỏi Kho đề nhưng không xóa cứng. */
export function deleteExercise(id: number): Promise<void> {
  return apiRequest<void>(`/exercises/${id}`, { method: "DELETE" });
}

export function getExercise(id: number): Promise<ExerciseResponse> {
  return apiRequest<ExerciseResponse>(`/exercises/${id}`);
}

/** V65 — Publish giờ chỉ đánh dấu đề "đủ điều kiện dùng làm nguồn", không còn giao lớp (xem Javadoc ExerciseService). */
export function publishExercise(id: number): Promise<ExerciseResponse> {
  return apiRequest<ExerciseResponse>(`/exercises/${id}/publish`, { method: "POST" });
}

export interface AddExerciseQuestionRequest {
  questionId: number;
  displayOrder: number;
  points: number;
}

export interface ExerciseQuestionResponse {
  id: number;
  exerciseId: number;
  questionId: number;
  questionType: QuestionType;
  questionContent: string;
  displayOrder: number;
  points: number;
  skill: QuestionSkill | null;
  audioUrl: string | null;
  referencePassage: string | null;
  structuredContent: QuestionStructuredContent | null;
  groupKey: string | null;
}

export function addExerciseQuestion(exerciseId: number, request: AddExerciseQuestionRequest): Promise<ExerciseQuestionResponse> {
  return apiRequest<ExerciseQuestionResponse>(`/exercises/${exerciseId}/questions`, { method: "POST", body: JSON.stringify(request) });
}

export function listExerciseQuestions(exerciseId: number): Promise<ExerciseQuestionResponse[]> {
  return apiRequest<ExerciseQuestionResponse[]>(`/exercises/${exerciseId}/questions`);
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — chỉ gỡ được khi Bài còn DRAFT (backend tự chặn 400 nếu đã Publish). */
export function removeExerciseQuestion(exerciseId: number, exerciseQuestionId: number): Promise<void> {
  return apiRequest<void>(`/exercises/${exerciseId}/questions/${exerciseQuestionId}`, { method: "DELETE" });
}

/**
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30): giao đề không còn thao tác
 * riêng ở "Soạn & Giao đề" nữa — id (bản giao) giờ tự động phát sinh khi Giáo viên chọn 1 Exercise
 * làm "BTVN Ngữ pháp buổi sau" ở Nhận xét học viên (xem academic/api.ts CreateStudentCommentRequest.
 * homeworkNextExerciseId). Interface/hàm dưới đây CHỈ còn dùng để GV xem lại bản giao đã phát sinh
 * (lịch sử) hoặc để DailyCommentPanel tra ngược "bản giao này ứng với Exercise nào" khi tải lại 1
 * comment đã chọn sẵn — không còn cách nào tạo bản giao thủ công từ FE nữa (endpoint POST .../assign
 * đã bị xóa bên BE).
 */
export interface ExerciseAssignmentResponse {
  id: number;
  uuid: string;
  exerciseId: number;
  exerciseTitle: string;
  exerciseCode: string;
  classId: number;
  assignedBy: number;
  availableFrom: string;
  dueAt: string | null;
  lateSubmissionAllowed: boolean;
  latePenaltyPercent: number | null;
  targetStudentIds: number[] | null;
  status: "ACTIVE" | "CANCELLED" | "COMPLETED";
}

/** Giáo viên xem lại các bản giao (tự động phát sinh từ Nhận xét học viên, V65) của 1 lớp — API lọc sẵn status=ACTIVE. */
export function listAssignmentsForClass(classId: number): Promise<ExerciseAssignmentResponse[]> {
  return apiRequest<ExerciseAssignmentResponse[]>(`/classes/${classId}/exercises`);
}

/** Kho đề: nguồn cho dropdown "BTVN buổi sau" ở Nhận xét học viên — mọi loại Bài đã Publish, thuộc 1 Đề đã gán cho lớp (không còn theo khung chương trình). */
export function listPublishedExercisesForClass(classId: number): Promise<ExerciseResponse[]> {
  return apiRequest<ExerciseResponse[]>(`/classes/${classId}/exercises/published`);
}

// ===================== Kho Video Ôn tập (UC-23/UC-23a) =====================

/** Khớp ReviewVideoSet.VideoType thật — CONNECTION: ôn từ vựng buổi học; REFLEX: hỏi-đáp luyện nói. */
export type ReviewVideoType = "CONNECTION" | "REFLEX";
export type ReviewVideoSetStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

/** Khớp ReviewVideoSetResponse thật — đúng 1 trong 2 curriculumId/classId khác null (không cả hai, không cái nào). */
export interface ReviewVideoSetResponse {
  id: number;
  /** V55 — dùng để dán trực tiếp vào Excel BTVN thay cho chọn dropdown khi danh sách quá dài. */
  uuid: string;
  code: string;
  title: string;
  videoType: ReviewVideoType;
  curriculumId: number | null;
  classId: number | null;
  subjectId: number | null;
  displayOrder: number;
  status: ReviewVideoSetStatus;
  publishedAt: string | null;
  createdBy: number;
}

export interface CreateReviewVideoSetRequest {
  code: string;
  title: string;
  videoType: ReviewVideoType;
  curriculumId?: number;
  classId?: number;
  subjectId?: number;
  displayOrder?: number;
}

/** Khớp UpdateReviewVideoSetRequest thật — không đổi được code/scope (curriculumId/classId) sau khi tạo, chỉ đổi status để công bố (PUBLISHED, publishedAt chỉ set 1 lần) hoặc gỡ (ARCHIVED, soft-remove). */
export interface UpdateReviewVideoSetRequest {
  title: string;
  subjectId?: number;
  displayOrder?: number;
  status: ReviewVideoSetStatus;
}

/** UC-23 Main Flow bước 1-4: chỉ Giáo viên được phân công dạy đúng lớp/khung mới tạo/sửa được — BE tự chặn theo class_teachers, không phải theo permission. */
export function createReviewVideoSet(request: CreateReviewVideoSetRequest): Promise<ReviewVideoSetResponse> {
  return apiRequest<ReviewVideoSetResponse>("/review-video-sets", { method: "POST", body: JSON.stringify(request) });
}

export function updateReviewVideoSet(id: number, request: UpdateReviewVideoSetRequest): Promise<ReviewVideoSetResponse> {
  return apiRequest<ReviewVideoSetResponse>(`/review-video-sets/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function listReviewVideoSetsByClass(classId: number): Promise<ReviewVideoSetResponse[]> {
  return apiRequest<ReviewVideoSetResponse[]>(`/classes/${classId}/review-video-sets`);
}

export function listReviewVideoSetsByCurriculum(curriculumId: number): Promise<ReviewVideoSetResponse[]> {
  return apiRequest<ReviewVideoSetResponse[]>(`/curriculums/${curriculumId}/review-video-sets`);
}

/**
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30): giao Video Ôn tập không còn
 * xảy ra khi Publish nữa — bản giao (ReviewVideoAssignment) tự động phát sinh khi Giáo viên chọn 1
 * bộ làm "BTVN buổi sau" ở Nhận xét học viên. Dùng để DailyCommentPanel tra ngược "bản giao này ứng
 * với bộ video nào" khi tải lại 1 comment đã chọn sẵn (StudentCommentResponse chỉ trả id bản giao).
 */
export interface ReviewVideoAssignmentResponse {
  id: number;
  uuid: string;
  reviewVideoSetId: number;
  reviewVideoSetTitle: string;
  classId: number;
  assignedBy: number;
  availableFrom: string;
  dueAt: string | null;
  targetStudentIds: number[] | null;
  status: "ACTIVE" | "CANCELLED" | "COMPLETED";
}

/** Giáo viên xem lại các bản giao Video Ôn tập ACTIVE của 1 lớp (V65). */
export function listReviewVideoAssignmentsForClass(classId: number): Promise<ReviewVideoAssignmentResponse[]> {
  return apiRequest<ReviewVideoAssignmentResponse[]>(`/classes/${classId}/review-video-assignments`);
}

/** Khớp ReviewVideo.SourceType thật. */
export type ReviewVideoSourceType = "YOUTUBE_URL" | "R2_VIDEO" | "R2_AUDIO";

/** Khớp AddReviewVideoRequest thật — durationSeconds BẮT BUỘC cho cả 3 nguồn, FE phải tự dò trước khi gọi (BE không tự dò). fileUrl lấy từ uploadMedia (module REVIEW_VIDEO) hoặc dán trực tiếp link YouTube. */
export interface AddReviewVideoRequest {
  sourceType: ReviewVideoSourceType;
  title: string;
  fileUrl: string;
  fileSizeBytes?: number;
  durationSeconds: number;
  displayOrder?: number;
  /** V59 — chỉ có ý nghĩa với videoType=CONNECTION, để trống dùng mặc định 80. */
  completionThresholdPercent?: number;
  /** V59 — chỉ có ý nghĩa với videoType=CONNECTION, để trống dùng mặc định 1. */
  requiredViewCount?: number;
}

export interface ReviewVideoResponse {
  id: number;
  reviewVideoSetId: number;
  sourceType: ReviewVideoSourceType;
  title: string;
  fileUrl: string;
  fileSizeBytes: number | null;
  durationSeconds: number;
  displayOrder: number;
  completionThresholdPercent: number;
  requiredViewCount: number;
}

export function addReviewVideo(setId: number, request: AddReviewVideoRequest): Promise<ReviewVideoResponse> {
  return apiRequest<ReviewVideoResponse>(`/review-video-sets/${setId}/videos`, { method: "POST", body: JSON.stringify(request) });
}

export function listReviewVideos(setId: number): Promise<ReviewVideoResponse[]> {
  return apiRequest<ReviewVideoResponse[]>(`/review-video-sets/${setId}/videos`);
}

/** Khớp VideoHeader/StatsCell thật — ma trận học sinh × video (roster LEFT JOIN tiến độ, học sinh chưa xem gì vẫn hiện 0%). */
export interface ReviewVideoStatsHeader {
  videoId: number;
  title: string;
  durationSeconds: number;
  /** V59 — chỉ có ý nghĩa với video CONNECTION. */
  requiredViewCount: number;
}

export interface ReviewVideoStatsCell {
  studentId: number;
  videoId: number;
  watchedSeconds: number;
  watchedPercent: number;
  completed: boolean;
  /** V59 — số lượt xem đã đạt ngưỡng %, chỉ có ý nghĩa với video CONNECTION. */
  viewCount: number;
}

export interface ReviewVideoSetStatsResponse {
  videos: ReviewVideoStatsHeader[];
  cells: ReviewVideoStatsCell[];
}

/** UC-23a Main Flow bước 4: chỉ Giáo viên được phân công (requireOwnerScope) mới xem được — classId bắt buộc nếu bộ gán theo khung chương trình. */
export function getReviewVideoSetStats(setId: number, classId?: number): Promise<ReviewVideoSetStatsResponse> {
  const query = classId ? `?classId=${classId}` : "";
  return apiRequest<ReviewVideoSetStatsResponse>(`/review-video-sets/${setId}/stats${query}`);
}

// ===================== Kho Video Ôn tập — Câu hỏi Video phản xạ (UC-23b, V57) =====================

/** Câu hỏi gắn 1 mốc thời gian trong video REFLEX — thời lượng ghi âm/số lần nộp lại đặt riêng theo TỪNG câu hỏi. */
export interface ReviewVideoQuestionResponse {
  id: number;
  reviewVideoId: number;
  timestampSeconds: number;
  prompt: string | null;
  maxRecordingSeconds: number;
  /** null = không giới hạn số lần nộp lại. */
  maxAttempts: number | null;
  displayOrder: number;
}

export interface AddReviewVideoQuestionRequest {
  timestampSeconds: number;
  prompt?: string;
  maxRecordingSeconds: number;
  maxAttempts?: number;
  displayOrder?: number;
}

export function addReviewVideoQuestion(videoId: number, request: AddReviewVideoQuestionRequest): Promise<ReviewVideoQuestionResponse> {
  return apiRequest<ReviewVideoQuestionResponse>(`/review-videos/${videoId}/questions`, { method: "POST", body: JSON.stringify(request) });
}

export function listReviewVideoQuestions(videoId: number): Promise<ReviewVideoQuestionResponse[]> {
  return apiRequest<ReviewVideoQuestionResponse[]>(`/review-videos/${videoId}/questions`);
}

// ===================== Kho Video Ôn tập — Câu hỏi trắc nghiệm Video Kết nối (V76) =====================

export interface ConnectionChoiceRequest {
  choiceLabel: string;
  content: string;
  isCorrect: boolean;
  displayOrder: number;
}

/** isCorrect = null khi trả về cho HỌC SINH (chưa nộp bài) — chỉ Giáo viên soạn bài mới thấy giá trị thật. */
export interface ReviewVideoConnectionChoiceResponse {
  id: number;
  choiceLabel: string;
  content: string;
  isCorrect: boolean | null;
  displayOrder: number;
}

export interface ReviewVideoConnectionQuestionResponse {
  id: number;
  reviewVideoId: number;
  prompt: string;
  displayOrder: number;
  choices: ReviewVideoConnectionChoiceResponse[];
}

export interface AddReviewVideoConnectionQuestionRequest {
  prompt: string;
  displayOrder?: number;
  choices: ConnectionChoiceRequest[];
}

export function addReviewVideoConnectionQuestion(
  videoId: number,
  request: AddReviewVideoConnectionQuestionRequest
): Promise<ReviewVideoConnectionQuestionResponse> {
  return apiRequest<ReviewVideoConnectionQuestionResponse>(`/review-videos/${videoId}/connection-questions`, {
    method: "POST",
    body: JSON.stringify(request)
  });
}

export function listReviewVideoConnectionQuestions(videoId: number): Promise<ReviewVideoConnectionQuestionResponse[]> {
  return apiRequest<ReviewVideoConnectionQuestionResponse[]>(`/review-videos/${videoId}/connection-questions`);
}

// ===================== Kho tài liệu tham khảo (UC-60) =====================

export type CurriculumDocumentType = "VIDEO" | "PDF" | "AUDIO" | "SLIDE" | "IMAGE" | "OTHER";
export type CurriculumDocumentStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

/** Khớp CurriculumDocumentResponse thật — độc lập với Kho Video Ôn tập (UC-23), chỉ gắn theo curriculum, không gắn 1 bộ video cụ thể nào. */
export interface CurriculumDocumentResponse {
  id: number;
  curriculumId: number;
  title: string;
  description: string | null;
  documentType: CurriculumDocumentType;
  fileUrl: string;
  displayOrder: number;
  status: CurriculumDocumentStatus;
  createdBy: number;
  /** V48: ảnh bìa hiển thị ở card, upload qua POST /api/media/upload (module CURRICULUM_DOCUMENT). */
  coverImageUrl: string | null;
}

export interface CreateCurriculumDocumentRequest {
  title: string;
  description?: string;
  documentType: CurriculumDocumentType;
  fileUrl: string;
  displayOrder?: number;
  coverImageUrl?: string;
}

export interface UpdateCurriculumDocumentRequest {
  title: string;
  description?: string;
  displayOrder?: number;
  status: CurriculumDocumentStatus;
  coverImageUrl?: string;
}

/** UC-60: chỉ tài khoản có quyền lms.document.create/update (mặc định gán cho TEACHER) mới tạo/sửa được. */
export function createCurriculumDocument(curriculumId: number, request: CreateCurriculumDocumentRequest): Promise<CurriculumDocumentResponse> {
  return apiRequest<CurriculumDocumentResponse>(`/curriculums/${curriculumId}/documents`, { method: "POST", body: JSON.stringify(request) });
}

export function updateCurriculumDocument(id: number, request: UpdateCurriculumDocumentRequest): Promise<CurriculumDocumentResponse> {
  return apiRequest<CurriculumDocumentResponse>(`/documents/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

/** Xem mọi trạng thái (staff quản lý) — khác hẳn GET /api/students/me/documents (Học sinh, chỉ PUBLISHED, self-service). */
export function listCurriculumDocuments(curriculumId: number): Promise<CurriculumDocumentResponse[]> {
  return apiRequest<CurriculumDocumentResponse[]>(`/curriculums/${curriculumId}/documents`);
}
