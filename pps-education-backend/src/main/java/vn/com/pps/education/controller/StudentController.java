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
import vn.com.pps.education.dto.CreateParentRequest;
import vn.com.pps.education.dto.CreateStudentRequest;
import vn.com.pps.education.dto.LinkParentRequest;
import vn.com.pps.education.dto.ParentResponse;
import vn.com.pps.education.dto.ParentStudentResponse;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.StudentStatusHistoryResponse;
import vn.com.pps.education.dto.StudentTransferHistoryResponse;
import vn.com.pps.education.dto.UpdateParentRequest;
import vn.com.pps.education.dto.UpdateStudentRequest;
import vn.com.pps.education.dto.UpdateStudentStatusRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.StudentService;
import vn.com.pps.education.service.StudentStatusService;

import java.util.List;

/**
 * UC-13: Quản lý hồ sơ học sinh (FR-STU-01) + UC-14: Cập nhật trạng thái
 * học tập (FR-STU-02). Cùng resource /api/students nhưng khác quyền — xem
 * @PreAuthorize riêng ở endpoint status (UC-14 không yêu cầu student.manage,
 * chỉ role SITE_MANAGER/STAFF, kiểm tra trong StudentStatusService).
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

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @GetMapping
    public ResponseEntity<List<StudentResponse>> search(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(studentService.search(query));
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getById(id));
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.create(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateStudentRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.update(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @PostMapping("/parents")
    public ResponseEntity<ParentResponse> createParent(@Valid @RequestBody CreateParentRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.createParent(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @PutMapping("/parents/{parentId}")
    public ResponseEntity<ParentResponse> updateParent(@PathVariable Long parentId,
                                                          @Valid @RequestBody UpdateParentRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.updateParent(parentId, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @GetMapping("/{id}/parents")
    public ResponseEntity<List<ParentStudentResponse>> listParents(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.listParents(id));
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @PostMapping("/{id}/parents")
    public ResponseEntity<ParentStudentResponse> linkParent(@PathVariable Long id,
                                                               @Valid @RequestBody LinkParentRequest request) {
        return ResponseEntity.ok(studentService.linkParent(id, request));
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @DeleteMapping("/{id}/parents/{parentStudentId}")
    public ResponseEntity<Void> unlinkParent(@PathVariable Long id, @PathVariable Long parentStudentId) {
        studentService.unlinkParent(id, parentStudentId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @GetMapping("/{id}/transfers")
    public ResponseEntity<List<StudentTransferHistoryResponse>> listTransferHistory(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.listTransferHistory(id));
    }

    @PreAuthorize("hasPermission(null, 'student.manage')")
    @PostMapping("/{id}/transfers")
    public ResponseEntity<StudentTransferHistoryResponse> recordTransfer(@PathVariable Long id,
                                                                            @Valid @RequestBody RecordTransferRequest request,
                                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.recordTransfer(id, request, actor.userId()));
    }

    /** UC-14 — không dùng hasPermission('student.manage'), xem Javadoc lớp. */
    @GetMapping("/{id}/status-history")
    public ResponseEntity<List<StudentStatusHistoryResponse>> listStatusHistory(@PathVariable Long id) {
        return ResponseEntity.ok(studentStatusService.listStatusHistory(id));
    }

    /** UC-14 — không dùng hasPermission('student.manage'), xem Javadoc lớp. */
    @PostMapping("/{id}/status")
    public ResponseEntity<StudentStatusHistoryResponse> updateStatus(@PathVariable Long id,
                                                                        @Valid @RequestBody UpdateStudentStatusRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentStatusService.updateStatus(id, request, actor.userId()));
    }
}
