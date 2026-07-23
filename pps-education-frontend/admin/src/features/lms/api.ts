import { apiRequest } from "@/lib/apiClient";

/**
 * Khớp MediaModule thật của backend — mỗi module quy định content-type/size limit riêng
 * (xem MediaStorageService.java). STUDENT/PARENT/EMPLOYEE (V48): chỉ nhận ảnh, dùng cho
 * ảnh đại diện — path riêng trên R2 ("profiles/students|parents|employees").
 */
export type MediaUploadModule = "LMS_QUESTION" | "CURRICULUM_DOCUMENT" | "LESSON_MATERIAL" | "STUDENT" | "PARENT" | "EMPLOYEE";

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

// ===================== Câu hỏi =====================

/** Khớp Question.QuestionType thật — 6 loại, không phải 3 (choices chỉ bắt buộc với 3 loại trắc nghiệm/đúng-sai đầu). */
export type QuestionType = "MULTIPLE_CHOICE" | "MULTIPLE_ANSWER" | "TRUE_FALSE" | "ESSAY" | "SPEAKING" | "FILL_IN_BLANK";

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
  defaultPoints?: number;
  tags?: string[];
  /** Bắt buộc khi questionType thuộc MULTIPLE_CHOICE/MULTIPLE_ANSWER/TRUE_FALSE — để trống với ESSAY/SPEAKING/FILL_IN_BLANK. */
  choices?: QuestionChoiceRequest[];
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
  defaultPoints: number | null;
  tags: string[] | null;
  status: string;
  createdBy: number;
  choices: QuestionChoiceResponse[];
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
 * (quyền lms.exercise.manage), dùng để GV tự xem lại đề kèm đáp án trước khi
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

// ===================== Đề (Exercise) — UC-40 bước 2-4 =====================

/** FE chỉ dùng 2 giá trị đúng phạm vi UC-40 (SDD có thêm MOCK_TEST/SKILL_PRACTICE thuộc UC khác, không đưa vào đây). */
export type ExerciseType = "SELF_PRACTICE" | "ASSIGNED";

