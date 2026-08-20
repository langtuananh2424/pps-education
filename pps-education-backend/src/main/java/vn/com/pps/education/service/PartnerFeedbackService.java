package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.PartnerFeedback;
import vn.com.pps.education.domain.PartnerFeedbackHistory;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AddFeedbackExchangeRequest;
import vn.com.pps.education.dto.PartnerFeedbackResponse;
import vn.com.pps.education.dto.ResolveFeedbackRequest;
import vn.com.pps.education.dto.SubmitPartnerFeedbackRequest;
import vn.com.pps.education.exception.InvalidFeedbackStatusTransitionException;
import vn.com.pps.education.exception.NotAuthorizedForFeedbackException;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.PartnerFeedbackHistoryRepository;
import vn.com.pps.education.repository.PartnerFeedbackRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-38: Gửi phản hồi tới Quản lý điểm trường (FR-FAC-05) + UC-39: Xử lý
 * phản hồi từ trường liên kết (FR-FAC-05). Xem
 * docs/uc/phan-he-10-co-so-vat-chat.md. Cùng 1 Service vì cùng 1 vòng đời
 * dữ liệu (partner_feedbacks) — gửi (UC-38) và xử lý (UC-39) là 2 nửa của
 * 1 quy trình trạng thái Mới → Đang xử lý → Đã giải quyết → Đóng.
 */
@Service
public class PartnerFeedbackService {

