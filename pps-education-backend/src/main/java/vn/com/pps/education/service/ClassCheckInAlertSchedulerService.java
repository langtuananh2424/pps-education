package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.SiteManagerRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UC-71 mở rộng (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-09-21): cảnh báo khi giáo viên KHÔNG nhận lớp đúng hạn.
 * <ul>
 *   <li><b>Chưa nhận lớp (LATE)</b>: tới giờ bắt đầu buổi học +
 *       {@code class_checkin_alert.late_after_minutes} mà chưa có bản ghi
 *       class_session_check_ins.</li>
 *   <li><b>Không nhận lớp (ABSENT)</b>: đã qua giờ kết thúc buổi học mà vẫn
 *       chưa có bản ghi nhận lớp (đúng định nghĩa "ABSENT" tính ra của
 *       {@link ClassSessionCheckInService#listEffectiveStatus}).</li>
 * </ul>
 * Mỗi mốc gửi 2 thông báo, ÉP kênh theo nghiệp vụ (bỏ qua
 * notification_preferences cá nhân): PUSH (+ in-app) tới mọi Quản lý điểm
 * trường đang phụ trách site của lớp ("lớp X giáo viên chưa nhận lớp, hãy
 * kiểm tra"), và EMAIL (+ in-app) tới giáo viên dạy buổi đó (primaryTeacher,
 * và cmTeacher nếu có — vì CM cũng được phép nhận lớp thay GVNN). Đánh dấu
 * đã gửi bằng {@code checkin_late_alert_sent_at}/{@code checkin_absent_alert_sent_at}
 * trên class_sessions (V184) để job quét mỗi phút không gửi lặp. Tắt toàn
 * bộ bằng {@code class_checkin_alert.enabled}. Mirror cấu trúc
 * {@link HomeworkDueSoonReminderSchedulerService}.
 */
@Service
public class ClassCheckInAlertSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ClassCheckInAlertSchedulerService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<ClassSession.Status> NOT_CHECKABLE =
            List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED);
    private static final Set<NotificationDelivery.Channel> MANAGER_CHANNELS =
            Set.of(NotificationDelivery.Channel.IN_APP, NotificationDelivery.Channel.PUSH);
    private static final Set<NotificationDelivery.Channel> TEACHER_CHANNELS =
            Set.of(NotificationDelivery.Channel.IN_APP, NotificationDelivery.Channel.EMAIL);

    private final ClassSessionRepository classSessionRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final NotificationService notificationService;
    private final ClassCheckInAlertSettings settings;

    public ClassCheckInAlertSchedulerService(ClassSessionRepository classSessionRepository,
                                             SiteManagerRepository siteManagerRepository,
                                             NotificationService notificationService,
                                             ClassCheckInAlertSettings settings) {
        this.classSessionRepository = classSessionRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.notificationService = notificationService;
        this.settings = settings;
    }

    /** Quét mỗi phút — cửa sổ nhận lớp tính theo phút nên 5 phút/lần (như BTVN) là quá thô. */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void runAlertScan() {
        if (!settings.isEnabled()) {
            return;
        }
        runAlertScan(OffsetDateTime.now());
    }

    /** Tách tham số {@code now} để test điều khiển được mốc thời gian; {@link #runAlertScan()} là entry cron. */
    @Transactional
    public void runAlertScan(OffsetDateTime now) {
        LocalDate today = now.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        LocalTime nowTime = now.atZoneSameInstant(ZoneId.systemDefault()).toLocalTime();
        // Chỉ nhìn lại 1 ngày: đủ bắt buổi cuối ngày bị lệch qua nửa đêm, không quét lại lịch sử.
        LocalDate fromDate = today.minusDays(1);

        LocalTime lateCutoff = nowTime.minusMinutes(settings.lateAfterMinutes());
        // Nếu trừ phút bị wrap về cuối ngày hôm trước (VD 00:02 trừ 5 phút) thì hôm nay chưa có buổi nào đủ điều kiện.
        if (lateCutoff.isAfter(nowTime)) {
            lateCutoff = LocalTime.MIN;
        }
        List<ClassSession> lateSessions = classSessionRepository.findPendingCheckInLateAlerts(fromDate, today, lateCutoff, NOT_CHECKABLE);
        for (ClassSession session : lateSessions) {
            sendLateAlert(session);
            session.setCheckinLateAlertSentAt(now);
            classSessionRepository.save(session);
        }

        List<ClassSession> absentSessions = classSessionRepository.findPendingCheckInAbsentAlerts(fromDate, today, nowTime, NOT_CHECKABLE);
        for (ClassSession session : absentSessions) {
            sendAbsentAlert(session);
            session.setCheckinAbsentAlertSentAt(now);
            classSessionRepository.save(session);
        }

        if (!lateSessions.isEmpty() || !absentSessions.isEmpty()) {
            log.info("ClassCheckInAlertSchedulerService: gửi {} cảnh báo chưa nhận lớp, {} cảnh báo không nhận lớp.",
                    lateSessions.size(), absentSessions.size());
        }
    }

    private void sendLateAlert(ClassSession session) {
        Map<String, Object> metadata = metadataOf(session);
        String className = session.getSchoolClass().getName();
        String when = sessionLabel(session);

        // Tên GV đặt ngay trong tiêu đề: dropdown chuông Header cắt nội dung còn 2 dòng nên tên GV ở
        // cuối content hay bị che — Quản lý điểm trường cần thấy ngay ai chưa nhận lớp.
        String managerTitle = "Lớp " + className + ": giáo viên " + teacherLabel(session) + " chưa nhận lớp";
        String managerContent = "Lớp " + className + " (" + when + ", điểm trường " + session.getSchoolClass().getSite().getName()
                + ") đã tới giờ học nhưng giáo viên " + teacherLabel(session) + " chưa nhận lớp — hãy kiểm tra.";
        notifyManagers(session, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT, managerTitle, managerContent, metadata);

        String teacherTitle = "Bạn chưa nhận lớp " + className;
        String teacherContent = "Lớp " + className + " (" + when + ") đã tới giờ học nhưng bạn chưa nhận lớp. "
                + "Vui lòng mở ứng dụng và nhận lớp ngay — lượt nhận lớp lúc này sẽ được ghi nhận là MUỘN.";
        notifyTeachers(session, Notification.NotificationType.CLASS_CHECKIN_LATE_ALERT, teacherTitle, teacherContent, metadata);
    }

    private void sendAbsentAlert(ClassSession session) {
        Map<String, Object> metadata = metadataOf(session);
        String className = session.getSchoolClass().getName();
        String when = sessionLabel(session);

        String managerTitle = "Lớp " + className + ": giáo viên " + teacherLabel(session) + " không nhận lớp";
        String managerContent = "Lớp " + className + " (" + when + ", điểm trường " + session.getSchoolClass().getSite().getName()
                + ") đã kết thúc nhưng giáo viên " + teacherLabel(session) + " không nhận lớp — hãy kiểm tra.";
        notifyManagers(session, Notification.NotificationType.CLASS_CHECKIN_ABSENT_ALERT, managerTitle, managerContent, metadata);

        String teacherTitle = "Bạn không nhận lớp " + className;
        String teacherContent = "Lớp " + className + " (" + when + ") đã kết thúc nhưng hệ thống không ghi nhận lượt nhận lớp nào của bạn. "
                + "Buổi học được tính là KHÔNG NHẬN LỚP. Nếu có nhầm lẫn, vui lòng liên hệ Quản lý điểm trường.";
        notifyTeachers(session, Notification.NotificationType.CLASS_CHECKIN_ABSENT_ALERT, teacherTitle, teacherContent, metadata);
    }

    private void notifyManagers(ClassSession session, Notification.NotificationType type, String title, String content,
                                Map<String, Object> metadata) {
        Long siteId = session.getSchoolClass().getSite().getId();
        List<SiteManager> managers = siteManagerRepository.findBySiteIdAndRoleTypeAndAssignedToIsNull(siteId, SiteManager.RoleType.SITE_MANAGER);
        if (managers.isEmpty()) {
            log.warn("ClassCheckInAlertSchedulerService: site id={} không có Quản lý điểm trường đang phụ trách — bỏ qua push cho buổi id={}.",
                    siteId, session.getId());
        }
        Set<Long> notified = new LinkedHashSet<>();
        for (SiteManager m : managers) {
            if (notified.add(m.getUser().getId())) {
                notificationService.notifyWithForcedChannels(m.getUser().getId(), type, title, content, metadata,
                        "CLASS_SESSION", session.getId(), Notification.Priority.HIGH, null, MANAGER_CHANNELS);
            }
        }
    }

    private void notifyTeachers(ClassSession session, Notification.NotificationType type, String title, String content,
                                Map<String, Object> metadata) {
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(session.getPrimaryTeacher().getId());
        if (session.getCmTeacher() != null) {
            recipients.add(session.getCmTeacher().getId());
        }
        for (Long userId : recipients) {
            notificationService.notifyWithForcedChannels(userId, type, title, content, metadata,
                    "CLASS_SESSION", session.getId(), Notification.Priority.HIGH, null, TEACHER_CHANNELS);
        }
    }

    private static String sessionLabel(ClassSession session) {
        return "buổi " + DATE_FMT.format(session.getSessionDate()) + " "
                + TIME_FMT.format(session.getStartTime()) + "-" + TIME_FMT.format(session.getEndTime());
    }

    private static String teacherLabel(ClassSession session) {
        String name = session.getActualTeacherName() != null && !session.getActualTeacherName().isBlank()
                ? session.getActualTeacherName() : session.getPrimaryTeacher().getFullName();
        return name == null ? "" : name;
    }

    private static Map<String, Object> metadataOf(ClassSession session) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classSessionId", session.getId());
        metadata.put("className", session.getSchoolClass().getName());
        metadata.put("siteName", session.getSchoolClass().getSite().getName());
        metadata.put("teacherName", teacherLabel(session));
        metadata.put("sessionDate", session.getSessionDate());
        // Lưu sẵn "HH:mm" (không lưu LocalTime) vì metadata JSONB đọc lại ở lúc dispatch chỉ còn chuỗi
        // ISO "HH:mm:ss" — push template sẽ in nguyên chuỗi này.
        metadata.put("startTime", TIME_FMT.format(session.getStartTime()));
        metadata.put("endTime", TIME_FMT.format(session.getEndTime()));
        return metadata;
    }
}
