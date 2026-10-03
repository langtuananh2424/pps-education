package vn.com.pps.education.academic.service;

import vn.com.pps.education.notification.service.NotificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.domain.ClassSession;
import vn.com.pps.education.notification.domain.Notification;
import vn.com.pps.education.notification.domain.NotificationDelivery;
import vn.com.pps.education.facility.domain.SiteManager;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.academic.dto.SessionReportStatusRow;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.facility.repository.SiteManagerRepository;
import vn.com.pps.education.permission.repository.UserRoleRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cảnh báo nộp & duyệt báo cáo buổi học (V207 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-01).
 * Trạng thái từng buổi tính ở {@link SessionReportTrackingService}; job này chỉ quyết định gửi gì, cho ai:
 * <table>
 *   <tr><td>Sắp hết hạn nộp</td><td>Giáo viên (phụ trách + CM)</td></tr>
 *   <tr><td>Quá hạn nộp mà chưa nộp</td><td>Giáo viên + Trưởng phòng đào tạo</td></tr>
 *   <tr><td>Sắp hết hạn duyệt</td><td>Quản lý điểm trường của lớp</td></tr>
 *   <tr><td>Quá hạn duyệt mà chưa duyệt</td><td>Quản lý điểm trường + Trưởng phòng đào tạo</td></tr>
 *   <tr><td>Quá hạn gửi lại sau khi bị từ chối</td><td>Giáo viên + Trưởng phòng đào tạo</td></tr>
 *   <tr><td>Tổng hợp hằng ngày (hôm qua)</td><td>Trưởng phòng đào tạo</td></tr>
 * </table>
 * Mỗi mốc đánh dấu đã gửi trên class_sessions (V207). Khâu duyệt/gửi lại lặp được nhiều vòng nên chỉ gửi
 * lại khi vòng đang mở bắt đầu SAU mốc đã gửi. "Trưởng phòng đào tạo" = mọi tài khoản mang vai trò
 * HEAD_ACADEMIC. Kênh: trong ứng dụng + push (ép kênh như cảnh báo nhận lớp). Mirror
 * {@link ClassCheckInAlertSchedulerService}.
 */
