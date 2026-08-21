package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.domain.Exam;
import vn.com.pps.education.domain.Question;
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.QuestionImportResponse;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.UpdateQuestionRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ExamRepository;
import vn.com.pps.education.repository.QuestionRepository;

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
                request.choices(), request.structuredContent(), request.groupKey());
        return questionBankService.createQuestionInBank(
                exam.getQuestionBank(), resolved, actorUserId, false);
    }

    @Transactional
    public QuestionResponse updateQuestion(Long examId, Long questionId,
                                           UpdateQuestionRequest request, Long actorUserId) {
        return questionBankService.updateResolvedQuestion(
                questionOrThrow(examId, questionId), request, actorUserId);
    }

    @Transactional
    public QuestionImportResponse importQuestions(Long examId, MultipartFile file, Long actorUserId) {
        Exam exam = examOrThrow(examId);
        return questionImportService.importQuestionsIntoBank(
                exam.getQuestionBank(), file, actorUserId, false);
    }

    public byte[] buildWordTemplate() {
        return questionImportService.buildWordTemplate();
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
