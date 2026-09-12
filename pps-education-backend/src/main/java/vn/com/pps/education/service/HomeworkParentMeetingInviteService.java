package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.HomeworkParentMeetingInvite;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.HomeworkParentMeetingInviteResponse;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.HomeworkParentMeetingInviteRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-12): "Thư
 * mời phụ huynh tới làm việc" (HOMEWORK_MISS_PARENT_MEETING_INVITE, học
 * sinh thiếu bài liên tục 4 buổi) phải được Quản lý điểm trường DUYỆT
 * trước khi gửi xuống Phụ huynh — 3 loại cảnh báo còn lại (2/3 buổi, không
 * liên tục) vẫn gửi thẳng, không qua service này.
 *
 * KHÔNG dùng chung {@code ApprovalFlow} (xem Javadoc
 * {@link HomeworkParentMeetingInvite}) — mirror đúng pattern kiểm tra
 * quyền/luồng duyệt của {@code StudentCommentService} (UC-22) nhưng tự
 * quản lý status của entity riêng.
 */
@Service
public class HomeworkParentMeetingInviteService {

    private final HomeworkParentMeetingInviteRepository inviteRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public HomeworkParentMeetingInviteService(HomeworkParentMeetingInviteRepository inviteRepository,
                                               SiteManagerRepository siteManagerRepository,
                                               ParentStudentRepository parentStudentRepository,
                                               UserRepository userRepository,
                                               NotificationService notificationService) {
        this.inviteRepository = inviteRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** Gọi từ HomeworkAlertTrackingService khi streak chạm mốc 4 — tạo bản ghi chờ duyệt, báo Quản lý điểm trường. */
    @Transactional
    public void submitForApproval(Student student, SchoolClass schoolClass, String channelLabel, int missCount) {
        HomeworkParentMeetingInvite invite = new HomeworkParentMeetingInvite();
        invite.setStudent(student);
        invite.setSchoolClass(schoolClass);
        invite.setChannelLabel(channelLabel);
        invite.setMissCount(missCount);
        invite = inviteRepository.save(invite);

        String studentName = student.getUser().getFullName();
        String title = "Thư mời phụ huynh chờ duyệt";
        String content = "Học sinh " + studentName + " (lớp " + schoolClass.getName() + ") thiếu " + channelLabel
                + " liên tục " + missCount + " buổi — cần bạn duyệt trước khi gửi thư mời phụ huynh.";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentName", studentName);
        metadata.put("className", schoolClass.getName());
        metadata.put("count", 1);

        for (SiteManager sm : siteManagerRepository.findBySiteIdAndRoleTypeAndAssignedToIsNull(
                schoolClass.getSite().getId(), SiteManager.RoleType.SITE_MANAGER)) {
            notificationService.notify(sm.getUser().getId(), Notification.NotificationType.HOMEWORK_MEETING_INVITE_PENDING_APPROVAL,
                    title, content, metadata, "HOMEWORK_PARENT_MEETING_INVITE", invite.getId(),
                    Notification.Priority.HIGH, null);
        }
    }

    @Transactional(readOnly = true)
    public List<HomeworkParentMeetingInviteResponse> listPendingForSite(Long actorUserId) {
        List<Long> siteIds = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                .map(sm -> sm.getSite().getId()).toList();
        return siteIds.stream()
                .flatMap(siteId -> inviteRepository.findByStatusAndSchoolClass_Site_IdOrderByCreatedAtAsc(
                        HomeworkParentMeetingInvite.Status.PENDING, siteId).stream())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<HomeworkParentMeetingInviteResponse> decide(List<Long> inviteIds, String decision, String comment, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        HomeworkParentMeetingInvite.Status target = "APPROVED".equalsIgnoreCase(decision)
                ? HomeworkParentMeetingInvite.Status.APPROVED : HomeworkParentMeetingInvite.Status.REJECTED;

        List<HomeworkParentMeetingInvite> invites = inviteRepository.findAllById(inviteIds);
        if (invites.size() != inviteIds.size()) {
            throw new ResourceNotFoundException("error.homeworkParentMeetingInvite.notFound", new Object[]{},
                    "Có thư mời không tồn tại trong danh sách inviteIds.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (HomeworkParentMeetingInvite invite : invites) {
            requireSiteManagerForSite(invite.getSchoolClass().getSite().getId(), actorUserId);
            if (invite.getStatus() != HomeworkParentMeetingInvite.Status.PENDING) {
                throw new ApprovalAlreadyDecidedException(
                        "error.approvalAlreadyDecided.homeworkParentMeetingInvite", new Object[]{invite.getStatus()},
                        "Thư mời này đã được quyết định (" + invite.getStatus() + ").");
            }
            invite.setStatus(target);
            invite.setDecidedBy(actor);
            invite.setDecidedAt(now);
            if (target == HomeworkParentMeetingInvite.Status.REJECTED) {
                invite.setRejectionReason(comment);
            }
        }
        List<HomeworkParentMeetingInvite> saved = inviteRepository.saveAll(invites);
        if (target == HomeworkParentMeetingInvite.Status.APPROVED) {
            saved.forEach(invite -> notifyParents(invite, actorUserId));
        }
        return saved.stream().map(this::toResponse).toList();
    }

    /** Gửi đúng ĐÚNG nội dung/mẫu HOMEWORK_MISS_PARENT_MEETING_INVITE cũ (email/push đã có sẵn) — chỉ trì hoãn tới lúc được duyệt. */
    private void notifyParents(HomeworkParentMeetingInvite invite, Long actorUserId) {
        Student student = invite.getStudent();
        SchoolClass schoolClass = invite.getSchoolClass();
        String studentName = student.getUser().getFullName();
        String title = "Cảnh báo BTVN — học sinh " + studentName;
        String content = invite.getChannelLabel() + ": thiếu liên tục " + invite.getMissCount()
                + " buổi (Thư mời phụ huynh tới làm việc).";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentName", studentName);
        metadata.put("className", schoolClass.getName());
        metadata.put("channelLabel", invite.getChannelLabel());
        metadata.put("count", invite.getMissCount());

        for (ParentStudent link : parentStudentRepository.findByStudentId(student.getId())) {
            notificationService.notify(link.getParent().getUser().getId(),
                    Notification.NotificationType.HOMEWORK_MISS_PARENT_MEETING_INVITE, title, content,
                    metadata, "STUDENT", student.getId(), Notification.Priority.URGENT, actorUserId);
        }
    }

    private void requireSiteManagerForSite(Long siteId, Long actorUserId) {
        if (!siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                siteId, actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            throw new NotSiteManagerForSiteException(
                    "Bạn không được gán phụ trách điểm trường này.");
        }
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.homeworkParentMeetingInvite.accountNotFoundById",
                        new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }

    private HomeworkParentMeetingInviteResponse toResponse(HomeworkParentMeetingInvite invite) {
        return new HomeworkParentMeetingInviteResponse(
                invite.getId(),
                invite.getStudent().getId(),
                invite.getStudent().getUser().getFullName(),
                invite.getSchoolClass().getId(),
                invite.getSchoolClass().getName(),
                invite.getChannelLabel(),
                invite.getMissCount(),
                invite.getStatus().name(),
                invite.getCreatedAt(),
                invite.getDecidedAt(),
                invite.getRejectionReason());
    }
}
