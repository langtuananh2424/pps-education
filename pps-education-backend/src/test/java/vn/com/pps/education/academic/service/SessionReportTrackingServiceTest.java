package vn.com.pps.education.academic.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.permission.domain.ApprovalFlow;
import vn.com.pps.education.academic.domain.ClassEnrollment;
import vn.com.pps.education.academic.domain.ClassSession;
import vn.com.pps.education.notification.domain.Notification;
import vn.com.pps.education.permission.domain.Role;
import vn.com.pps.education.academic.domain.SchoolClass;
import vn.com.pps.education.facility.domain.Site;
import vn.com.pps.education.student.domain.Student;
import vn.com.pps.education.student.domain.StudentComment;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.permission.domain.UserRole;
import vn.com.pps.education.lms.dto.ClassResponse;
import vn.com.pps.education.academic.dto.CreateClassRequest;
import vn.com.pps.education.academic.dto.CreateCurriculumRequest;
import vn.com.pps.education.academic.dto.SessionReportStatusRow;
import vn.com.pps.education.academic.dto.SessionReportTimelineEvent;
import vn.com.pps.education.academic.dto.UpdateCurriculumRequest;
import vn.com.pps.education.permission.repository.ApprovalFlowRepository;
import vn.com.pps.education.academic.repository.ClassEnrollmentRepository;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.notification.repository.NotificationRepository;
import vn.com.pps.education.permission.repository.RoleRepository;
import vn.com.pps.education.academic.repository.SchoolClassRepository;
import vn.com.pps.education.facility.repository.SiteRepository;
import vn.com.pps.education.student.repository.StudentCommentRepository;
import vn.com.pps.education.student.repository.StudentRepository;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.permission.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V207 (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-01) — theo dõi nộp & duyệt báo cáo buổi học:
 * 3 khâu (giáo viên nộp / Quản lý điểm trường duyệt / giáo viên gửi lại) và cảnh báo. Hạn mặc định (V207):
 * nộp 24 giờ sau khi buổi học kết thúc (buổi 08:00-09:30 → hạn 09:30 hôm sau), duyệt 24 giờ, gửi lại 12 giờ.
 */