    private final PartnerFeedbackRepository partnerFeedbackRepository;
    private final PartnerFeedbackHistoryRepository partnerFeedbackHistoryRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public PartnerFeedbackService(PartnerFeedbackRepository partnerFeedbackRepository,
                                   PartnerFeedbackHistoryRepository partnerFeedbackHistoryRepository,
                                   SiteManagerRepository siteManagerRepository,
                                   UserRepository userRepository,
                                   NotificationService notificationService) {
        this.partnerFeedbackRepository = partnerFeedbackRepository;
        this.partnerFeedbackHistoryRepository = partnerFeedbackHistoryRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** UC-38 Main Flow bước 1-4: Đại diện trường liên kết gửi phản hồi, tự động gán Quản lý điểm trường phụ trách. */
    @Transactional
    public PartnerFeedbackResponse submitFeedback(SubmitPartnerFeedbackRequest request, Long actorUserId) {
        Site site = requirePartnerSite(actorUserId);
        User actor = getUserOrThrow(actorUserId);
        SiteManager currentManager = siteManagerRepository
                .findBySiteIdAndRoleTypeAndAssignedToIsNull(site.getId(), SiteManager.RoleType.SITE_MANAGER)
                .stream().findFirst().orElse(null);

        PartnerFeedback feedback = new PartnerFeedback();
        feedback.setSite(site);
        feedback.setSubmittedBy(actor);
        feedback.setContent(request.content());
        feedback.setFeedbackType(PartnerFeedback.FeedbackType.valueOf(request.feedbackType()));
        if (request.priority() != null) {
            feedback.setPriority(PartnerFeedback.Priority.valueOf(request.priority()));
        }
        feedback.setStatus(PartnerFeedback.Status.NEW);
        if (currentManager != null) {
            feedback.setAssignedTo(currentManager.getUser());
        }
        feedback = partnerFeedbackRepository.save(feedback);

        writeHistory(feedback, actor, PartnerFeedbackHistory.Action.CREATED, Map.of("status", "NEW"));
        if (currentManager != null) {
            notificationService.notify(currentManager.getUser().getId(), Notification.NotificationType.PARTNER_FEEDBACK,
                    "Phản hồi mới từ trường liên kết",
                    "Điểm trường " + site.getName() + " vừa gửi 1 phản hồi (" + feedback.getFeedbackType() + ", ưu tiên "
                            + feedback.getPriority() + ").");
        }
        return toResponse(feedback);
    }

    /** UC-38 A1: Đại diện trường liên kết xem lại lịch sử phản hồi đã gửi và trạng thái xử lý. */
    @Transactional(readOnly = true)
    public List<PartnerFeedbackResponse> listMySubmittedFeedbacks(Long actorUserId) {
        return partnerFeedbackRepository.findBySubmittedByIdOrderByCreatedAtDesc(actorUserId).stream()
                .map(this::toResponse).toList();
    }

    /** UC-39 Main Flow bước 1: hàng đợi phản hồi của (các) điểm trường Quản lý điểm trường phụ trách, ưu tiên theo mức độ ưu tiên thật (không phải alphabet). */
    @Transactional(readOnly = true)
    public List<PartnerFeedbackResponse> listForMySites(Long actorUserId) {
        List<Long> siteIds = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                .map(sm -> sm.getSite().getId()).toList();
        return siteIds.stream()
                .flatMap(siteId -> partnerFeedbackRepository.findBySiteId(siteId).stream())
                .sorted(Comparator.comparing((PartnerFeedback f) -> f.getPriority().ordinal()).reversed()
                        .thenComparing(Comparator.comparing(PartnerFeedback::getCreatedAt).reversed()))
                .map(this::toResponse)
                .toList();
    }

    /** UC-39 Main Flow bước 2: Mới → Đang xử lý. */
    @Transactional
    public PartnerFeedbackResponse startProcessing(Long feedbackId, Long actorUserId) {
        PartnerFeedback feedback = getFeedbackOrThrow(feedbackId);
        requireSiteManagerForFeedback(feedback, actorUserId);
        if (feedback.getStatus() != PartnerFeedback.Status.NEW) {
            throw new InvalidFeedbackStatusTransitionException(
                    "Phản hồi này phải ở trạng thái Mới để chuyển Đang xử lý (hiện tại: " + feedback.getStatus() + ").");
        }
        feedback.setStatus(PartnerFeedback.Status.IN_PROGRESS);
        feedback = partnerFeedbackRepository.save(feedback);
        writeHistory(feedback, getUserOrThrow(actorUserId), PartnerFeedbackHistory.Action.UPDATED, Map.of("status", "IN_PROGRESS"));
        return toResponse(feedback);
    }

    /**
     * UC-39 A1: 1 lượt trao đổi qua lại — cả Quản lý điểm trường (phụ
     * trách site đó) và Đại diện trường liên kết (người gửi) đều gọi được,
     * miễn phản hồi chưa Đóng.
     */
    @Transactional
    public PartnerFeedbackResponse addExchange(Long feedbackId, AddFeedbackExchangeRequest request, Long actorUserId) {
        PartnerFeedback feedback = getFeedbackOrThrow(feedbackId);
        requireParticipant(feedback, actorUserId);
        if (feedback.getStatus() == PartnerFeedback.Status.CLOSED) {
            throw new InvalidFeedbackStatusTransitionException("Phản hồi này đã Đóng, không thể trao đổi thêm.");
        }
        writeHistory(feedback, getUserOrThrow(actorUserId), PartnerFeedbackHistory.Action.EXCHANGE, Map.of("note", request.note()));
        return toResponse(feedback);
    }

    /** UC-39 Main Flow bước 3-4: ghi nội dung giải quyết, chuyển Đã giải quyết, thông báo Đại diện trường liên kết. */
    @Transactional
    public PartnerFeedbackResponse resolve(Long feedbackId, ResolveFeedbackRequest request, Long actorUserId) {
        PartnerFeedback feedback = getFeedbackOrThrow(feedbackId);
        requireSiteManagerForFeedback(feedback, actorUserId);
        if (feedback.getStatus() != PartnerFeedback.Status.IN_PROGRESS) {
            throw new InvalidFeedbackStatusTransitionException(
                    "Phản hồi này phải ở trạng thái Đang xử lý để giải quyết (hiện tại: " + feedback.getStatus() + ").");
        }
        feedback.setResolutionNotes(request.resolutionNotes());
        feedback.setStatus(PartnerFeedback.Status.RESOLVED);
        feedback.setResolvedAt(OffsetDateTime.now());
        feedback = partnerFeedbackRepository.save(feedback);
        writeHistory(feedback, getUserOrThrow(actorUserId), PartnerFeedbackHistory.Action.UPDATED, Map.of("status", "RESOLVED"));

        notificationService.notify(feedback.getSubmittedBy().getId(), Notification.NotificationType.PARTNER_FEEDBACK,
                "Phản hồi của bạn đã được giải quyết",
                "Phản hồi gửi ngày " + feedback.getCreatedAt().toLocalDate() + " đã được xử lý: " + request.resolutionNotes());
        return toResponse(feedback);
    }

    /** UC-39 Main Flow bước 5: Đã giải quyết → Đóng. */
    @Transactional
    public PartnerFeedbackResponse close(Long feedbackId, Long actorUserId) {
        PartnerFeedback feedback = getFeedbackOrThrow(feedbackId);
        requireSiteManagerForFeedback(feedback, actorUserId);
        if (feedback.getStatus() != PartnerFeedback.Status.RESOLVED) {
            throw new InvalidFeedbackStatusTransitionException(
                    "Phản hồi này phải ở trạng thái Đã giải quyết để Đóng (hiện tại: " + feedback.getStatus() + ").");
        }
        feedback.setStatus(PartnerFeedback.Status.CLOSED);
        feedback = partnerFeedbackRepository.save(feedback);
        writeHistory(feedback, getUserOrThrow(actorUserId), PartnerFeedbackHistory.Action.UPDATED, Map.of("status", "CLOSED"));
        return toResponse(feedback);
    }

    // ===================== Helpers =====================

    private Site requirePartnerSite(Long actorUserId) {
        return siteManagerRepository.findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.PARTNER_REP)
                .stream()
                .map(SiteManager::getSite)
                .filter(site -> site.getSiteType() == Site.SiteType.PARTNER)
                .findFirst()
                .orElseThrow(() -> new NotAuthorizedForPortalAccessException(
                        "Tài khoản của bạn chưa được gán vào điểm trường liên kết (loại PARTNER) nào."));
    }

