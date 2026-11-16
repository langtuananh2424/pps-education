package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.lms.domain.Exam;
import vn.com.pps.education.lms.domain.Question;
import vn.com.pps.education.lms.dto.CreateCkeditorChoiceExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateCkeditorChunkExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateCkeditorGroupExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateCkeditorWordBankExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateCkeditorEssayExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateQuestionRequest;
import vn.com.pps.education.lms.dto.KeyGrammarStructureResponse;
import vn.com.pps.education.lms.dto.QuestionImportResponse;
import vn.com.pps.education.lms.dto.QuestionResponse;
import vn.com.pps.education.lms.dto.UpdateCkeditorQuestionRequest;
import vn.com.pps.education.lms.dto.UpdateQuestionRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.lms.repository.ExamRepository;
import vn.com.pps.education.lms.repository.QuestionRepository;

import java.util.List;

/**
 * UC-40 V75: Soạn/sửa/import câu hỏi qua Đề. Service tự resolve ngân hàng
 * nội bộ; client không biết/truyền questionBankId. Import/soạn mới cho phép
 * trùng nội dung theo quyết định 2026-08-04.
 */
@Service
public class ExamQuestionService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final QuestionBankService questionBankService;
    private final QuestionImportService questionImportService;

    public ExamQuestionService(ExamRepository examRepository,
                               QuestionRepository questionRepository,
                               QuestionBankService questionBankService,
                               QuestionImportService questionImportService) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.questionBankService = questionBankService;
        this.questionImportService = questionImportService;
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listQuestions(Long examId) {
        Exam exam = examOrThrow(examId);
        return questionBankService.listQuestionsInBank(exam.getQuestionBank().getId());
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(Long examId, Long questionId) {
        return questionBankService.toResponseForResolvedQuestion(questionOrThrow(examId, questionId));
    }

    @Transactional
    public QuestionResponse createQuestion(Long examId, CreateExamQuestionRequest request, Long actorUserId) {
        Exam exam = examOrThrow(examId);
        CreateQuestionRequest resolved = new CreateQuestionRequest(
                exam.getQuestionBank().getId(), request.questionType(), request.skill(), request.difficulty(),
                request.content(), request.audioUrl(), request.imageUrl(), request.referencePassage(),
                request.explanation(), request.correctAnswerText(), request.defaultPoints(), request.tags(),
                request.choices(), request.structuredContent(), request.groupKey(), null, request.tapescript());
        return questionBankService.createQuestionInBank(
                exam.getQuestionBank(), resolved, actorUserId, false);
    }

    /** Mirror {@link #createQuestion} cho "Ô trống"/"Hộp từ vựng" soạn bằng CKEditor5 — xem Javadoc {@link CreateCkeditorWordBankExamQuestionRequest}. */
    @Transactional
    public QuestionResponse createCkeditorWordBankQuestion(Long examId, CreateCkeditorWordBankExamQuestionRequest request, Long actorUserId) {
        Exam exam = examOrThrow(examId);
        CreateQuestionRequest resolved = questionBankService.buildCkeditorWordBankRequest(
                exam.getQuestionBank().getId(), request.html(), request.skill(), request.difficulty(),
                request.audioUrl(), request.imageUrl(), request.referencePassage(), request.explanation(),
                request.defaultPoints(), request.tags(), request.groupKey(), request.displayNumberOverride(), request.tapescript(),
                request.displayNumberLabel());
        return questionBankService.createQuestionInBank(exam.getQuestionBank(), resolved, actorUserId, false);
    }

    /** Mirror {@link #createCkeditorWordBankQuestion} cho "Tự luận" soạn bằng CKEditor5 — xem Javadoc {@link CreateCkeditorEssayExamQuestionRequest}. */
    @Transactional
    public QuestionResponse createCkeditorEssayQuestion(Long examId, CreateCkeditorEssayExamQuestionRequest request, Long actorUserId) {
        Exam exam = examOrThrow(examId);
        CreateQuestionRequest resolved = questionBankService.buildCkeditorEssayRequest(
                exam.getQuestionBank().getId(), request.html(), request.skill(), request.difficulty(),
                request.audioUrl(), request.imageUrl(), request.referencePassage(), request.explanation(),
                request.defaultPoints(), request.tags(), request.groupKey(), request.displayNumberOverride(), request.tapescript(),
                request.displayNumberLabel());
        return questionBankService.createQuestionInBank(exam.getQuestionBank(), resolved, actorUserId, false);
    }

    /** Mirror {@link #createCkeditorWordBankQuestion} cho "Nhóm lựa chọn" soạn bằng CKEditor5 — xem Javadoc {@link CreateCkeditorChoiceExamQuestionRequest}. */
    @Transactional
    public QuestionResponse createCkeditorChoiceQuestion(Long examId, CreateCkeditorChoiceExamQuestionRequest request, Long actorUserId) {
        Exam exam = examOrThrow(examId);
        CreateQuestionRequest resolved = questionBankService.buildCkeditorChoiceRequest(
                exam.getQuestionBank().getId(), request.html(), request.skill(), request.difficulty(),
                request.audioUrl(), request.imageUrl(), request.referencePassage(), request.explanation(),
                request.defaultPoints(), request.tags(), request.groupKey(), request.displayNumberOverride(), request.tapescript(),
                request.displayNumberLabel());
        return questionBankService.createQuestionInBank(exam.getQuestionBank(), resolved, actorUserId, false);
    }

    /** Mirror {@link #createCkeditorWordBankQuestion} cho "Khối sắp xếp" soạn bằng CKEditor5 — xem Javadoc {@link CreateCkeditorChunkExamQuestionRequest}. */
    @Transactional
    public QuestionResponse createCkeditorChunkQuestion(Long examId, CreateCkeditorChunkExamQuestionRequest request, Long actorUserId) {
        Exam exam = examOrThrow(examId);
        CreateQuestionRequest resolved = questionBankService.buildCkeditorChunkRequest(
                exam.getQuestionBank().getId(), request.html(), request.skill(), request.difficulty(),
                request.audioUrl(), request.imageUrl(), request.referencePassage(), request.explanation(),
                request.defaultPoints(), request.tags(), request.groupKey(), request.displayNumberOverride(), request.tapescript(),
                request.displayNumberLabel());
        return questionBankService.createQuestionInBank(exam.getQuestionBank(), resolved, actorUserId, false);
    }

    /** "Bảng lưới"/"Lựa chọn trong câu" soạn bằng CKEditor5 — 1 lần gửi tạo NHIỀU câu cùng nhóm, xem Javadoc {@link CreateCkeditorGroupExamQuestionRequest}. */
    @Transactional
    public List<QuestionResponse> createCkeditorGroupQuestions(Long examId, CreateCkeditorGroupExamQuestionRequest request, Long actorUserId) {
        Exam exam = examOrThrow(examId);
        return questionBankService.buildCkeditorGroupRequests(
                        exam.getQuestionBank().getId(), request.html(), request.difficulty(), request.explanation(), request.defaultPoints())
                .stream()
                .map(resolved -> questionBankService.createQuestionInBank(exam.getQuestionBank(), resolved, actorUserId, false))
                .toList();
    }

    /** Bài nhiều phần (ngăn bằng đường kẻ ngang) — xem QuestionBankService#buildCkeditorMultipartRequests. */
    @Transactional
    public List<QuestionResponse> createCkeditorMultipartQuestions(Long examId, CreateCkeditorGroupExamQuestionRequest request, Long actorUserId) {
        Exam exam = examOrThrow(examId);
        return questionBankService.buildCkeditorMultipartRequests(
                        exam.getQuestionBank().getId(), request.html(), request.difficulty(), request.explanation(), request.defaultPoints())
                .stream()
                .map(resolved -> questionBankService.createQuestionInBank(exam.getQuestionBank(), resolved, actorUserId, false))
                .toList();
    }

    @Transactional
    public QuestionResponse updateQuestion(Long examId, Long questionId,
                                           UpdateQuestionRequest request, Long actorUserId) {
        return questionBankService.updateResolvedQuestion(
                questionOrThrow(examId, questionId), request, actorUserId);
    }

    /** Sửa câu hỏi tạo bằng CKEditor5 theo Đề — xem QuestionBankService#buildCkeditorUpdateRequest. */
    @Transactional
    public QuestionResponse updateCkeditorQuestion(Long examId, Long questionId,
                                                   UpdateCkeditorQuestionRequest request, Long actorUserId) {
        Question question = questionOrThrow(examId, questionId);
        return questionBankService.updateResolvedQuestion(
                question, questionBankService.buildCkeditorUpdateRequest(question, request), actorUserId);
    }

    /**
     * Key Grammar (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — danh sách mã cấu trúc
     * khả dụng cho dropdown chọn tay ở modal "Sửa câu hỏi" (FE, ExerciseAssignPage.tsx). Rỗng nếu câu hỏi
     * không phải ESSAY hoặc Khối/chương trình chưa có từ điển.
     */
    @Transactional(readOnly = true)
    public List<KeyGrammarStructureResponse> listKeyGrammarOptions(Long examId, Long questionId) {
        return questionBankService.listKeyGrammarOptionsForResolvedQuestion(questionOrThrow(examId, questionId));
    }

    /**
     * Bổ sung 2026-08-28 (đã xác nhận với người dùng) — {@code defaultKind}: xem Javadoc
     * QuestionImportService#importQuestions(Long, MultipartFile, Long, String).
     */
    @Transactional
    public QuestionImportResponse importQuestions(Long examId, MultipartFile file, Long actorUserId, String defaultKind) {
        Exam exam = examOrThrow(examId);
        return questionImportService.importQuestionsIntoBank(
                exam.getQuestionBank(), file, actorUserId, false, defaultKind, false);
    }

    /**
     * Bổ sung 2026-10-19 (đã xác nhận với người dùng khi review UX import) — xem trước TRƯỚC KHI
     * ghi DB, mirror QuestionImportService#previewImport. readOnly=true, không tạo ImportJob/
     * Question/Choice nào — xem Javadoc importQuestionsIntoBank(dryRun).
     */
    @Transactional(readOnly = true)
    public QuestionImportResponse previewImport(Long examId, MultipartFile file, Long actorUserId, String defaultKind) {
        Exam exam = examOrThrow(examId);
        return questionImportService.importQuestionsIntoBank(
                exam.getQuestionBank(), file, actorUserId, false, defaultKind, true);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — {@code skillCategory}/
     * {@code teacherType} optional, lọc template chỉ còn block khớp Nhóm kỹ năng đã chọn ở bước 1
     * "Soạn Bài mới" (null = in đủ tất cả, xem QuestionImportService.buildWordTemplate). {@code
     * defaultKind} (bổ sung 2026-08-28) ưu tiên cao hơn — GV đã chọn 1 loại cụ thể ở panel Import thì
     * chỉ in đúng 1 ví dụ loại đó.
     */
    public byte[] buildWordTemplate(String skillCategory, String teacherType, String defaultKind) {
        return questionImportService.buildWordTemplate(skillCategory, teacherType, defaultKind);
    }

    /** V87 (merge từ develop 2026-08-04) — không lộ Đề đã "xóa" (deleted_at), cùng pattern ExamService. */
    private Exam examOrThrow(Long id) {
        return examRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.examQuestion.examNotFound",
                        new Object[]{id}, "Không tìm thấy Đề id=" + id));
    }

    private Question questionOrThrow(Long examId, Long questionId) {
        Exam exam = examOrThrow(examId);
        return questionRepository.findByIdAndQuestionBankId(questionId, exam.getQuestionBank().getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.examQuestion.questionNotFoundInExam",
                        new Object[]{questionId, examId},
                        "Không tìm thấy câu hỏi id=" + questionId + " trong Đề id=" + examId));
    }
}