@Transactional
class SessionReportTrackingServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private SessionReportTrackingService trackingService;
    @Autowired private SessionReportAlertSchedulerService alertScheduler;
    @Autowired private ClassService classService;
    @Autowired private CurriculumService curriculumService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private ClassSessionRepository classSessionRepository;
    @Autowired private ClassEnrollmentRepository classEnrollmentRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentCommentRepository studentCommentRepository;
    @Autowired private ApprovalFlowRepository approvalFlowRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User headAcademic;
    private User teacher;
    private User siteManager;
    private SchoolClass schoolClass;
    private Student student;
    private LocalDate sessionDay;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        teacher = newUser("teacher");
        siteManager = newUser("site.manager");

        var curriculum = curriculumService.create(
                new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null, null, null),
                headAcademic.getId());
        var activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());
        Site site = newSite();
        ClassResponse created = classService.create(
                new CreateClassRequest("CLS-" + SEQ.incrementAndGet(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now().minusMonths(1), null, null), headAcademic.getId());
        schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(created.id()).orElseThrow();

        student = newStudent();
        ClassEnrollment enrollment = new ClassEnrollment();
        enrollment.setSchoolClass(schoolClass);
        enrollment.setStudent(student);
        enrollment.setEnrolledDate(LocalDate.now().minusMonths(1));
        enrollment.setStatus(ClassEnrollment.Status.ACTIVE);
        enrollment.setEnrolledBy(headAcademic);
        classEnrollmentRepository.save(enrollment);

        sessionDay = LocalDate.now(VN).minusDays(2);
    }

    // ===================== Khâu 1 — giáo viên nộp =====================

    @Test
    void computeRow_V207_submitBeforeDeadline_isOnTime() {
        ClassSession session = newSession(ClassSession.Status.SCHEDULED);
        StudentComment comment = newComment(session, StudentComment.Status.PENDING);
        newFlow(comment, at(sessionDay, 21, 0), ApprovalFlow.Status.PENDING, null, null);

        SessionReportStatusRow row = rowOf(session, at(sessionDay, 21, 30));

        assertThat(row.submitState()).isEqualTo("ON_TIME");
        assertThat(row.submitLateMinutes()).isZero();
        assertThat(row.approvalState()).isEqualTo("WAITING");
    }

    @Test
    void computeRow_V207_submitAfterDeadline_isLateWithMinutes() {
        ClassSession session = newSession(ClassSession.Status.SCHEDULED);
        StudentComment comment = newComment(session, StudentComment.Status.PENDING);
        newFlow(comment, at(sessionDay.plusDays(1), 11, 0), ApprovalFlow.Status.PENDING, null, null);

        SessionReportStatusRow row = rowOf(session, at(sessionDay.plusDays(1), 12, 0));

        assertThat(row.submitState()).isEqualTo("LATE");
        assertThat(row.submitLateMinutes()).isEqualTo(90);
    }

    @Test
    void computeRow_V207_noSubmissionAfterDeadline_isMissing() {
        ClassSession session = newSession(ClassSession.Status.SCHEDULED);

        assertThat(rowOf(session, at(sessionDay.plusDays(1), 9, 0)).submitState()).isEqualTo("NOT_DUE");
        assertThat(rowOf(session, at(sessionDay.plusDays(1), 10, 0)).submitState()).isEqualTo("MISSING");
    }

    @Test
    void evaluateAll_V207_cancelledSessionIsNotTracked() {
        ClassSession cancelled = newSession(ClassSession.Status.CANCELLED);

        assertThat(trackingService.evaluateAll(sessionDay, sessionDay, at(sessionDay.plusDays(1), 8, 0)))
                .noneMatch(e -> e.session().getId().equals(cancelled.getId()));
    }

    // ===================== Khâu 2 — Quản lý điểm trường duyệt =====================

    @Test
    void computeRow_V207_teacherOnTimeButApproverLate_isFlaggedOnApprovalOnly() {
        ClassSession session = newSession(ClassSession.Status.SCHEDULED);
        StudentComment comment = newComment(session, StudentComment.Status.APPROVED);
        newFlow(comment, at(sessionDay, 21, 0), ApprovalFlow.Status.APPROVED, siteManager, at(sessionDay.plusDays(1), 23, 0));

        SessionReportStatusRow row = rowOf(session, at(sessionDay.plusDays(2), 8, 0));

        assertThat(row.submitState()).isEqualTo("ON_TIME");
        assertThat(row.approvalState()).isEqualTo("LATE");
        assertThat(row.approvalLateMinutes()).isEqualTo(120);
        assertThat(row.fullyApprovedAt()).isNotNull();
        assertThat(row.approverNames()).containsExactly(siteManager.getFullName());
    }

    @Test
    void computeRow_V207_pendingPastApprovalWindow_isOverdue() {
        ClassSession session = newSession(ClassSession.Status.SCHEDULED);
        StudentComment comment = newComment(session, StudentComment.Status.PENDING);
        OffsetDateTime submittedAt = at(sessionDay, 21, 0);
        newFlow(comment, submittedAt, ApprovalFlow.Status.PENDING, null, null);

        SessionReportStatusRow row = rowOf(session, submittedAt.plusHours(25));

        assertThat(row.approvalState()).isEqualTo("OVERDUE");
        assertThat(row.openApprovalSince()).isEqualTo(submittedAt);
    }

    // ===================== Khâu 3 — gửi lại sau khi bị từ chối =====================

    @Test
    void computeRow_V207_rejectedThenResubmittedLate_countsRejectionAndLateResubmit() {
        ClassSession session = newSession(ClassSession.Status.SCHEDULED);
        StudentComment comment = newComment(session, StudentComment.Status.PENDING);
        OffsetDateTime rejectedAt = at(sessionDay, 21, 30);
        newFlow(comment, at(sessionDay, 21, 0), ApprovalFlow.Status.REJECTED, siteManager, rejectedAt);
        newFlow(comment, rejectedAt.plusHours(12).plusMinutes(15), ApprovalFlow.Status.PENDING, null, null);

        SessionReportStatusRow row = rowOf(session, rejectedAt.plusHours(13));

        assertThat(row.rejectionCount()).isEqualTo(1);
        assertThat(row.resubmitState()).isEqualTo("LATE");
        assertThat(row.resubmitLateMinutes()).isEqualTo(15);

        List<SessionReportTimelineEvent> timeline = trackingService.timelineForSession(session.getId());
        assertThat(timeline).extracting(SessionReportTimelineEvent::type)
                .containsExactly("SUBMITTED", "REJECTED", "RESUBMITTED");
        assertThat(timeline.get(2).timeliness()).isEqualTo("LATE");
    }

    @Test
    void computeRow_V207_rejectedNotResubmittedPastWindow_isOverdue() {
        ClassSession session = newSession(ClassSession.Status.SCHEDULED);
        StudentComment comment = newComment(session, StudentComment.Status.REJECTED);
        OffsetDateTime rejectedAt = at(sessionDay, 21, 30);
        newFlow(comment, at(sessionDay, 21, 0), ApprovalFlow.Status.REJECTED, siteManager, rejectedAt);

        SessionReportStatusRow row = rowOf(session, rejectedAt.plusHours(13));

        assertThat(row.resubmitState()).isEqualTo("OVERDUE");
        assertThat(row.openRejectionSince()).isEqualTo(rejectedAt);
    }

    // ===================== Cảnh báo =====================

    @Test
    void run_V207_missingReportAlertsTeacherAndHeadAcademicOnce() {
        ClassSession session = newSession(ClassSession.Status.SCHEDULED);
        OffsetDateTime now = at(sessionDay.plusDays(1), 10, 0);

        alertScheduler.run(now);
        alertScheduler.run(now.plusMinutes(1));

        assertThat(countNotifications(teacher, Notification.NotificationType.SESSION_REPORT_OVERDUE)).isEqualTo(1);
        assertThat(countNotifications(headAcademic, Notification.NotificationType.SESSION_REPORT_ESCALATION)).isEqualTo(1);
        assertThat(classSessionRepository.findById(session.getId()).orElseThrow().getReportOverdueAlertSentAt()).isNotNull();
    }

    @Test
    void run_V207_approvalOverdueAlertsAgainOnlyForNewCycle() {
        ClassSession session = newSession(ClassSession.Status.SCHEDULED);
        StudentComment comment = newComment(session, StudentComment.Status.PENDING);
        OffsetDateTime submittedAt = at(sessionDay, 21, 0);
        newFlow(comment, submittedAt, ApprovalFlow.Status.PENDING, null, null);

        alertScheduler.run(submittedAt.plusHours(25));
        alertScheduler.run(submittedAt.plusHours(26));

        assertThat(countNotifications(headAcademic, Notification.NotificationType.SESSION_REPORT_ESCALATION)).isEqualTo(1);
    }

    // ===================== Helpers =====================

    private SessionReportStatusRow rowOf(ClassSession session, OffsetDateTime now) {
        return trackingService.evaluateAll(session.getSessionDate(), session.getSessionDate(), now).stream()
                .filter(e -> e.session().getId().equals(session.getId()))
                .findFirst().orElseThrow().row();
    }

    private long countNotifications(User recipient, Notification.NotificationType type) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipient.getId(), Pageable.unpaged())
                .stream().filter(n -> n.getNotificationType() == type).count();
    }

    private static OffsetDateTime at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(VN).toOffsetDateTime();
    }

    private ClassSession newSession(ClassSession.Status status) {
        ClassSession session = new ClassSession();
        session.setSchoolClass(schoolClass);
        session.setSessionDate(sessionDay);
        session.setStartTime(LocalTime.of(8, 0));
        session.setEndTime(LocalTime.of(9, 30));
        session.setPrimaryTeacher(teacher);
        session.setStatus(status);
        session.setCreatedBy(headAcademic);
        return classSessionRepository.save(session);
    }

    private StudentComment newComment(ClassSession session, StudentComment.Status status) {
        StudentComment comment = new StudentComment();
        comment.setStudent(student);
        comment.setSchoolClass(schoolClass);
        comment.setTeacher(teacher);
        comment.setCommentType(StudentComment.CommentType.DAILY);
        comment.setClassSession(session);
        comment.setCommentDate(session.getSessionDate());
        comment.setContent("Nhận xét");
        comment.setStatus(status);
        return studentCommentRepository.save(comment);
    }

    private void newFlow(StudentComment comment, OffsetDateTime submittedAt, ApprovalFlow.Status status,
                         User approver, OffsetDateTime decidedAt) {
        ApprovalFlow flow = new ApprovalFlow();
        flow.setEntityType(ApprovalFlow.EntityType.STUDENT_COMMENT);
        flow.setEntityId(comment.getId());
        flow.setStatus(status);
        flow.setSubmittedBy(teacher);
        flow.setSubmittedAt(submittedAt);
        flow.setApprover(approver);
        flow.setDecidedAt(decidedAt);
        if (status == ApprovalFlow.Status.APPROVED) {
            flow.setDecision(ApprovalFlow.Decision.APPROVED);
        } else if (status == ApprovalFlow.Status.REJECTED) {
            flow.setDecision(ApprovalFlow.Decision.REJECTED);
        }
        approvalFlowRepository.save(flow);
    }

    private Student newStudent() {
        Student s = new Student();
        s.setUser(newUser("student"));
        s.setStudentCode("HS-SR-" + SEQ.incrementAndGet());
        s.setDateOfBirth(LocalDate.of(2012, 5, 1));
        s.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(s);
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-SR-" + SEQ.incrementAndGet());
        s.setName("Test Site " + SEQ.get());
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
