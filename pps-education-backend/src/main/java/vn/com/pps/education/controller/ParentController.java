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
import vn.com.pps.education.dto.CreateParentRequest;
import vn.com.pps.education.dto.ParentResponse;
import vn.com.pps.education.dto.UpdateParentRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.StudentService;

import java.util.List;

/**
 * UC-13: Quản lý hồ sơ phụ huynh (FR-STU-01 Main Flow bước 2). Business
 * logic nằm ở StudentService (bundle cùng hồ sơ học sinh — xem Javadoc
 * StudentService); Controller này tách riêng resource REST /api/parents
 * khỏi /api/students theo đúng ranh giới 1 Controller = 1 resource
 * (.claude/rules/solid.md — SRP).
 */
@RestController
@RequestMapping("/api/parents")
public class ParentController {

    private final StudentService studentService;

    public ParentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PreAuthorize("hasPermission(null, 'student.parent.view')")
    @GetMapping
    public ResponseEntity<List<ParentResponse>> search(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(studentService.searchParents(query));
    }

    @PreAuthorize("hasPermission(null, 'student.parent.view')")
    @GetMapping("/{id}")
    public ResponseEntity<ParentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getParentById(id));
    }

    @PreAuthorize("hasPermission(null, 'student.parent.manage')")
    @PostMapping
    public ResponseEntity<ParentResponse> create(@Valid @RequestBody CreateParentRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.createParent(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'student.parent.manage')")
    @PutMapping("/{id}")
    public ResponseEntity<ParentResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateParentRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.updateParent(id, request, actor.userId()));
    }
}
