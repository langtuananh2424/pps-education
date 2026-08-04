package vn.com.pps.education.controller;

import jakarta.validation.Valid;
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
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.QuestionImportResponse;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.UpdateQuestionRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ExamQuestionService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** UC-40 V75: API câu hỏi theo Đề — không expose questionBankId cho Giáo viên. */
@RestController
public class ExamQuestionController {

    private final ExamQuestionService examQuestionService;

    public ExamQuestionController(ExamQuestionService examQuestionService) {
        this.examQuestionService = examQuestionService;
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.view')")
    @GetMapping("/api/exams/{examId}/questions")
    public ResponseEntity<List<QuestionResponse>> listQuestions(@PathVariable Long examId) {
        return ResponseEntity.ok(examQuestionService.listQuestions(examId));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.view')")
    @GetMapping("/api/exams/{examId}/questions/{questionId}")
    public ResponseEntity<QuestionResponse> getQuestion(@PathVariable Long examId, @PathVariable Long questionId) {
        return ResponseEntity.ok(examQuestionService.getQuestion(examId, questionId));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping("/api/exams/{examId}/questions")
    public ResponseEntity<QuestionResponse> createQuestion(@PathVariable Long examId,
                                                            @Valid @RequestBody CreateExamQuestionRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.createQuestion(examId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.update')")
    @PutMapping("/api/exams/{examId}/questions/{questionId}")
    public ResponseEntity<QuestionResponse> updateQuestion(@PathVariable Long examId,
                                                            @PathVariable Long questionId,
                                                            @Valid @RequestBody UpdateQuestionRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.updateQuestion(examId, questionId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping(value = "/api/exams/{examId}/questions/import", consumes = "multipart/form-data")
    public ResponseEntity<QuestionImportResponse> importQuestions(@PathVariable Long examId,
                                                                   @RequestParam("file") MultipartFile file,
                                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.importQuestions(examId, file, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @GetMapping("/api/exams/question-imports/template.docx")
    public ResponseEntity<byte[]> downloadWordTemplate() {
        byte[] content = examQuestionService.buildWordTemplate();
        String filename = "mau-soan-cau-hoi.docx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename)
                .body(content);
    }
}
