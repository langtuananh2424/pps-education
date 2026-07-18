package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AddFeedbackExchangeRequest;
import vn.com.pps.education.dto.PartnerFeedbackResponse;
import vn.com.pps.education.dto.ResolveFeedbackRequest;
import vn.com.pps.education.dto.SubmitPartnerFeedbackRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.PartnerFeedbackService;

import java.util.List;

/** UC-38: Gửi phản hồi tới Quản lý điểm trường + UC-39: Xử lý phản hồi từ trường liên kết (FR-FAC-05) — xem Javadoc PartnerFeedbackService. */
@RestController
public class PartnerFeedbackController {

    private final PartnerFeedbackService partnerFeedbackService;

    public PartnerFeedbackController(PartnerFeedbackService partnerFeedbackService) {
        this.partnerFeedbackService = partnerFeedbackService;
    }

    @PostMapping("/api/partner-feedbacks")
    public ResponseEntity<PartnerFeedbackResponse> submitFeedback(@Valid @RequestBody SubmitPartnerFeedbackRequest request,
                                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerFeedbackService.submitFeedback(request, actor.userId()));
    }

    @GetMapping("/api/partner-feedbacks/my-submitted")
    public ResponseEntity<List<PartnerFeedbackResponse>> listMySubmitted(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerFeedbackService.listMySubmittedFeedbacks(actor.userId()));
    }

    @GetMapping("/api/partner-feedbacks/my-sites")
    public ResponseEntity<List<PartnerFeedbackResponse>> listForMySites(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerFeedbackService.listForMySites(actor.userId()));
    }

    @PostMapping("/api/partner-feedbacks/{id}/start-processing")
    public ResponseEntity<PartnerFeedbackResponse> startProcessing(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerFeedbackService.startProcessing(id, actor.userId()));
    }

    @PostMapping("/api/partner-feedbacks/{id}/exchange")
    public ResponseEntity<PartnerFeedbackResponse> addExchange(@PathVariable Long id,
                                                                  @Valid @RequestBody AddFeedbackExchangeRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerFeedbackService.addExchange(id, request, actor.userId()));
    }

    @PostMapping("/api/partner-feedbacks/{id}/resolve")
    public ResponseEntity<PartnerFeedbackResponse> resolve(@PathVariable Long id,
                                                              @Valid @RequestBody ResolveFeedbackRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerFeedbackService.resolve(id, request, actor.userId()));
    }

    @PostMapping("/api/partner-feedbacks/{id}/close")
    public ResponseEntity<PartnerFeedbackResponse> close(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(partnerFeedbackService.close(id, actor.userId()));
    }
}
