package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.BookResponse;
import vn.com.pps.education.dto.CreateUnitRequest;
import vn.com.pps.education.dto.UnitResponse;
import vn.com.pps.education.dto.UpdateBookRequest;
import vn.com.pps.education.service.CurriculumService;

import java.util.List;

/** V148 (Kho đề — Curriculum (chương trình+khối) -&gt; Sách -&gt; Unit -&gt; Sub Topic -&gt; Lesson -&gt; Bài): Unit con của 1 Sách. */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final CurriculumService curriculumService;

    public BookController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @GetMapping("/{bookId}/units")
    public ResponseEntity<List<UnitResponse>> listUnits(@PathVariable Long bookId) {
        return ResponseEntity.ok(curriculumService.listUnits(bookId));
    }

    // Mirror CurriculumController#addBook — dùng chung lms.exercise.create (xem Javadoc ở đó).
    @PreAuthorize("hasPermission(null, 'lms.exercise.create')")
    @PostMapping("/{bookId}/units")
    public ResponseEntity<UnitResponse> addUnit(@PathVariable Long bookId,
                                                    @Valid @RequestBody CreateUnitRequest request) {
        return ResponseEntity.ok(curriculumService.addUnit(bookId, request));
    }

    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — sửa/xóa 1 Sách, dùng chung quyền
    // lms.exercise.update (cùng nhóm quyền Kho đề như addUnit/addBook ở trên).
    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @PutMapping("/{id}")
    public ResponseEntity<BookResponse> updateBook(@PathVariable Long id, @Valid @RequestBody UpdateBookRequest request) {
        return ResponseEntity.ok(curriculumService.updateBook(id, request));
    }

    /** Chỉ xóa được khi Sách đã hết Unit — xem Javadoc CurriculumService#deleteBook. */
    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        curriculumService.deleteBook(id);
        return ResponseEntity.ok().build();
    }
}
