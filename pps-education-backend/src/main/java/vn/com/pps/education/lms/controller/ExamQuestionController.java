package vn.com.pps.education.lms.controller;

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
import vn.com.pps.education.lms.dto.CreateCkeditorChoiceExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateCkeditorChunkExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateCkeditorGroupExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateCkeditorWordBankExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateCkeditorEssayExamQuestionRequest;
import vn.com.pps.education.lms.dto.CreateExamQuestionRequest;
import vn.com.pps.education.lms.dto.KeyGrammarStructureResponse;
import vn.com.pps.education.lms.dto.QuestionImportResponse;
import vn.com.pps.education.lms.dto.QuestionResponse;
import vn.com.pps.education.lms.dto.UpdateCkeditorQuestionRequest;
import vn.com.pps.education.lms.dto.UpdateQuestionRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.lms.service.ExamQuestionService;

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

    /** Soạn "Ô trống"/"Hộp từ vựng" bằng khung CKEditor5 theo Đề — xem Javadoc CreateCkeditorWordBankExamQuestionRequest. */
    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping("/api/exams/{examId}/questions/ckeditor-word-bank")
    public ResponseEntity<QuestionResponse> createCkeditorWordBankQuestion(@PathVariable Long examId,
                                                                            @Valid @RequestBody CreateCkeditorWordBankExamQuestionRequest request,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.createCkeditorWordBankQuestion(examId, request, actor.userId()));
    }

    /** Soạn "Tự luận" (chấm AI) bằng khung CKEditor5 theo Đề — xem Javadoc CreateCkeditorEssayExamQuestionRequest. */
    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping("/api/exams/{examId}/questions/ckeditor-essay")
    public ResponseEntity<QuestionResponse> createCkeditorEssayQuestion(@PathVariable Long examId,
                                                                         @Valid @RequestBody CreateCkeditorEssayExamQuestionRequest request,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.createCkeditorEssayQuestion(examId, request, actor.userId()));
    }

    /** Soạn "Nhóm lựa chọn" bằng khung CKEditor5 theo Đề — xem Javadoc CreateCkeditorChoiceExamQuestionRequest. */
    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping("/api/exams/{examId}/questions/ckeditor-choice")
    public ResponseEntity<QuestionResponse> createCkeditorChoiceQuestion(@PathVariable Long examId,
                                                                          @Valid @RequestBody CreateCkeditorChoiceExamQuestionRequest request,
                                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.createCkeditorChoiceQuestion(examId, request, actor.userId()));
    }

    /** Soạn "Bảng lưới"/"Lựa chọn trong câu" bằng khung CKEditor5 theo Đề — tạo NHIỀU câu cùng nhóm, xem Javadoc CreateCkeditorGroupExamQuestionRequest. */
    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping("/api/exams/{examId}/questions/ckeditor-group")
    public ResponseEntity<List<QuestionResponse>> createCkeditorGroupQuestions(@PathVariable Long examId,
                                                                               @Valid @RequestBody CreateCkeditorGroupExamQuestionRequest request,
                                                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.createCkeditorGroupQuestions(examId, request, actor.userId()));
    }

    /** Bài nhiều phần (ngăn bằng đường kẻ ngang) soạn trong 1 khung CKEditor5 — mỗi phần thành câu riêng, chung 1 groupKey. */
    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping("/api/exams/{examId}/questions/ckeditor-multipart")
    public ResponseEntity<List<QuestionResponse>> createCkeditorMultipartQuestions(@PathVariable Long examId,
                                                                                   @Valid @RequestBody CreateCkeditorGroupExamQuestionRequest request,
                                                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.createCkeditorMultipartQuestions(examId, request, actor.userId()));
    }

    /** Soạn "Khối sắp xếp" bằng khung CKEditor5 theo Đề — xem Javadoc CreateCkeditorChunkExamQuestionRequest. */
    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping("/api/exams/{examId}/questions/ckeditor-chunk")
    public ResponseEntity<QuestionResponse> createCkeditorChunkQuestion(@PathVariable Long examId,
                                                                         @Valid @RequestBody CreateCkeditorChunkExamQuestionRequest request,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.createCkeditorChunkQuestion(examId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.update')")
    @PutMapping("/api/exams/{examId}/questions/{questionId}")
    public ResponseEntity<QuestionResponse> updateQuestion(@PathVariable Long examId,
                                                            @PathVariable Long questionId,
                                                            @Valid @RequestBody UpdateQuestionRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.updateQuestion(examId, questionId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.update')")
    @PutMapping("/api/exams/{examId}/questions/{questionId}/ckeditor")
    public ResponseEntity<QuestionResponse> updateCkeditorQuestion(@PathVariable Long examId,
                                                                    @PathVariable Long questionId,
                                                                    @Valid @RequestBody UpdateCkeditorQuestionRequest request,
                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.updateCkeditorQuestion(examId, questionId, request, actor.userId()));
    }

    /**
     * Key Grammar (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — danh sách mã cấu trúc
     * khả dụng cho dropdown chọn tay ở modal "Sửa câu hỏi". Rỗng nếu câu hỏi không phải ESSAY hoặc
     * Khối/chương trình chưa có từ điển.
     */
    @PreAuthorize("hasPermission(null, 'lms.exam-question.view')")
    @GetMapping("/api/exams/{examId}/questions/{questionId}/key-grammar-options")
    public ResponseEntity<List<KeyGrammarStructureResponse>> listKeyGrammarOptions(@PathVariable Long examId, @PathVariable Long questionId) {
        return ResponseEntity.ok(examQuestionService.listKeyGrammarOptions(examId, questionId));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping(value = "/api/exams/{examId}/questions/import", consumes = "multipart/form-data")
    public ResponseEntity<QuestionImportResponse> importQuestions(@PathVariable Long examId,
                                                                   @RequestParam("file") MultipartFile file,
                                                                   @RequestParam(required = false) String defaultKind,
                                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.importQuestions(examId, file, actor.userId(), defaultKind));
    }

    /**
     * Bổ sung 2026-10-19 (đã xác nhận với người dùng khi review UX import) — xem trước TRƯỚC KHI
     * ghi DB, mirror QuestionImportController#previewImport cho luồng Đề.
     */
    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @PostMapping(value = "/api/exams/{examId}/questions/import/preview", consumes = "multipart/form-data")
    public ResponseEntity<QuestionImportResponse> previewImport(@PathVariable Long examId,
                                                                   @RequestParam("file") MultipartFile file,
                                                                   @RequestParam(required = false) String defaultKind,
                                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(examQuestionService.previewImport(examId, file, actor.userId(), defaultKind));
    }

    @PreAuthorize("hasPermission(null, 'lms.exam-question.create')")
    @GetMapping("/api/exams/question-imports/template.docx")
    public ResponseEntity<byte[]> downloadWordTemplate(@RequestParam(required = false) String skillCategory,
                                                         @RequestParam(required = false) String teacherType,
                                                         @RequestParam(required = false) String defaultKind) {
        byte[] content = examQuestionService.buildWordTemplate(skillCategory, teacherType, defaultKind);
        String filename = "mau-soan-cau-hoi.docx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename)
                .body(content);
    }
}
