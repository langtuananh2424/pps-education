package vn.com.pps.education.lms.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.lms.dto.CreateQuestionBankRequest;
import vn.com.pps.education.lms.dto.CreateQuestionRequest;
import vn.com.pps.education.lms.dto.QuestionBankResponse;
import vn.com.pps.education.lms.dto.QuestionResponse;
import vn.com.pps.education.lms.dto.QuestionSearchItemResponse;
import vn.com.pps.education.lms.dto.UpdateQuestionBankStatusRequest;
import vn.com.pps.education.lms.dto.UpdateQuestionRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.lms.service.QuestionBankService;

import java.util.List;

/** UC-40: Soạn & giao đề kiểm tra (FR-LMS-10) — phần Ngân hàng câu hỏi, xem Javadoc QuestionBankService. */
@RestController
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    public QuestionBankController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.create')")
    @PostMapping("/api/question-banks")
    public ResponseEntity<QuestionBankResponse> createBank(@Valid @RequestBody CreateQuestionBankRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(questionBankService.createBank(request, actor.userId()));
    }

    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.view')")
    @GetMapping("/api/curriculums/{curriculumId}/question-banks")
    public ResponseEntity<List<QuestionBankResponse>> listBanksByCurriculum(@PathVariable Long curriculumId) {
        return ResponseEntity.ok(questionBankService.listBanksByCurriculum(curriculumId));
    }

    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.update')")
    @PutMapping("/api/question-banks/{id}/status")
    public ResponseEntity<QuestionBankResponse> updateBankStatus(@PathVariable Long id,
                                                                    @Valid @RequestBody UpdateQuestionBankStatusRequest request,
                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(questionBankService.updateBankStatus(id, request, actor.userId()));
    }

    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.create')")
    @PostMapping("/api/questions")
    public ResponseEntity<QuestionResponse> createQuestion(@Valid @RequestBody CreateQuestionRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(questionBankService.createQuestion(request, actor.userId()));
    }

    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.update')")
    @PutMapping("/api/questions/{id}")
    public ResponseEntity<QuestionResponse> updateQuestion(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateQuestionRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(questionBankService.updateQuestion(id, request, actor.userId()));
    }

    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.view')")
    @GetMapping("/api/question-banks/{bankId}/questions")
    public ResponseEntity<List<QuestionResponse>> listQuestions(@PathVariable Long bankId) {
        return ResponseEntity.ok(questionBankService.listQuestions(bankId));
    }

    /**
     * Trang "Ngân hàng câu hỏi" (lọc + phân trang xuyên mọi khung chương trình/ngân hàng) — bổ
     * sung 2026-10-19, đã xác nhận với người dùng khi review hiệu năng, thay cho cách FE cũ tự
     * tải toàn bộ rồi lọc/phân trang phía client. Xem Javadoc QuestionBankService#searchQuestions.
     */
    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.view')")
    @GetMapping("/api/questions/search")
    public ResponseEntity<Page<QuestionSearchItemResponse>> searchQuestions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(questionBankService.searchQuestions(search, questionType, difficulty, level, page, size));
    }

    /**
     * Chỉ dành cho GV/quản lý ngân hàng câu hỏi — trả về cả is_correct nên
     * TUYỆT ĐỐI không cho Học viên gọi trực tiếp (đáp án đúng chỉ lộ ra
     * cho Học viên qua ExerciseAttemptService sau khi nộp bài, có kiểm
     * soát show_correct_answers — xem Javadoc ExerciseAttemptService).
     */
    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.view')")
    @GetMapping("/api/questions/{id}")
    public ResponseEntity<QuestionResponse> getQuestion(@PathVariable Long id) {
        return ResponseEntity.ok(questionBankService.getQuestion(id));
    }
}
