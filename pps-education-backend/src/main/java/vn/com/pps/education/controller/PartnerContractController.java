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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreatePartnerContractRequest;
import vn.com.pps.education.dto.ExpiringPartnerContractResponse;
import vn.com.pps.education.dto.PartnerContractResponse;
import vn.com.pps.education.dto.UpdatePartnerContractRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.PartnerContractService;

import java.util.List;

/** UC-36b: Quản lý hợp đồng liên kết trường (FR-FAC-01) — xem Javadoc PartnerContractService. */
@RestController
public class PartnerContractController {

    private final PartnerContractService partnerContractService;

    public PartnerContractController(PartnerContractService partnerContractService) {
        this.partnerContractService = partnerContractService;
    }

    @PostMapping("/api/partner-contracts")
    @PreAuthorize("hasPermission(null, 'facility.partner-contract.create') or hasPermission(null, 'facility.manage')")
    public ResponseEntity<PartnerContractResponse> createContract(@Valid @RequestBody CreatePartnerContractRequest request,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerContractService.createContract(request, actor.userId()));
    }

    @PutMapping("/api/partner-contracts/{id}")
    @PreAuthorize("hasPermission(null, 'facility.partner-contract.update') or hasPermission(null, 'facility.manage')")
    public ResponseEntity<PartnerContractResponse> updateContract(@PathVariable Long id,
                                                                      @Valid @RequestBody UpdatePartnerContractRequest request,
                                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerContractService.updateContract(id, request, actor.userId()));
    }

    @PostMapping("/api/partner-contracts/{id}/terminate")
    @PreAuthorize("hasPermission(null, 'facility.partner-contract.update') or hasPermission(null, 'facility.manage')")
    public ResponseEntity<PartnerContractResponse> terminateContract(@PathVariable Long id,
                                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerContractService.terminateContract(id, actor.userId()));
    }

    @GetMapping("/api/partner-contracts/expiring")
    @PreAuthorize("hasPermission(null, 'facility.manage')")
    public ResponseEntity<List<ExpiringPartnerContractResponse>> listExpiringContracts(@RequestParam int withinDays) {
        return ResponseEntity.ok(partnerContractService.listExpiringContracts(withinDays));
    }

    @GetMapping("/api/sites/{siteId}/partner-contracts")
    @PreAuthorize("hasPermission(null, 'facility.manage')")
    public ResponseEntity<List<PartnerContractResponse>> listBySite(@PathVariable Long siteId) {
        return ResponseEntity.ok(partnerContractService.listBySite(siteId));
    }

    @DeleteMapping("/api/partner-contracts/{id}")
    @PreAuthorize("hasPermission(null, 'facility.partner-contract.delete') or hasPermission(null, 'facility.manage')")
    public ResponseEntity<Void> deleteContract(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        partnerContractService.deleteContract(id, actor.userId());
        return ResponseEntity.noContent().build();
    }
}
