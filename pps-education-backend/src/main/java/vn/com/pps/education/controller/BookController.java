package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateUnitRequest;
import vn.com.pps.education.dto.UnitResponse;
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
}
