package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.StudentBatchImportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.StudentBatchImportService;

/** UC-35: Nhập học theo lô cho lớp liên kết (FR-CRM-04) — xem Javadoc StudentBatchImportService. */
@RestController
public class StudentBatchImportController {

    private final StudentBatchImportService studentBatchImportService;

    public StudentBatchImportController(StudentBatchImportService studentBatchImportService) {
        this.studentBatchImportService = studentBatchImportService;
    }

    @PostMapping(value = "/api/student-imports", consumes = "multipart/form-data")
    public ResponseEntity<StudentBatchImportResponse> importStudents(@RequestParam("file") MultipartFile file,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentBatchImportService.importStudents(file, actor.userId()));
    }

    @GetMapping("/api/student-imports/{id}")
    public ResponseEntity<StudentBatchImportResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(studentBatchImportService.getJob(id));
    }
}