@Service
public class SessionReportAlertSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SessionReportAlertSchedulerService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Chỉ xét các buổi trong 7 ngày gần nhất — đủ bắt các vòng duyệt/gửi lại kéo dài, không quét lại lịch sử. */
    private static final int LOOKBACK_DAYS = 7;
    private static final String HEAD_ACADEMIC_ROLE = "HEAD_ACADEMIC";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM");
    private static final Set<NotificationDelivery.Channel> CHANNELS =
            Set.of(NotificationDelivery.Channel.IN_APP, NotificationDelivery.Channel.PUSH);

    private final SessionReportTrackingService trackingService;
    private final SessionReportSettings settings;
    private final ClassSessionRepository classSessionRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final UserRoleRepository userRoleRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public SessionReportAlertSchedulerService(SessionReportTrackingService trackingService,
                                              SessionReportSettings settings,
                                              ClassSessionRepository classSessionRepository,
                                              SiteManagerRepository siteManagerRepository,
                                              UserRoleRepository userRoleRepository,
                                              NotificationService notificationService,
                                              Clock clock) {
        this.trackingService = trackingService;
        this.settings = settings;
        this.classSessionRepository = classSessionRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.userRoleRepository = userRoleRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /** Quét mỗi phút (mirror cảnh báo nhận lớp) — hạn tính theo phút nên quét thưa hơn sẽ nhắc trễ. */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void runScan() {
        if (!settings.isAlertEnabled()) {
            return;
        }
        run(OffsetDateTime.ofInstant(clock.instant(), APP_ZONE));
    }

    /** Tách tham số {@code now} để test điều khiển được mốc thời gian; {@link #runScan()} là entry cron. */
    @Transactional
    public void run(OffsetDateTime now) {
        LocalDate today = now.atZoneSameInstant(APP_ZONE).toLocalDate();
        int submitDueSoon = settings.submitDueSoonMinutes();
        int approvalDueSoon = settings.approvalDueSoonMinutes();
        Duration approvalWindow = Duration.ofHours(settings.approvalDeadlineHours());
        Set<Long> headAcademicIds = null;
        int sent = 0;

        for (SessionReportTrackingService.EvaluatedSession e : trackingService.evaluateAll(today.minusDays(LOOKBACK_DAYS), today, now)) {
            ClassSession s = e.session();
            SessionReportStatusRow r = e.row();
            boolean changed = false;

            if (submitDueSoon > 0 && "NOT_DUE".equals(r.submitState()) && s.getReportDueSoonAlertSentAt() == null
                    && !now.isBefore(r.submitDeadline().minusMinutes(submitDueSoon))) {
                notifyTeachers(s, Notification.NotificationType.SESSION_REPORT_DUE_SOON,
                        "Sắp hết hạn gửi báo cáo lớp " + r.className(),
                        "Lớp " + r.className() + " (" + sessionLabel(r) + ") chưa gửi duyệt nhận xét. Hạn nộp: "
                                + format(r.submitDeadline()) + ".", r);
                s.setReportDueSoonAlertSentAt(now);
                changed = true;
            }

            if ("MISSING".equals(r.submitState()) && s.getReportOverdueAlertSentAt() == null) {
                notifyTeachers(s, Notification.NotificationType.SESSION_REPORT_OVERDUE,
                        "Quá hạn gửi báo cáo lớp " + r.className(),
                        "Lớp " + r.className() + " (" + sessionLabel(r) + ") đã quá hạn nộp (" + format(r.submitDeadline())
                                + ") mà chưa gửi duyệt nhận xét. Vui lòng gửi ngay — báo cáo sẽ được ghi nhận nộp MUỘN.", r);
                headAcademicIds = headAcademicIds == null ? resolveHeadAcademicIds() : headAcademicIds;
                notifyUsers(headAcademicIds, s, "Lớp " + r.className() + ": GV " + r.teacherName() + " chưa gửi báo cáo",
                        "Lớp " + r.className() + " (" + sessionLabel(r) + ", điểm trường " + r.siteName() + ") đã quá hạn nộp "
                                + format(r.submitDeadline()) + " mà giáo viên " + r.teacherName() + " chưa gửi duyệt nhận xét.", r);
                s.setReportOverdueAlertSentAt(now);
                if (s.getReportDueSoonAlertSentAt() == null) {
                    s.setReportDueSoonAlertSentAt(now);
                }
                changed = true;
            }

            OffsetDateTime approvalCycle = r.openApprovalSince();
            if (approvalDueSoon > 0 && "WAITING".equals(r.approvalState()) && approvalCycle != null
                    && isNewCycle(s.getReportApprovalDueSoonAlertSentAt(), approvalCycle)
                    && !now.isBefore(approvalCycle.plus(approvalWindow).minusMinutes(approvalDueSoon))) {
                notifyManagers(s, Notification.NotificationType.SESSION_REPORT_APPROVAL_DUE_SOON,
                        "Sắp hết hạn duyệt báo cáo lớp " + r.className(),
                        "Báo cáo lớp " + r.className() + " (" + sessionLabel(r) + ") của GV " + r.teacherName()
                                + " gửi lúc " + format(approvalCycle) + " cần được duyệt trước "
                                + format(approvalCycle.plus(approvalWindow)) + ".", r);
                s.setReportApprovalDueSoonAlertSentAt(now);
                changed = true;
            }

            if ("OVERDUE".equals(r.approvalState()) && approvalCycle != null
                    && isNewCycle(s.getReportApprovalOverdueAlertSentAt(), approvalCycle)) {
                String content = "Báo cáo lớp " + r.className() + " (" + sessionLabel(r) + ", điểm trường " + r.siteName()
                        + ") của GV " + r.teacherName() + " gửi lúc " + format(approvalCycle) + " đã quá hạn duyệt "
                        + format(approvalCycle.plus(approvalWindow)) + " mà chưa được duyệt.";
                notifyManagers(s, Notification.NotificationType.SESSION_REPORT_APPROVAL_OVERDUE,
                        "Quá hạn duyệt báo cáo lớp " + r.className(), content, r);
                headAcademicIds = headAcademicIds == null ? resolveHeadAcademicIds() : headAcademicIds;
                notifyUsers(headAcademicIds, s, "Lớp " + r.className() + ": báo cáo chờ duyệt quá hạn", content, r);
                s.setReportApprovalOverdueAlertSentAt(now);
                if (isNewCycle(s.getReportApprovalDueSoonAlertSentAt(), approvalCycle)) {
                    s.setReportApprovalDueSoonAlertSentAt(now);
                }
                changed = true;
            }

            OffsetDateTime rejectionCycle = r.openRejectionSince();
            if ("OVERDUE".equals(r.resubmitState()) && rejectionCycle != null
                    && isNewCycle(s.getReportResubmitOverdueAlertSentAt(), rejectionCycle)) {
                notifyTeachers(s, Notification.NotificationType.SESSION_REPORT_RESUBMIT_OVERDUE,
                        "Quá hạn gửi lại báo cáo lớp " + r.className(),
                        "Nhận xét lớp " + r.className() + " (" + sessionLabel(r) + ") bị từ chối lúc " + format(rejectionCycle)
                                + " đã quá hạn gửi lại. Vui lòng sửa và gửi lại ngay.", r);
                headAcademicIds = headAcademicIds == null ? resolveHeadAcademicIds() : headAcademicIds;
                notifyUsers(headAcademicIds, s, "Lớp " + r.className() + ": GV " + r.teacherName() + " chưa gửi lại báo cáo",
                        "Nhận xét lớp " + r.className() + " (" + sessionLabel(r) + ", điểm trường " + r.siteName()
                                + ") bị từ chối lúc " + format(rejectionCycle) + " nhưng giáo viên " + r.teacherName()
                                + " chưa gửi lại đúng hạn.", r);
                s.setReportResubmitOverdueAlertSentAt(now);
                changed = true;
            }

            if (changed) {
                classSessionRepository.save(s);
                sent++;
            }
        }

        ZonedDateTime local = now.atZoneSameInstant(APP_ZONE);
        int digestHour = settings.dailyDigestHour();
        if (digestHour >= 0 && local.getHour() == digestHour && local.getMinute() == 0) {
            sendDailyDigest(today.minusDays(1), now, headAcademicIds == null ? resolveHeadAcademicIds() : headAcademicIds);
        }
        if (sent > 0) {
            log.info("SessionReportAlertSchedulerService: gửi cảnh báo cho {} buổi học.", sent);
        }
    }

    /**
     * Tổng hợp hằng ngày cho Trưởng phòng đào tạo: các buổi của ngày {@code day} theo 3 khâu, cộng số mục
     * đang quá hạn (chưa duyệt / chưa gửi lại) trong {@link #LOOKBACK_DAYS} ngày gần nhất.
     */
    private void sendDailyDigest(LocalDate day, OffsetDateTime now, Set<Long> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        List<SessionReportStatusRow> rows = trackingService.evaluateAll(day.minusDays(LOOKBACK_DAYS - 1L), day, now).stream()
                .map(SessionReportTrackingService.EvaluatedSession::row).toList();
        List<SessionReportStatusRow> dayRows = rows.stream().filter(r -> r.sessionDate().equals(day)).toList();
        long onTime = dayRows.stream().filter(r -> "ON_TIME".equals(r.submitState())).count();
        long late = dayRows.stream().filter(r -> "LATE".equals(r.submitState())).count();
        long missing = dayRows.stream().filter(r -> "MISSING".equals(r.submitState())).count();
        long approvalLate = dayRows.stream().filter(r -> "LATE".equals(r.approvalState())).count();
        long approvalOverdue = rows.stream().filter(r -> "OVERDUE".equals(r.approvalState())).count();
        long resubmitOverdue = rows.stream().filter(r -> "OVERDUE".equals(r.resubmitState())).count();
        List<String> missingTeachers = dayRows.stream().filter(r -> "MISSING".equals(r.submitState()))
                .map(SessionReportStatusRow::teacherName).distinct().limit(5).toList();

        String title = "Tổng hợp báo cáo buổi học ngày " + DATE_FMT.format(day);
        StringBuilder content = new StringBuilder()
                .append(dayRows.size()).append(" buổi: ").append(onTime).append(" nộp đúng hạn, ")
                .append(late).append(" nộp muộn, ").append(missing).append(" chưa nộp. Duyệt muộn: ").append(approvalLate)
                .append(". Đang quá hạn: ").append(approvalOverdue).append(" chờ duyệt, ")
                .append(resubmitOverdue).append(" chưa gửi lại.");
        if (!missingTeachers.isEmpty()) {
            content.append(" GV chưa nộp: ").append(String.join(", ", missingTeachers)).append('.');
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("date", day.toString());
        for (Long userId : recipients) {
            notificationService.notifyWithForcedChannels(userId, Notification.NotificationType.SESSION_REPORT_DAILY_DIGEST,
                    title, content.toString(), metadata, null, null, Notification.Priority.NORMAL, null, CHANNELS);
        }
    }

    private void notifyTeachers(ClassSession session, Notification.NotificationType type, String title, String content,
                                SessionReportStatusRow row) {
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(session.getPrimaryTeacher().getId());
        if (session.getCmTeacher() != null) {
            recipients.add(session.getCmTeacher().getId());
        }
        for (Long userId : recipients) {
            send(userId, type, title, content, session, row);
        }
    }

    private void notifyManagers(ClassSession session, Notification.NotificationType type, String title, String content,
                                SessionReportStatusRow row) {
        Set<Long> recipients = new LinkedHashSet<>();
        for (SiteManager m : siteManagerRepository.findBySiteIdAndRoleTypeAndAssignedToIsNull(row.siteId(), SiteManager.RoleType.SITE_MANAGER)) {
            recipients.add(m.getUser().getId());
        }
        if (recipients.isEmpty()) {
            log.warn("SessionReportAlertSchedulerService: site id={} không có Quản lý điểm trường đang phụ trách — bỏ qua nhắc duyệt buổi id={}.",
                    row.siteId(), session.getId());
        }
        for (Long userId : recipients) {
            send(userId, type, title, content, session, row);
        }
    }

    private void notifyUsers(Set<Long> recipients, ClassSession session, String title, String content, SessionReportStatusRow row) {
        for (Long userId : recipients) {
            send(userId, Notification.NotificationType.SESSION_REPORT_ESCALATION, title, content, session, row);
        }
    }

    private void send(Long userId, Notification.NotificationType type, String title, String content,
                      ClassSession session, SessionReportStatusRow row) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classId", row.classId());
        metadata.put("classSessionId", session.getId());
        metadata.put("siteId", row.siteId());
        metadata.put("date", row.sessionDate().toString());
        notificationService.notifyWithForcedChannels(userId, type, title, content, metadata,
                "CLASS_SESSION", session.getId(), Notification.Priority.HIGH, null, CHANNELS);
    }

    private Set<Long> resolveHeadAcademicIds() {
        Set<Long> ids = new LinkedHashSet<>();
        userRoleRepository.findByRole_Code(HEAD_ACADEMIC_ROLE).stream()
                .map(ur -> ur.getUser())
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .forEach(u -> ids.add(u.getId()));
        return ids;
    }

    private static boolean isNewCycle(OffsetDateTime alertSentAt, OffsetDateTime cycleStart) {
        return alertSentAt == null || alertSentAt.isBefore(cycleStart);
    }

    private static String sessionLabel(SessionReportStatusRow r) {
        return "buổi " + DATE_FMT.format(r.sessionDate()) + " " + TIME_FMT.format(r.startTime()) + "-" + TIME_FMT.format(r.endTime());
    }

    private static String format(OffsetDateTime value) {
        return value.atZoneSameInstant(APP_ZONE).format(DATE_TIME_FMT);
    }
}
