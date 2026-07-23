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
import vn.com.pps.education.dto.CommendationResponse;
import vn.com.pps.education.dto.CreateCommendationRequest;
import vn.com.pps.education.dto.CreateEmployeeRequest;
import vn.com.pps.education.dto.CreateEmploymentContractRequest;
import vn.com.pps.education.dto.CreateQualificationRequest;
import vn.com.pps.education.dto.EmployeeResponse;
import vn.com.pps.education.dto.EmploymentContractResponse;
import vn.com.pps.education.dto.ExpiringContractResponse;
import vn.com.pps.education.dto.QualificationResponse;
import vn.com.pps.education.dto.UpdateEmployeeRequest;
import vn.com.pps.education.dto.UpdateEmploymentContractRequest;
import vn.com.pps.education.dto.UpdateOwnEmployeeProfileRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.EmployeeService;

import java.util.List;

/** UC-08: Quản lý hồ sơ nhân sự (FR-HRM-01). */
@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasPermission(null, 'hrm.manage')")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> search(@RequestParam(required = false) String query,
                                                           @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(employeeService.search(query, departmentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getById(id));
    }

    /**
     * UC-63: nhân viên tự xem hồ sơ của chính mình — override permission
     * hrm.manage ở class-level, chỉ cần đăng nhập (Service tự giới hạn
     * đúng hồ sơ của actor qua user_id, không nhận id tùy ý).
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<EmployeeResponse> getMine(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(employeeService.getMyProfile(actor.userId()));
    }

    /** UC-63: nhân viên tự cập nhật ảnh đại diện + địa chỉ liên hệ của chính mình (FR-USR-07). */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me")
    public ResponseEntity<EmployeeResponse> updateMine(@AuthenticationPrincipal AuthenticatedUser actor,
                                                         @Valid @RequestBody UpdateOwnEmployeeProfileRequest request) {
        return ResponseEntity.ok(employeeService.updateMyProfile(actor.userId(), request));
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(employeeService.create(request, actor.userId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateEmployeeRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(employeeService.update(id, request, actor.userId()));
    }

    @GetMapping("/{id}/qualifications")
    public ResponseEntity<List<QualificationResponse>> listQualifications(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.listQualifications(id));
    }

    @PostMapping("/{id}/qualifications")
    public ResponseEntity<QualificationResponse> addQualification(@PathVariable Long id,
                                                                     @Valid @RequestBody CreateQualificationRequest request) {
        return ResponseEntity.ok(employeeService.addQualification(id, request));
    }

    @GetMapping("/{id}/commendations")
    public ResponseEntity<List<CommendationResponse>> listCommendations(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.listCommendations(id));
    }

    @PostMapping("/{id}/commendations")
    public ResponseEntity<CommendationResponse> addCommendation(@PathVariable Long id,
                                                                   @Valid @RequestBody CreateCommendationRequest request,
                                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(employeeService.addCommendation(id, request, actor.userId()));
    }

    @GetMapping("/{id}/contracts")
    public ResponseEntity<List<EmploymentContractResponse>> listContracts(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.listContracts(id));
    }

    @PostMapping("/{id}/contracts")
    public ResponseEntity<EmploymentContractResponse> addContract(@PathVariable Long id,
                                                                     @Valid @RequestBody CreateEmploymentContractRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(employeeService.addContract(id, request, actor.userId()));
    }

    @PutMapping("/{id}/contracts/{contractId}")
    public ResponseEntity<EmploymentContractResponse> updateContract(@PathVariable Long id,
                                                                        @PathVariable Long contractId,
                                                                        @Valid @RequestBody UpdateEmploymentContractRequest request,
                                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(employeeService.updateContract(id, contractId, request, actor.userId()));
    }

    /**
     * A2 — hợp đồng ACTIVE sắp/đã hết hạn trong vòng withinDays ngày kể từ hôm nay.
     * withinDays bắt buộc do người gọi chỉ định — UC không quy định 1 ngưỡng cố định,
     * không tự đặt mặc định (xem .claude/rules/business-fidelity.md).
     */
    @GetMapping("/contracts/expiring")
    public ResponseEntity<List<ExpiringContractResponse>> listExpiringContracts(@RequestParam int withinDays) {
        return ResponseEntity.ok(employeeService.listExpiringContracts(withinDays));
    }
}
