package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.UpdateQuestionRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.QuestionBankService;

import java.util.List;

/** UC-40: Soạn & giao đề kiểm tra (FR-LMS-10) — phần Ngân hàng câu hỏi, xem Javadoc QuestionBankService. */
@RestController
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    public QuestionBankController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @PostMapping("/api/question-banks")
    public ResponseEntity<QuestionBankResponse> createBank(@Valid @RequestBody CreateQuestionBankRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(questionBankService.createBank(request, actor.userId()));
    }

    @GetMapping("/api/curriculums/{curriculumId}/question-banks")
    public ResponseEntity<List<QuestionBankResponse>> listBanksByCurriculum(@PathVariable Long curriculumId) {
        return ResponseEntity.ok(questionBankService.listBanksByCurriculum(curriculumId));
    }

    @PostMapping("/api/questions")
    public ResponseEntity<QuestionResponse> createQuestion(@Valid @RequestBody CreateQuestionRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(questionBankService.createQuestion(request, actor.userId()));
    }

    @PutMapping("/api/questions/{id}")
    public ResponseEntity<QuestionResponse> updateQuestion(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateQuestionRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(questionBankService.updateQuestion(id, request, actor.userId()));
    }

    @GetMapping("/api/question-banks/{bankId}/questions")
    public ResponseEntity<List<QuestionResponse>> listQuestions(@PathVariable Long bankId) {
        return ResponseEntity.ok(questionBankService.listQuestions(bankId));
    }

    @GetMapping("/api/questions/{id}")
    public ResponseEntity<QuestionResponse> getQuestion(@PathVariable Long id) {
        return ResponseEntity.ok(questionBankService.getQuestion(id));
    }
}
