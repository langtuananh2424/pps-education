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
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — hộp từ vựng hiển thị cho học sinh (WORD_BANK), tách khỏi `blanks` (đáp án đúng) để có thể chứa từ nhiễu. Bỏ trống = dùng `blanks` làm hộp từ như hành vi cũ. */
  wordBankOptions?: string[];
  /**
   * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — hộp từ vựng THAM KHẢO (tĩnh, không phải đáp án
   * đúng) hiện chung 1 lần phía trên cả nhóm câu FILL_IN_BLANK (FillInBlankGroupBuilder) — khớp hình
   * thức "khung từ" trong đề giấy gốc (Ex.1) trong khi mỗi câu vẫn tự chấm độc lập theo
   * correctAnswerText riêng (Cách B). Đặt CÙNG giá trị trên MỌI Question của nhóm (giống cách
   * referencePassage/audioUrl dùng chung của ListeningGroupBuilder), FE chỉ đọc từ câu đầu nhóm.
   */
  wordBox?: string[];
}

/** Khớp Question.Skill thật (Question.java) — KHÔNG phải free-text, backend chỉ nhận đúng 1 trong 6 giá trị này. */
export type QuestionSkill = "LISTENING" | "READING" | "WRITING" | "SPEAKING" | "GRAMMAR" | "OTHER";

/** Khớp Question.Difficulty thật. */
export type QuestionDifficulty = "EASY" | "MEDIUM" | "HARD";

export interface QuestionChoiceRequest {
  choiceLabel: string;
  content: string;
  /** V143 — ảnh riêng cho lựa chọn (câu hỏi Listening dạng chọn đáp án bằng hình), NULL = đáp án chữ. */
  imageUrl?: string;
  isCorrect: boolean;
  displayOrder: number;
}

export interface QuestionChoiceResponse {
  id: number;
  choiceLabel: string;
  content: string;
  /** V143 — ảnh riêng cho lựa chọn (câu hỏi Listening dạng chọn đáp án bằng hình), NULL = đáp án chữ. */
  imageUrl: string | null;
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
/**
 * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — {@code defaultKind}: khớp thói quen thật "1 Ex
 * chỉ 1 loại câu hỏi" nên cả file thường cùng 1 giá trị — GV chọn 1 lần ở panel Import thay vì gõ lại
 * cột "Loại câu hỏi" ở MỌI dòng. Dòng nào tự ghi giá trị riêng vẫn ưu tiên giá trị đó (xem
 * QuestionImportService.resolveKind() phía backend).
 */
export function importQuestions(bankId: number, file: File, defaultKind?: string): Promise<QuestionImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  if (defaultKind) formData.append("defaultKind", defaultKind);
  return apiRequest<QuestionImportResponse>(`/question-banks/${bankId}/questions/import`, { method: "POST", body: formData });
}

export function importExamQuestions(examId: number, file: File, defaultKind?: string): Promise<QuestionImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  if (defaultKind) formData.append("defaultKind", defaultKind);
  return apiRequest<QuestionImportResponse>(`/exams/${examId}/questions/import`, { method: "POST", body: formData });
}

/**
 * File mẫu Word generic legacy. `defaultKind` (bổ sung 2026-08-28, đã xác nhận với người dùng) — khi
 * GV đã chọn "Loại câu hỏi mặc định" ở panel Import, chỉ in đúng 1 ví dụ khớp loại đó thay vì cả bảng
 * tra cứu 12 loại (khớp thói quen "1 Ex chỉ 1 loại câu hỏi").
 */
export function downloadQuestionImportWordTemplate(defaultKind?: string): Promise<Blob> {
  const query = defaultKind ? `?defaultKind=${encodeURIComponent(defaultKind)}` : "";
  return apiRequestBlob(`/question-imports/template.docx${query}`);
}