    private void requireSiteManagerForFeedback(PartnerFeedback feedback, Long actorUserId) {
        boolean isManager = siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                feedback.getSite().getId(), actorUserId, SiteManager.RoleType.SITE_MANAGER);
        if (!isManager) {
            throw new NotAuthorizedForFeedbackException(
                    "Bạn không phụ trách điểm trường liên quan tới phản hồi này.");
        }
    }

    private void requireParticipant(PartnerFeedback feedback, Long actorUserId) {
        boolean isSubmitter = feedback.getSubmittedBy().getId().equals(actorUserId);
        boolean isManager = siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                feedback.getSite().getId(), actorUserId, SiteManager.RoleType.SITE_MANAGER);
        if (!isSubmitter && !isManager) {
            throw new NotAuthorizedForFeedbackException(
                    "Bạn không liên quan tới phản hồi này.");
        }
    }

    private void writeHistory(PartnerFeedback feedback, User actor, PartnerFeedbackHistory.Action action, Map<String, Object> extra) {
        PartnerFeedbackHistory history = new PartnerFeedbackHistory();
        history.setFeedback(feedback);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> details = new HashMap<>(extra);
        history.setDetails(details);
        partnerFeedbackHistoryRepository.save(history);
    }

    private PartnerFeedback getFeedbackOrThrow(Long id) {
        return partnerFeedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.partnerFeedback.notFoundById", new Object[]{id}, "Không tìm thấy phản hồi id=" + id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.partnerFeedback.accountNotFound", new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }

    private PartnerFeedbackResponse toResponse(PartnerFeedback f) {
        return new PartnerFeedbackResponse(f.getId(), f.getSite().getId(), f.getSubmittedBy().getId(), f.getContent(),
                f.getFeedbackType().name(), f.getPriority().name(), f.getStatus().name(),
                f.getAssignedTo() == null ? null : f.getAssignedTo().getId(), f.getResolutionNotes(), f.getResolvedAt(),
                f.getCreatedAt());
    }
}
