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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassEnrollmentResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassTeacherResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.UpdateClassRequest;
import vn.com.pps.education.dto.WithdrawEnrollmentRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ClassService;

import java.util.List;

/** UC-18: Xếp lớp & gán khóa học (FR-ACA-02). */
@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    /**
     * Dropdown FE: chọn trường (siteId) -> hiển thị lớp của trường đó; lọc
     * thêm theo chương trình (curriculumId/classCategory). Giáo viên (không
     * có quyền academic.class.manage) chỉ thấy lớp thuộc (các) site được
     * gán qua site_teachers — xem Javadoc ClassService.search.
     */
    @GetMapping
    public ResponseEntity<List<ClassResponse>> search(@RequestParam(required = false) String query,
                                                       @RequestParam(required = false) Long siteId,
                                                       @RequestParam(required = false) Long curriculumId,
                                                       @RequestParam(required = false) String classCategory,
                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.search(query, siteId, curriculumId, classCategory, actor.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(classService.getById(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping
    public ResponseEntity<ClassResponse> create(@Valid @RequestBody CreateClassRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.create(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PutMapping("/{id}")
    public ResponseEntity<ClassResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateClassRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.update(id, request, actor.userId()));
    }

    @GetMapping("/{id}/teachers")
    public ResponseEntity<List<ClassTeacherResponse>> listTeachers(@PathVariable Long id) {
        return ResponseEntity.ok(classService.listTeachers(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/{id}/teachers")
    public ResponseEntity<ClassTeacherResponse> assignTeacher(@PathVariable Long id,
                                                                  @Valid @RequestBody AssignTeacherRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.assignTeacher(id, request, actor.userId()));
    }

    @GetMapping("/{id}/enrollments")
    public ResponseEntity<List<ClassEnrollmentResponse>> listEnrollments(@PathVariable Long id) {
        return ResponseEntity.ok(classService.listEnrollments(id));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/{id}/enrollments")
    public ResponseEntity<ClassEnrollmentResponse> enroll(@PathVariable Long id,
                                                              @Valid @RequestBody EnrollStudentRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.enroll(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping("/{id}/enrollments/{enrollmentId}/withdraw")
    public ResponseEntity<ClassEnrollmentResponse> withdraw(@PathVariable Long id,
                                                                @PathVariable Long enrollmentId,
                                                                @Valid @RequestBody WithdrawEnrollmentRequest request,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classService.withdraw(id, enrollmentId, request, actor.userId()));
    }
}