/**
 * V75: File mẫu Word cho luồng Giáo viên theo Đề.
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — `skillCategory`/`teacherType` optional,
 * lọc template chỉ còn block khớp Nhóm kỹ năng của Bài đang soạn (bỏ trống = in đủ tất cả, mirror
 * hành vi backend QuestionImportService.buildWordTemplate). `defaultKind` (bổ sung 2026-08-28) ưu
 * tiên cao hơn — GV đã chọn 1 loại cụ thể thì chỉ in đúng 1 ví dụ loại đó.
 */
export function downloadExamQuestionImportWordTemplate(skillCategory?: string, teacherType?: string, defaultKind?: string): Promise<Blob> {
  const params = new URLSearchParams();
  if (skillCategory) params.set("skillCategory", skillCategory);
  if (teacherType) params.set("teacherType", teacherType);
  if (defaultKind) params.set("defaultKind", defaultKind);
  const query = params.toString();
  return apiRequestBlob(`/exams/question-imports/template.docx${query ? `?${query}` : ""}`);
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
  /** V144 — Lesson thuộc Sub Topic nào trong mục lục sách (Sách/Khối -> Unit -> Sub Topic -> Lesson). Bỏ trống = chưa phân loại vào cấu trúc mới. */
  subTopicId?: number;
}

export interface UpdateExamRequest {
  title: string;
  teacherType: ExamTeacherType;
  examType: ExamType;
  subTopicId?: number;
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
  questionBankId: number;
  subTopicId: number | null;
  subTopicTitle: string | null;
}

// ===================== V148: Curriculum (chương trình+khối) -> Sách -> Unit -> Sub Topic -> Lesson -> Bài =====================
// Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24 — Curriculum (chương trình+khối, VD
// "IELTS Grade 6") đã có sẵn (track/gradeLevel); thêm Sách/Unit/SubTopic làm 3 cấp điều hướng mới phía
// trên Lesson (Exam). V148: Unit trước đó (V144) gắn thẳng Curriculum — đổi sang gắn Sách vì "Khung
// chương trình" chỉ là khung, không phải nơi tạo Unit trực tiếp (phản hồi người dùng 2026-08-24).

export interface BookResponse {
  id: number;
  curriculumId: number;
  title: string;
  displayOrder: number;
}

export interface CreateBookRequest {
  title: string;
  displayOrder?: number;
}

export interface UnitResponse {
  id: number;
  bookId: number;
  title: string;
  displayOrder: number;
}

export interface CreateUnitRequest {
  title: string;
  displayOrder?: number;
}

export interface SubTopicResponse {
  id: number;
  unitId: number;
  title: string;
  displayOrder: number;
}

export interface CreateSubTopicRequest {
  title: string;
  displayOrder?: number;
}

export function listBooks(curriculumId: number): Promise<BookResponse[]> {
  return apiRequest<BookResponse[]>(`/curriculums/${curriculumId}/books`);
}

export function createBook(curriculumId: number, request: CreateBookRequest): Promise<BookResponse> {
  return apiRequest<BookResponse>(`/curriculums/${curriculumId}/books`, { method: "POST", body: JSON.stringify(request) });
}

export function listUnits(bookId: number): Promise<UnitResponse[]> {
  return apiRequest<UnitResponse[]>(`/books/${bookId}/units`);
}

export function createUnit(bookId: number, request: CreateUnitRequest): Promise<UnitResponse> {
  return apiRequest<UnitResponse>(`/books/${bookId}/units`, { method: "POST", body: JSON.stringify(request) });
}

export function listSubTopics(unitId: number): Promise<SubTopicResponse[]> {
  return apiRequest<SubTopicResponse[]>(`/units/${unitId}/sub-topics`);
}

export function createSubTopic(unitId: number, request: CreateSubTopicRequest): Promise<SubTopicResponse> {
  return apiRequest<SubTopicResponse>(`/units/${unitId}/sub-topics`, { method: "POST", body: JSON.stringify(request) });
}

// Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — sửa/xóa Sách/Unit/Sub Topic. Xóa BE tự
// chặn (400) nếu còn cấp con (Sách còn Unit / Unit còn Sub Topic) hoặc Sub Topic đang bị 1 Đề/Bộ video
// tham chiếu — lỗi hiện thẳng ra (err.message), không tự kiểm tra trước ở FE.

export interface UpdateBookRequest {
  title: string;
  displayOrder?: number;
}

export function updateBook(id: number, request: UpdateBookRequest): Promise<BookResponse> {
  return apiRequest<BookResponse>(`/books/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function deleteBook(id: number): Promise<void> {
  return apiRequest<void>(`/books/${id}`, { method: "DELETE" });
}

export interface UpdateUnitRequest {
  title: string;
  displayOrder?: number;
}

export function updateUnit(id: number, request: UpdateUnitRequest): Promise<UnitResponse> {
  return apiRequest<UnitResponse>(`/units/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function deleteUnit(id: number): Promise<void> {
  return apiRequest<void>(`/units/${id}`, { method: "DELETE" });
}

export interface UpdateSubTopicRequest {
  title: string;
  displayOrder?: number;
}

export function updateSubTopic(id: number, request: UpdateSubTopicRequest): Promise<SubTopicResponse> {
  return apiRequest<SubTopicResponse>(`/sub-topics/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function deleteSubTopic(id: number): Promise<void> {
  return apiRequest<void>(`/sub-topics/${id}`, { method: "DELETE" });
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

/**
 * V136 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-21) — nhóm kỹ năng của Bài, ĐỘC LẬP
 * với ExerciseType (cơ chế giao bài) và Exam.examType (mục đích sử dụng) — không thay thế field nào.
 * Cố định từ lúc tạo (không sửa được qua UpdateExerciseRequest). undefined/null = chưa phân loại (dữ
 * liệu cũ). Dùng để lọc dropdown "chọn đề Reading/Writing" ở Nhận xét học viên (UC-21).
 */
export type ExerciseSkillCategory = "READING" | "WRITING" | "VOCAB_GRAMMAR" | "LISTENING";

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
  /** V89/V100 — không truyền = dùng mặc định hệ thống (70%, exercises.pass_threshold_percent). */
  passThresholdPercent?: number;
  skillCategory?: ExerciseSkillCategory;
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
  /** Denormalize từ Exam.teacherType (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05) — dùng để lọc dropdown BTVN buổi sau ở Nhận xét học viên theo loại giáo viên. */
  examTeacherType: ExamTeacherType;
  subjectId: number | null;
  exerciseType: ExerciseType;
  /** V136 — null = chưa phân loại (dữ liệu cũ). */
  skillCategory: ExerciseSkillCategory | null;
  totalPoints: number;
  timeLimitMinutes: number | null;
  allowRetake: boolean;
  maxAttempts: number | null;
  showCorrectAnswers: boolean;
  /** V89/V100 — ngưỡng % để tính đạt/chưa đạt (exercises.pass_threshold_percent), mặc định 70. */
  passThresholdPercent: number;
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
  /** V89/V100 — không truyền = giữ nguyên giá trị hiện tại (backend chỉ ghi đè khi có giá trị). */
  passThresholdPercent?: number;
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

/** Khớp backend ExerciseQuestionChoiceResponse — KHÔNG kèm isCorrect (endpoint dùng chung với học sinh, xem Javadoc backend). */
export interface ExerciseQuestionChoiceResponse {
  id: number;
  choiceLabel: string;
  content: string;
  /** V143 — ảnh riêng cho lựa chọn (câu hỏi Listening dạng chọn đáp án bằng hình), NULL = đáp án chữ. */
  imageUrl: string | null;
  displayOrder: number;
}

export interface ExerciseQuestionResponse {
  id: number;
  exerciseId: number;
  questionId: number;
  questionType: QuestionType;
  questionContent: string;
  displayOrder: number;
  points: number;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — trước đây thiếu field này trong
   * type khai báo dù backend luôn trả kèm choices, khiến GV không tra được đáp án học sinh đã chọn
   * (chỉ thấy raw choice id) ở màn "Chi tiết kết quả" — xem AssignmentStatsDetailPage.tsx.
   */
  choices: ExerciseQuestionChoiceResponse[];
  skill: QuestionSkill | null;
  audioUrl: string | null;
  referencePassage: string | null;
  structuredContent: QuestionStructuredContent | null;
  groupKey: string | null;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-03 — ảnh minh họa (ESSAY/WORD_BANK/SENTENCE_BUILDING), backend luôn trả kèm nhưng type khai báo trước đây thiếu field này (mirror portal/src/features/portal/api.ts). */
  imageUrl: string | null;
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
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — sửa điểm 1 câu hỏi đã gắn vào Bài.
 * Chỉ sửa được khi Bài còn DRAFT; backend chặn nếu tổng điểm vượt quá exercise.totalPoints.
 */
export function updateExerciseQuestionPoints(exerciseId: number, exerciseQuestionId: number, points: number): Promise<ExerciseQuestionResponse> {
  return apiRequest<ExerciseQuestionResponse>(`/exercises/${exerciseId}/questions/${exerciseQuestionId}/points`, {
    method: "PUT",
    body: JSON.stringify({ points })
  });
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

/**
 * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — "Lô giao BTVN theo kỹ năng":
 * 1 entry/Lesson (Đề) có >=1 Bài Published cùng skillCategory, thay cho danh sách Exercise lẻ/bản gộp
 * cũ ở kênh "BTVN online" của Nhận xét học viên (UC-21). Chọn 1 nhóm = giao TOÀN BỘ exerciseCount Bài
 * trong đó cùng lúc — value gửi lên (homeworkNext*ExerciseId trong CreateStudentCommentRequest) là
 * examId của nhóm này, KHÔNG còn là 1 exerciseId đơn.
 */
export interface HomeworkSkillGroupResponse {
  examId: number;
  examCode: string;
  examTitle: string;
  examTeacherType: ExamTeacherType;
  skillCategory: ExerciseSkillCategory;
  exerciseCount: number;
  questionCount: number;
}

export function listHomeworkSkillGroupsForClass(classId: number, skillCategory: ExerciseSkillCategory): Promise<HomeworkSkillGroupResponse[]> {
  return apiRequest<HomeworkSkillGroupResponse[]>(`/classes/${classId}/homework-skill-groups?skillCategory=${skillCategory}`);
}

// ===================== Kho Video Ôn tập (UC-23/UC-23a) =====================

/** Khớp ReviewVideoSet.VideoType thật — CONNECTION: ôn từ vựng buổi học; REFLEX: hỏi-đáp luyện nói. */
export type ReviewVideoType = "CONNECTION" | "REFLEX";
export type ReviewVideoSetStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

/** V98, đã xác nhận với người dùng 2026-08-06: Bộ dành cho GV Việt Nam hay GV nước ngoài — dùng lọc khi giao bài (mirror ExamTeacherType). */
export type ReviewVideoTeacherType = "VIETNAMESE" | "FOREIGN";

/**
 * V98 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) —
 * đổi mô hình gán lớp giống hệt Kho đề: curriculumId luôn khác null
 * (CHỈ dùng lọc/tìm kiếm trong Kho Video), điều kiện hiển thị cho lớp là
 * gán tường minh riêng (xem assignReviewVideoSetToClass) — classId
 * không còn trên response.
 */
export interface ReviewVideoSetResponse {
  id: number;
  /** V55 — dùng để dán trực tiếp vào Excel BTVN thay cho chọn dropdown khi danh sách quá dài. */
  uuid: string;
  code: string;
  title: string;
  videoType: ReviewVideoType;
  curriculumId: number;
  curriculumCode: string;
  subjectId: number | null;
  teacherType: ReviewVideoTeacherType;
  displayOrder: number;
  status: ReviewVideoSetStatus;
  publishedAt: string | null;
  createdBy: number;
  /** V155 — Bộ thuộc Sub Topic nào trong mục lục sách (Sách/Khối -> Unit -> Sub Topic -> Bộ). NULL = chưa phân loại vào cấu trúc mới. */
  subTopicId: number | null;
  subTopicTitle: string | null;
}

export interface CreateReviewVideoSetRequest {
  code: string;
  title: string;
  videoType: ReviewVideoType;
  curriculumId: number;
  teacherType: ReviewVideoTeacherType;
  subjectId?: number;
  displayOrder?: number;
  subTopicId?: number;
}

/** Khớp UpdateReviewVideoSetRequest thật — không đổi được code/khung chương trình sau khi tạo, teacherType sửa được cùng title (V98). Đổi status để công bố (PUBLISHED, publishedAt chỉ set 1 lần) hoặc gỡ (ARCHIVED, soft-remove). */
export interface UpdateReviewVideoSetRequest {
  title: string;
  teacherType: ReviewVideoTeacherType;
  subjectId?: number;
  displayOrder?: number;
  status: ReviewVideoSetStatus;
  subTopicId?: number;
}

/** UC-23 Main Flow bước 1: chỉ Giáo viên được phân công dạy 1 lớp thuộc đúng khung mới tạo được — BE tự chặn theo class_teachers, không phải theo permission. */
export function createReviewVideoSet(request: CreateReviewVideoSetRequest): Promise<ReviewVideoSetResponse> {
  return apiRequest<ReviewVideoSetResponse>("/review-video-sets", { method: "POST", body: JSON.stringify(request) });
}

export function updateReviewVideoSet(id: number, request: UpdateReviewVideoSetRequest): Promise<ReviewVideoSetResponse> {
  return apiRequest<ReviewVideoSetResponse>(`/review-video-sets/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

/** V156 — "Xóa Bộ" (soft-delete), chỉ xóa được khi Bộ đã hết Video (BE tự chặn 400 nếu còn). */
export function deleteReviewVideoSet(id: number): Promise<void> {
  return apiRequest<void>(`/review-video-sets/${id}`, { method: "DELETE" });
}

export function listReviewVideoSetsByClass(classId: number): Promise<ReviewVideoSetResponse[]> {
  return apiRequest<ReviewVideoSetResponse[]>(`/classes/${classId}/review-video-sets`);
}

/** Kho Video — lọc theo khung chương trình/loại giáo viên (V98, mirror listExams). Bỏ trống để không lọc theo tiêu chí đó. */
export function listReviewVideoSets(curriculumId?: number, teacherType?: ReviewVideoTeacherType): Promise<ReviewVideoSetResponse[]> {
  const params = new URLSearchParams();
  if (curriculumId) params.set("curriculumId", String(curriculumId));
  if (teacherType) params.set("teacherType", teacherType);
  const query = params.toString() ? `?${params.toString()}` : "";
  return apiRequest<ReviewVideoSetResponse[]>(`/review-video-sets${query}`);
}

/** V98 (mirror assignExamToClass) — gán Bộ cho 1 lớp, điều kiện hiển thị DUY NHẤT cho học sinh lớp đó. */
export function assignReviewVideoSetToClass(setId: number, classId: number): Promise<void> {
  return apiRequest<void>(`/review-video-sets/${setId}/classes/${classId}`, { method: "POST" });
}

export function unassignReviewVideoSetFromClass(setId: number, classId: number): Promise<void> {
  return apiRequest<void>(`/review-video-sets/${setId}/classes/${classId}`, { method: "DELETE" });
}

export function listReviewVideoSetAssignedClasses(setId: number): Promise<ClassResponse[]> {
  return apiRequest<ClassResponse[]>(`/review-video-sets/${setId}/classes`);
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

/**
 * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — 1 dòng "BTVN Video Ôn tập" cho
 * trang "Thống kê BTVN theo lớp" (gộp cùng ExerciseAssignmentStatsResponse ở features/academic/api.ts).
 * Không có passedCount/passRatePercent — Video Ôn tập chưa có khái niệm ngưỡng điểm đạt/rớt trong schema.
 */
export interface ReviewVideoAssignmentStatsResponse {
  assignmentId: number;
  reviewVideoSetId: number;
  reviewVideoSetCode: string;
  reviewVideoSetTitle: string;
  videoType: ReviewVideoType;
  /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — dùng lọc GV Việt Nam/nước ngoài ở UC-66. */
  teacherType: ReviewVideoTeacherType;
  availableFrom: string;
  dueAt: string | null;
  status: "ACTIVE" | "CANCELLED" | "COMPLETED";
  totalStudents: number;
  completedCount: number;
  completionPercent: number;
  /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — chỉ có giá trị cho bộ CONNECTION (điểm trắc nghiệm ≥ ngưỡng % pass). NULL cho bộ REFLEX — chưa có khái niệm đạt/rớt. */
  passedCount: number | null;
  passRatePercent: number | null;
}

export function listReviewVideoAssignmentStatsForClass(classId: number): Promise<ReviewVideoAssignmentStatsResponse[]> {
  return apiRequest<ReviewVideoAssignmentStatsResponse[]>(`/classes/${classId}/review-video-assignments/stats`);
}

/**
 * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — trang "Xem chi tiết" 1
 * BTVN Video Ôn tập cụ thể (mirror ExerciseAssignmentStudentStatsResponse ở features/academic/api.ts).
 * Field CONNECTION-only/REFLEX-only null ở nhóm còn lại — 1 response luôn cùng 1 videoType.
 */
export interface ReviewVideoAssignmentStudentRow {
  studentId: number;
  studentCode: string;
  studentFullName: string;
  viewCount: number;
  requiredViewCount: number;
  completed: boolean;
  /** CONNECTION only */
  correctCount: number | null;
  totalQuestions: number | null;
  passed: boolean | null;
  /** REFLEX only */
  answeredQuestionCount: number | null;
  totalReflexQuestions: number | null;
  averageScore: number | null;
  averageMaxScore: number | null;
}

export interface ReviewVideoAssignmentStudentStatsResponse {
  assignment: ReviewVideoAssignmentStatsResponse;
  students: ReviewVideoAssignmentStudentRow[];
}

export function getReviewVideoAssignmentStudentStats(assignmentId: number): Promise<ReviewVideoAssignmentStudentStatsResponse> {
  return apiRequest<ReviewVideoAssignmentStudentStatsResponse>(`/review-video-assignments/${assignmentId}/stats/students`);
}

/** CONNECTION only — phân tích câu hay bị sai, mirror ExerciseAssignmentQuestionStatsResponse. Rỗng cho assignment REFLEX. */
export interface ReviewVideoAssignmentWrongStudent {
  studentId: number;
  studentCode: string;
  studentFullName: string;
}

export interface ReviewVideoAssignmentQuestionRow {
  questionId: number;
  reviewVideoId: number;
  reviewVideoTitle: string;
  displayOrder: number;
  prompt: string;
  answeredCount: number;
  wrongCount: number;
  wrongRatePercent: number;
  wrongStudents: ReviewVideoAssignmentWrongStudent[];
}

export interface ReviewVideoAssignmentQuestionStatsResponse {
  questions: ReviewVideoAssignmentQuestionRow[];
}

export function getReviewVideoAssignmentQuestionStats(assignmentId: number): Promise<ReviewVideoAssignmentQuestionStatsResponse> {
  return apiRequest<ReviewVideoAssignmentQuestionStatsResponse>(`/review-video-assignments/${assignmentId}/stats/questions`);
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

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — BE tự chặn (400) nếu video đã có học sinh xem/làm bài. */
export function deleteReviewVideo(videoId: number): Promise<void> {
  return apiRequest<void>(`/review-videos/${videoId}`, { method: "DELETE" });
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

/** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — sửa 1 câu hỏi REFLEX đã có (trước đây chỉ thêm mới được). */
export type UpdateReviewVideoQuestionRequest = AddReviewVideoQuestionRequest;

export function updateReviewVideoQuestion(questionId: number, request: UpdateReviewVideoQuestionRequest): Promise<ReviewVideoQuestionResponse> {
  return apiRequest<ReviewVideoQuestionResponse>(`/review-video-questions/${questionId}`, { method: "PUT", body: JSON.stringify(request) });
}

// ===================== Kho Video Ôn tập — Import Excel câu hỏi (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) =====================

export interface ReviewVideoQuestionImportedRow {
  id: number;
  summary: string;
}

export interface ReviewVideoQuestionImportResponse {
  jobId: number;
  sourceFileName: string;
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: { row: number; reason: string }[];
  createdQuestions: ReviewVideoQuestionImportedRow[];
}

/** Soạn hàng loạt câu hỏi REFLEX (mốc thời gian + ghi âm) qua file .xlsx theo mẫu — mirror importQuestions (Kho đề, UC-40). */
export function importReviewVideoQuestions(videoId: number, file: File): Promise<ReviewVideoQuestionImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<ReviewVideoQuestionImportResponse>(`/review-videos/${videoId}/questions/import`, { method: "POST", body: formData });
}

// ===================== Kho Video Ôn tập — Chấm bài Video phản xạ (UC-23b Main Flow bước 3-4) =====================

/** UC-23b: dùng chung cho cả Học sinh xem bài của mình và Giáo viên xem danh sách/chấm điểm. 1 dòng = 1 attempt (giữ lịch sử). */
export interface ReviewVideoSubmissionResponse {
  id: number;
  reviewVideoQuestionId: number;
  attemptNumber: number;
  studentId: number;
  studentFullName: string;
  audioUrl: string;
  submittedAt: string;
  score: number | null;
  maxScore: number | null;
  feedback: string | null;
  gradedByUserId: number | null;
  gradedAt: string | null;
  /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — chỉ có giá trị khi trả về từ listReviewVideoSubmissionsForClass (hàng chờ chấm gộp nhiều Bộ theo lớp), null ở mọi nơi khác. */
  reviewVideoSetId: number | null;
  reviewVideoSetTitle: string | null;
  reviewVideoId: number | null;
  reviewVideoTitle: string | null;
  reviewVideoDisplayOrder: number | null;
  questionPrompt: string | null;
  timestampSeconds: number | null;
}

/** Giáo viên xem danh sách bài audio đã nộp theo bộ + lớp cụ thể — chỉ attempt MỚI NHẤT mỗi (câu hỏi, học sinh). classId bắt buộc (BE từ chối nếu thiếu). */
export function listReviewVideoSubmissionsForGrading(setId: number, classId: number): Promise<ReviewVideoSubmissionResponse[]> {
  return apiRequest<ReviewVideoSubmissionResponse[]>(`/review-video-sets/${setId}/submissions?classId=${classId}`);
}

/** Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — tóm tắt số bài chưa chấm theo TỪNG lớp giáo viên đang đứng lớp thật, dùng cho badge Sidebar + landing "Hàng chờ chấm bài". */
export interface PendingGradingClassSummaryResponse {
  classId: number;
  classCode: string;
  className: string;
  pendingSubmissionCount: number;
}

export function listPendingGradingClasses(): Promise<PendingGradingClassSummaryResponse[]> {
  return apiRequest<PendingGradingClassSummaryResponse[]>("/review-video-submissions/pending-grading");
}

/** Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — hàng chờ chấm GỘP mọi Bộ REFLEX đã gán cho 1 lớp, không cần tự chọn Bộ trước. */
export function listReviewVideoSubmissionsForClass(classId: number): Promise<ReviewVideoSubmissionResponse[]> {
  return apiRequest<ReviewVideoSubmissionResponse[]>(`/classes/${classId}/review-video-submissions`);
}

export interface GradeReviewVideoSubmissionRequest {
  score: number;
  maxScore: number;
  feedback?: string;
}

export function gradeReviewVideoSubmission(submissionId: number, request: GradeReviewVideoSubmissionRequest): Promise<ReviewVideoSubmissionResponse> {
  return apiRequest<ReviewVideoSubmissionResponse>(`/review-video-submissions/${submissionId}/grade`, {
    method: "POST",
    body: JSON.stringify(request)
  });
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

/** Soạn hàng loạt câu hỏi trắc nghiệm CONNECTION qua file .xlsx theo mẫu — mirror importReviewVideoQuestions (REFLEX). */
export function importReviewVideoConnectionQuestions(videoId: number, file: File): Promise<ReviewVideoQuestionImportResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<ReviewVideoQuestionImportResponse>(`/review-videos/${videoId}/connection-questions/import`, { method: "POST", body: formData });
}

export function listReviewVideoConnectionQuestions(videoId: number): Promise<ReviewVideoConnectionQuestionResponse[]> {
  return apiRequest<ReviewVideoConnectionQuestionResponse[]>(`/review-videos/${videoId}/connection-questions`);
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — sửa nội dung câu hỏi + nội dung/đáp
 * án đúng của từng đáp án ĐÃ CÓ (trước đây chỉ thêm mới được). choiceId bắt buộc khớp đúng đáp án đang
 * sửa — KHÔNG thêm/bớt số lượng đáp án qua đường này (xem UpdateConnectionChoiceRequest phía backend).
 */
export interface UpdateConnectionChoiceRequest {
  choiceId: number;
  content: string;
  isCorrect: boolean;
}

export interface UpdateReviewVideoConnectionQuestionRequest {
  prompt: string;
  displayOrder?: number;
  choices: UpdateConnectionChoiceRequest[];
}

export function updateReviewVideoConnectionQuestion(
  questionId: number,
  request: UpdateReviewVideoConnectionQuestionRequest
): Promise<ReviewVideoConnectionQuestionResponse> {
  return apiRequest<ReviewVideoConnectionQuestionResponse>(`/review-video-connection-questions/${questionId}`, {
    method: "PUT",
    body: JSON.stringify(request)
  });
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

// ===================== Chờ chấm thủ công (UC-41/UC-26) — dùng cho Dashboard Giáo viên =====================

/** UC-41 Main Flow bước 1: hàng chờ chấm tay của bài tập/đề (Essay/Speaking) thuộc thẩm quyền GV đang đăng nhập. */
export interface PendingGradingResponse {
  studentAnswerId: number;
  exerciseAttemptId: number;
  exerciseId: number;
  exerciseTitle: string;
  studentId: number;
  studentFullName: string;
  questionId: number;
  questionType: string;
  questionContent: string;
  answerText: string | null;
  audioAnswerUrl: string | null;
}

export function listPendingGrading(): Promise<PendingGradingResponse[]> {
  return apiRequest<PendingGradingResponse[]>("/grading/pending");
}

/** UC-26: hàng chờ chấm tay riêng cho lượt luyện Nói đã nộp — tách khỏi ManualGradingController. */
export interface PendingListeningGradingResponse {
  practiceAttemptId: number;
  practiceItemId: number;
  practiceItemTitle: string;
  studentId: number;
  studentFullName: string;
  audioAnswerUrl: string | null;
  scriptText: string | null;
}

export function listPendingListeningGrading(): Promise<PendingListeningGradingResponse[]> {
  return apiRequest<PendingListeningGradingResponse[]>("/listening-practice/grading/pending");
}