export interface CreateExerciseRequest {
  code: string;
  title: string;
  curriculumId?: number;
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
  code: string;
  title: string;
  curriculumId: number | null;
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

export function getExercise(id: number): Promise<ExerciseResponse> {
  return apiRequest<ExerciseResponse>(`/exercises/${id}`);
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
}

export function addExerciseQuestion(exerciseId: number, request: AddExerciseQuestionRequest): Promise<ExerciseQuestionResponse> {
  return apiRequest<ExerciseQuestionResponse>(`/exercises/${exerciseId}/questions`, { method: "POST", body: JSON.stringify(request) });
}

export function listExerciseQuestions(exerciseId: number): Promise<ExerciseQuestionResponse[]> {
  return apiRequest<ExerciseQuestionResponse[]>(`/exercises/${exerciseId}/questions`);
}

export interface AssignExerciseRequest {
  classId: number;
  availableFrom?: string;
  dueAt?: string;
  lateSubmissionAllowed: boolean;
  latePenaltyPercent?: number;
  /** Để trống (undefined) = giao cả lớp (mọi học sinh ACTIVE) — chỉ gửi khi giao riêng 1 số học sinh. */
  targetStudentIds?: number[];
}

export interface ExerciseAssignmentResponse {
  id: number;
  exerciseId: number;
  classId: number;
  assignedBy: number;
  availableFrom: string;
  dueAt: string | null;
  lateSubmissionAllowed: boolean;
  latePenaltyPercent: number | null;
  targetStudentIds: number[] | null;
  status: "ACTIVE" | "CANCELLED" | "COMPLETED";
}

/** UC-40 Main Flow bước 3-4: giao đề cho 1 lớp + hạn nộp — tự chuyển đề sang PUBLISHED, báo học sinh ACTIVE trong lớp (hoặc targetStudentIds). */
export function assignExercise(exerciseId: number, request: AssignExerciseRequest): Promise<ExerciseAssignmentResponse> {
  return apiRequest<ExerciseAssignmentResponse>(`/exercises/${exerciseId}/assign`, { method: "POST", body: JSON.stringify(request) });
}

/** Giáo viên xem lại đã giao đề gì cho 1 lớp — API lọc sẵn status=ACTIVE. */
export function listAssignmentsForClass(classId: number): Promise<ExerciseAssignmentResponse[]> {
  return apiRequest<ExerciseAssignmentResponse[]>(`/classes/${classId}/exercises`);
}

// ===================== Kho bài giảng (UC-23) =====================

export type LessonType = "VIDEO_LECTURE" | "PDF_DOCUMENT" | "MIXED" | "LIVE_RECORDING";
export type LessonStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

/** Khớp LessonResponse thật — đúng 1 trong 2 curriculumId/classId khác null (không cả hai, không cái nào). */
export interface LessonResponse {
  id: number;
  code: string;
  title: string;
  curriculumId: number | null;
  classId: number | null;
  subjectId: number | null;
  lessonOrder: number | null;
  lessonType: LessonType;
  durationMinutes: number | null;
  status: LessonStatus;
  publishedAt: string | null;
  createdBy: number;
}

export interface CreateLessonRequest {
  code: string;
  title: string;
  curriculumId?: number;
  classId?: number;
  subjectId?: number;
  lessonOrder?: number;
  lessonType: LessonType;
  durationMinutes?: number;
}

export interface UpdateLessonRequest {
  title: string;
  subjectId?: number;
  lessonOrder?: number;
  durationMinutes?: number;
  status: LessonStatus;
}

/** UC-23 Main Flow bước 3-4: chỉ Giáo viên được phân công dạy đúng lớp/khung mới tạo được — BE tự chặn (403), không bypass qua permission. */
export function createLesson(request: CreateLessonRequest): Promise<LessonResponse> {
  return apiRequest<LessonResponse>("/lessons", { method: "POST", body: JSON.stringify(request) });
}

export function updateLesson(id: number, request: UpdateLessonRequest): Promise<LessonResponse> {
  return apiRequest<LessonResponse>(`/lessons/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function listLessonsByClass(classId: number): Promise<LessonResponse[]> {
  return apiRequest<LessonResponse[]>(`/classes/${classId}/lessons`);
}

export function listLessonsByCurriculum(curriculumId: number): Promise<LessonResponse[]> {
  return apiRequest<LessonResponse[]>(`/curriculums/${curriculumId}/lessons`);
}

export type LessonMaterialType = "VIDEO" | "PDF" | "AUDIO" | "SLIDE" | "IMAGE" | "OTHER";

/** Khớp AddLessonMaterialRequest thật — fileUrl lấy từ URL trả về sau khi upload thật qua uploadMedia (module LESSON_MATERIAL). */
export interface AddLessonMaterialRequest {
  materialType: LessonMaterialType;
  title: string;
  fileUrl: string;
  fileSizeBytes?: number;
  durationSeconds?: number;
  displayOrder?: number;
  isDownloadable: boolean;
}

export interface LessonMaterialResponse {
  id: number;
  lessonId: number;
  materialType: LessonMaterialType;
  title: string;
  fileUrl: string;
  fileSizeBytes: number | null;
  durationSeconds: number | null;
  displayOrder: number;
  isDownloadable: boolean;
}

export function addLessonMaterial(lessonId: number, request: AddLessonMaterialRequest): Promise<LessonMaterialResponse> {
  return apiRequest<LessonMaterialResponse>(`/lessons/${lessonId}/materials`, { method: "POST", body: JSON.stringify(request) });
}

export function listLessonMaterials(lessonId: number): Promise<LessonMaterialResponse[]> {
  return apiRequest<LessonMaterialResponse[]>(`/lessons/${lessonId}/materials`);
}

// ===================== Kho tài liệu tham khảo (UC-60) =====================

export type CurriculumDocumentType = "VIDEO" | "PDF" | "AUDIO" | "SLIDE" | "IMAGE" | "OTHER";
export type CurriculumDocumentStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

/** Khớp CurriculumDocumentResponse thật — độc lập với Lesson (UC-23), chỉ gắn theo curriculum, không gắn 1 bài giảng cụ thể nào. */
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

/** UC-60: chỉ tài khoản có quyền lms.document.manage (mặc định gán cho TEACHER) mới tạo/sửa được. */
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
