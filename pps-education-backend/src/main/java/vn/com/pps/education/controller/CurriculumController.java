package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateCurriculumSubjectRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.CurriculumSubjectResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.CurriculumService;

import java.util.List;

/** UC-16: Quản lý khung chương trình (FR-ACA-01, chỉ khung chuẩn — xem Javadoc CurriculumService). */
@RestController
@RequestMapping("/api/curriculums")
public class CurriculumController {

    private final CurriculumService curriculumService;

    public CurriculumController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @GetMapping
    public ResponseEntity<List<CurriculumResponse>> listStandard() {
        return ResponseEntity.ok(curriculumService.listStandard());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CurriculumResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(curriculumService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CurriculumResponse> create(@Valid @RequestBody CreateCurriculumRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(curriculumService.create(request, actor.userId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CurriculumResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateCurriculumRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(curriculumService.update(id, request, actor.userId()));
    }

    @GetMapping("/{id}/subjects")
    public ResponseEntity<List<CurriculumSubjectResponse>> listSubjects(@PathVariable Long id) {
        return ResponseEntity.ok(curriculumService.listSubjects(id));
    }

    @PostMapping("/{id}/subjects")
    public ResponseEntity<CurriculumSubjectResponse> addSubject(@PathVariable Long id,
                                                                    @Valid @RequestBody CreateCurriculumSubjectRequest request,
                                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(curriculumService.addSubject(id, request, actor.userId()));
    }
}
