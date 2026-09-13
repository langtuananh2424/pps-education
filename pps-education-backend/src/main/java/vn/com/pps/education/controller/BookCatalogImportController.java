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
import vn.com.pps.education.dto.BookCatalogImportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.BookCatalogImportService;

import java.math.BigDecimal;

/**
 * UC-72: Import Excel nhanh mục lục Sách/Unit/Sub Topic/Lesson/Bài (Kho
 * đề, FR-LMS-10). Xem docs/uc/phan-he-07-lms-portal.md (UC-72). Đòi hỏi
 * đủ 2 quyền tương ứng thao tác tay ở Kho đề (Sách/Unit/Sub Topic/Bài
 * dùng lms.exercise.create, Đề dùng lms.exam.create — xem
 * CurriculumController#addBook/ExamController#createExam).
 */
@RestController
@PreAuthorize("hasPermission(null, 'lms.exercise.create') and hasPermission(null, 'lms.exam.create')")
public class BookCatalogImportController {

    private final BookCatalogImportService bookCatalogImportService;

    public BookCatalogImportController(BookCatalogImportService bookCatalogImportService) {
        this.bookCatalogImportService = bookCatalogImportService;
    }

    @PostMapping(value = "/api/curriculums/{curriculumId}/book-catalog-imports", consumes = "multipart/form-data")
    public ResponseEntity<BookCatalogImportResponse> importCatalog(@PathVariable Long curriculumId,
                                                                     @RequestParam("file") MultipartFile file,
                                                                     @RequestParam("teacherType") String teacherType,
                                                                     @RequestParam("examType") String examType,
                                                                     @RequestParam("exerciseType") String exerciseType,
                                                                     @RequestParam("totalPoints") BigDecimal totalPoints,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(bookCatalogImportService.importCatalog(
                curriculumId, file, teacherType, examType, exerciseType, totalPoints, actor.userId()));
    }

    @GetMapping("/api/book-catalog-imports/{id}")
    public ResponseEntity<BookCatalogImportResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(bookCatalogImportService.getJob(id));
    }
}
