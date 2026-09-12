package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentAttitudeEscalation;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.StudentAttitudeEscalationResponse;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.StudentAttitudeEscalationRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-12): cảnh
 * báo thái độ học tập Yếu/Trung bình liên tục 3 buổi phải được Quản lý
 * điểm trường DUYỆT trước khi gửi xuống Phụ huynh — cảnh báo 1 buổi đơn lẻ
 * (STUDENT_ATTITUDE_ALERT) vẫn gửi thẳng, không qua service này. Mirror
 * 1:1 {@code HomeworkParentMeetingInviteService}.
 *
 * KHÔNG dùng chung {@code ApprovalFlow} — xem Javadoc {@link StudentAttitudeEscalation}.
 */
@Service
public class StudentAttitudeEscalationService {

    private final StudentAttitudeEscalationRepository escalationRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public StudentAttitudeEscalationService(StudentAttitudeEscalationRepository escalationRepository,
                                             SiteManagerRepository siteManagerRepository,
                                             ParentStudentRepository parentStudentRepository,
                                             UserRepository userRepository,
                                             NotificationService notificationService) {
        this.escalationRepository = escalationRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** Gọi từ StudentAttitudeAlertTrackingService khi streak chạm mốc 3 — tạo bản ghi chờ duyệt, báo Quản lý điểm trường. */
    @Transactional
    public void submitForApproval(Student student, SchoolClass schoolClass, int streakCount) {
        StudentAttitudeEscalation escalation = new StudentAttitudeEscalation();
        escalation.setStudent(student);
        escalation.setSchoolClass(schoolClass);
        escalation.setStreakCount(streakCount);
        escalation = escalationRepository.save(escalation);

        String studentName = student.getUser().getFullName();
        String title = "Cảnh báo thái độ chờ duyệt";
        String content = "Học sinh " + studentName + " (lớp " + schoolClass.getName() + ") có thái độ học tập yếu/trung bình liên tục "
                + streakCount + " buổi — cần bạn duyệt trước khi gửi phụ huynh.";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentName", studentName);
        metadata.put("className", schoolClass.getName());
        metadata.put("streakCount", streakCount);

        for (SiteManager sm : siteManagerRepository.findBySiteIdAndRoleTypeAndAssignedToIsNull(
                schoolClass.getSite().getId(), SiteManager.RoleType.SITE_MANAGER)) {
            notificationService.notify(sm.getUser().getId(), Notification.NotificationType.STUDENT_ATTITUDE_ESCALATION_PENDING_APPROVAL,
                    title, content, metadata, "STUDENT_ATTITUDE_ESCALATION", escalation.getId(),
                    Notification.Priority.HIGH, null);
        }
    }

    @Transactional(readOnly = true)
    public List<StudentAttitudeEscalationResponse> listPendingForSite(Long actorUserId) {
        List<Long> siteIds = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                .map(sm -> sm.getSite().getId()).toList();
        return siteIds.stream()
                .flatMap(siteId -> escalationRepository.findByStatusAndSchoolClass_Site_IdOrderByCreatedAtAsc(
                        StudentAttitudeEscalation.Status.PENDING, siteId).stream())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<StudentAttitudeEscalationResponse> decide(List<Long> escalationIds, String decision, String comment, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        StudentAttitudeEscalation.Status target = "APPROVED".equalsIgnoreCase(decision)
                ? StudentAttitudeEscalation.Status.APPROVED : StudentAttitudeEscalation.Status.REJECTED;

        List<StudentAttitudeEscalation> escalations = escalationRepository.findAllById(escalationIds);
        if (escalations.size() != escalationIds.size()) {
            throw new ResourceNotFoundException("error.studentAttitudeEscalation.notFound", new Object[]{},
                    "Có cảnh báo không tồn tại trong danh sách escalationIds.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (StudentAttitudeEscalation escalation : escalations) {
            requireSiteManagerForSite(escalation.getSchoolClass().getSite().getId(), actorUserId);
            if (escalation.getStatus() != StudentAttitudeEscalation.Status.PENDING) {
                throw new ApprovalAlreadyDecidedException(
                        "error.approvalAlreadyDecided.studentAttitudeEscalation", new Object[]{escalation.getStatus()},
                        "Cảnh báo này đã được quyết định (" + escalation.getStatus() + ").");
            }
            escalation.setStatus(target);
            escalation.setDecidedBy(actor);
            escalation.setDecidedAt(now);
            if (target == StudentAttitudeEscalation.Status.REJECTED) {
                escalation.setRejectionReason(comment);
            }
        }
        List<StudentAttitudeEscalation> saved = escalationRepository.saveAll(escalations);
        if (target == StudentAttitudeEscalation.Status.APPROVED) {
            saved.forEach(escalation -> notifyParents(escalation, actorUserId));
        }
        return saved.stream().map(this::toResponse).toList();
    }

    private void notifyParents(StudentAttitudeEscalation escalation, Long actorUserId) {
        Student student = escalation.getStudent();
        SchoolClass schoolClass = escalation.getSchoolClass();
        String studentName = student.getUser().getFullName();
        String title = "Cảnh báo thái độ học tập liên tục";
        String content = studentName + " (lớp " + schoolClass.getName() + ") có thái độ học tập yếu/trung bình liên tục "
                + escalation.getStreakCount() + " buổi. Kính mong Quý Phụ huynh quan tâm, đồng hành cùng con.";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentName", studentName);
        metadata.put("className", schoolClass.getName());
        metadata.put("streakCount", escalation.getStreakCount());

        for (ParentStudent link : parentStudentRepository.findByStudentId(student.getId())) {
            notificationService.notify(link.getParent().getUser().getId(),
                    Notification.NotificationType.STUDENT_ATTITUDE_ESCALATION, title, content,
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
                .orElseThrow(() -> new ResourceNotFoundException("error.studentAttitudeEscalation.accountNotFoundById",
                        new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }

    private StudentAttitudeEscalationResponse toResponse(StudentAttitudeEscalation escalation) {
        return new StudentAttitudeEscalationResponse(
                escalation.getId(),
                escalation.getStudent().getId(),
                escalation.getStudent().getUser().getFullName(),
                escalation.getSchoolClass().getId(),
                escalation.getSchoolClass().getName(),
                escalation.getStreakCount(),
                escalation.getStatus().name(),
                escalation.getCreatedAt(),
                escalation.getDecidedAt(),
                escalation.getRejectionReason());
    }
}
