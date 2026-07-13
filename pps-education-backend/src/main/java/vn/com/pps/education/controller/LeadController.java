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
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AssignLeadRequest;
import vn.com.pps.education.dto.CreateLeadRequest;
import vn.com.pps.education.dto.LeadResponse;
import vn.com.pps.education.dto.UpdateLeadStatusRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.LeadService;

import java.util.List;

/** UC-33: Quản lý lead & tư vấn tuyển sinh + UC-34: Chuyển đổi lead thành học sinh — xem Javadoc LeadService. */
@RestController
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PreAuthorize("hasPermission(null, 'crm.lead.manage')")
    @PostMapping("/api/leads")
    public ResponseEntity<LeadResponse> createLead(@Valid @RequestBody CreateLeadRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leadService.createLead(request, actor.userId()));
    }

    @GetMapping("/api/leads/{id}")
    public ResponseEntity<LeadResponse> getLead(@PathVariable Long id) {
        return ResponseEntity.ok(leadService.getLead(id));
    }

    @GetMapping("/api/leads/my-leads")
    public ResponseEntity<List<LeadResponse>> listMyLeads(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leadService.listMyLeads(actor.userId()));
    }

    @GetMapping("/api/leads/open")
    public ResponseEntity<List<LeadResponse>> listOpenLeads() {
        return ResponseEntity.ok(leadService.listOpenLeads());
    }

    @PreAuthorize("hasPermission(null, 'crm.lead.assign')")
    @PutMapping("/api/leads/{id}/assign")
    public ResponseEntity<LeadResponse> assignLead(@PathVariable Long id, @Valid @RequestBody AssignLeadRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leadService.assignLead(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'crm.lead.manage')")
    @PutMapping("/api/leads/{id}/status")
    public ResponseEntity<LeadResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateLeadStatusRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leadService.updateStatus(id, request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'crm.lead.manage')")
    @PostMapping("/api/leads/{id}/convert")
    public ResponseEntity<LeadResponse> convertToStudent(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(leadService.convertToStudent(id, actor.userId()));
    }
}
