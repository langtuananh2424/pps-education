package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.QuestionImportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.QuestionImportService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * UC-40: Soạn & giao đề kiểm tra (FR-LMS-10) — soạn đề nhanh qua file mẫu
 * Excel/Word (bổ sung ngoài SDD gốc, đã xác nhận với người dùng), xem
 * Javadoc QuestionImportService. Quyền dùng chung với QuestionBankController
 * (cùng resource "câu hỏi trong ngân hàng", không phải quyền mới).
 */
@RestController
public class QuestionImportController {

    private final QuestionImportService questionImportService;

    public QuestionImportController(QuestionImportService questionImportService) {
        this.questionImportService = questionImportService;
    }

    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.create')")
    @PostMapping(value = "/api/question-banks/{bankId}/questions/import", consumes = "multipart/form-data")
    public ResponseEntity<QuestionImportResponse> importQuestions(@PathVariable Long bankId,
                                                                    @RequestParam("file") MultipartFile file,
                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(questionImportService.importQuestions(bankId, file, actor.userId()));
    }

    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.create')")
    @GetMapping("/api/question-imports/{id}")
    public ResponseEntity<QuestionImportResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(questionImportService.getJob(id));
    }

    /** File mẫu Word (.docx) tĩnh — không cá nhân hoá theo bank, xem Javadoc buildWordTemplate(). */
    @PreAuthorize("hasAnyRole('HEAD_ACADEMIC','SYS_ADMIN','SUPER_ADMIN') and hasPermission(null, 'lms.question-bank.create')")
    @GetMapping("/api/question-imports/template.docx")
    public ResponseEntity<byte[]> downloadWordTemplate() {
        byte[] content = questionImportService.buildWordTemplate();
        String filename = "mau-soan-cau-hoi.docx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename)
                .body(content);
    }
}
