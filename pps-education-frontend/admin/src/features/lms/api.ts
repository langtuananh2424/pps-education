import { apiRequest, apiRequestBlob } from "@/lib/apiClient";

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
  /** V54 — chỉ dùng khi questionType=FILL_IN_BLANK, so khớp case-insensitive + trim khi tự chấm. */
  correctAnswerText?: string;
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
  correctAnswerText: string | null;
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
  correctAnswerText?: string;
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

/** File mẫu Word (.docx) — sinh ở backend (không cá nhân hoá theo bank, dùng chung cho mọi ngân hàng). Mẫu Excel dựng client-side, xem QuestionImportPanel.tsx. */
export function downloadQuestionImportWordTemplate(): Promise<Blob> {
  return apiRequestBlob("/question-imports/template.docx");
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
  /** V55 — dùng để dán trực tiếp vào Excel BTVN thay cho chọn dropdown khi danh sách quá dài. */
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

/** UC-40 Main Flow bước 3-4: giao đề cho 1 lớp + hạn nộp — tự chuyển đề sang PUBLISHED, báo học sinh ACTIVE trong lớp (hoặc targetStudentIds). */
export function assignExercise(exerciseId: number, request: AssignExerciseRequest): Promise<ExerciseAssignmentResponse> {
  return apiRequest<ExerciseAssignmentResponse>(`/exercises/${exerciseId}/assign`, { method: "POST", body: JSON.stringify(request) });
}

/** Giáo viên xem lại đã giao đề gì cho 1 lớp — API lọc sẵn status=ACTIVE. */
export function listAssignmentsForClass(classId: number): Promise<ExerciseAssignmentResponse[]> {
  return apiRequest<ExerciseAssignmentResponse[]>(`/classes/${classId}/exercises`);
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
