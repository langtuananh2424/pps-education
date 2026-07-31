package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateStudentRequest;
import vn.com.pps.education.dto.LinkParentRequest;
import vn.com.pps.education.dto.ParentStudentResponse;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.StudentStatusHistoryResponse;
import vn.com.pps.education.dto.StudentTransferHistoryResponse;
import vn.com.pps.education.dto.UpdateStudentRequest;
import vn.com.pps.education.dto.UpdateStudentStatusRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.StudentService;
import vn.com.pps.education.service.StudentStatusService;

import java.util.List;

/**
 * UC-13: Quản lý hồ sơ học sinh (FR-STU-01) + UC-14: Cập nhật trạng thái
 * học tập (FR-STU-02). Cùng resource /api/students nhưng khác permission —
 * UC-14 dùng student.status.manage riêng (Hybrid PBAC — V28), không dùng
 * nhóm student.profile.* (V44).
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;
    private final StudentStatusService studentStatusService;

    public StudentController(StudentService studentService, StudentStatusService studentStatusService) {
        this.studentService = studentService;
        this.studentStatusService = studentStatusService;
    }

    @PreAuthorize("hasPermission(null, 'student.profile.view')")
    @GetMapping
    public ResponseEntity<List<StudentResponse>> search(@RequestParam(required = false) String query,
                                                          @RequestParam(required = false) Long siteId,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.search(query, siteId, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'student.profile.view')")
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.getById(id, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'student.profile.create')")
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.create(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'student.profile.update')")
    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateStudentRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.update(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'student.parent.view')")
    @GetMapping("/{id}/parents")
    public ResponseEntity<List<ParentStudentResponse>> listParents(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.listParents(id));
    }

    @PreAuthorize("hasPermission(null, 'student.parent.link.create')")
    @PostMapping("/{id}/parents")
    public ResponseEntity<ParentStudentResponse> linkParent(@PathVariable Long id,
                                                               @Valid @RequestBody LinkParentRequest request) {
        return ResponseEntity.ok(studentService.linkParent(id, request));
    }

    @PreAuthorize("hasPermission(null, 'student.parent.link.delete')")
    @DeleteMapping("/{id}/parents/{parentStudentId}")
    public ResponseEntity<Void> unlinkParent(@PathVariable Long id, @PathVariable Long parentStudentId) {
        studentService.unlinkParent(id, parentStudentId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasPermission(null, 'student.profile.view')")
    @GetMapping("/{id}/transfers")
    public ResponseEntity<List<StudentTransferHistoryResponse>> listTransferHistory(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.listTransferHistory(id));
    }

    @PreAuthorize("hasPermission(null, 'student.profile.update')")
    @PostMapping("/{id}/transfers")
    public ResponseEntity<StudentTransferHistoryResponse> recordTransfer(@PathVariable Long id,
                                                                            @Valid @RequestBody RecordTransferRequest request,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.recordTransfer(id, request, actor.userId()));
    }

    /** UC-14 — dùng student.status.manage riêng, xem Javadoc lớp. */
    @GetMapping("/{id}/status-history")
    public ResponseEntity<List<StudentStatusHistoryResponse>> listStatusHistory(@PathVariable Long id) {
        return ResponseEntity.ok(studentStatusService.listStatusHistory(id));
    }

    /** UC-14 — dùng student.status.manage riêng, xem Javadoc lớp. */
    @PreAuthorize("hasPermission(null, 'student.status.manage')")
    @PostMapping("/{id}/status")
    public ResponseEntity<StudentStatusHistoryResponse> updateStatus(@PathVariable Long id,
                                                                        @Valid @RequestBody UpdateStudentStatusRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentStatusService.updateStatus(id, request, actor.userId()));
    }
}
