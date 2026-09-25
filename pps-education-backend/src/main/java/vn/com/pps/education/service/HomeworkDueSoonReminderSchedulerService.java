package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: nhắc Phụ
 * huynh (và từ 2026-09-16, nhắc thêm chính Học sinh trên Portal — xem
 * {@link #notifyParents}) trước hạn nộp BTVN ~12 tiếng
 * (`system_settings.homework_alert.reminder_before_due_hours`) để nhắc
 * con làm bài, mirror cấu trúc {@link HomeworkDeadlineSchedulerService}
 * (cùng cron 5 phút, cùng cách quét theo `due_at`, tái dùng thẳng
 * {@code targetStudents(...)} của service đó thay vì lặp lại logic
 * enrollment). Chỉ nhắc học sinh CHƯA đạt (tái dùng
 * {@link HomeworkProgressService#grammarPassed}/{@code videoPassed}) —
 * học sinh đã làm xong không cần nhắc.
 * <p>
 * Khung đêm (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-25,
 * migration V195): nếu mốc nhắc (hạn nộp − reminder_before_due_hours) rơi vào
 * khung [reminder_quiet_start_hour, reminder_quiet_end_hour) — mặc định
 * 21:00–07:00 giờ VN — thì gửi SỚM hơn, lúc giờ bắt đầu khung đêm ngay trước
 * đó (VD hạn 12:00 trưa: nhắc 21:00 tối hôm trước thay vì 0:00). Giao bài
 * trong khung đêm mà mốc nhắc đã qua thì vẫn gửi ngay vì hạn đã gần. Xem
 * {@link #reminderSendAt}.
 */
@Service
public class HomeworkDueSoonReminderSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(HomeworkDueSoonReminderSchedulerService.class);
    // Hạn nộp hiện trong nội dung thông báo in-app phải là giờ Việt Nam, dạng "16:59 ngày 22/09/2026"
    // (theo yêu cầu người dùng 2026-09-22) — trước đây nối thẳng OffsetDateTime (UTC, ISO-8601) vào chuỗi.
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DUE_AT_FMT = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final HomeworkDeadlineSchedulerService homeworkDeadlineSchedulerService;
    private final HomeworkProgressService homeworkProgressService;
    private final HomeworkAlertSettings homeworkAlertSettings;
    private final NotificationService notificationService;
    private final ParentStudentRepository parentStudentRepository;

    public HomeworkDueSoonReminderSchedulerService(ExerciseAssignmentRepository exerciseAssignmentRepository,
                                                     ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                                                     HomeworkDeadlineSchedulerService homeworkDeadlineSchedulerService,
                                                     HomeworkProgressService homeworkProgressService,
                                                     HomeworkAlertSettings homeworkAlertSettings,
                                                     NotificationService notificationService,
                                                     ParentStudentRepository parentStudentRepository) {
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.homeworkDeadlineSchedulerService = homeworkDeadlineSchedulerService;
        this.homeworkProgressService = homeworkProgressService;
        this.homeworkAlertSettings = homeworkAlertSettings;
        this.notificationService = notificationService;
        this.parentStudentRepository = parentStudentRepository;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void runReminderScan() {
        if (!homeworkAlertSettings.isEnabled()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        int beforeDueHours = homeworkAlertSettings.reminderBeforeDueHours();
        int quietStartHour = homeworkAlertSettings.reminderQuietStartHour();
        int quietEndHour = homeworkAlertSettings.reminderQuietEndHour();
        // Quét rộng thêm độ dài khung đêm: mốc nhắc bị kéo sớm về giờ bắt đầu khung đêm có thể tới
        // trước hạn nộp tối đa beforeDueHours + độ dài khung đêm. Lọc chính xác bằng reminderSendAt.
        int quietLengthHours = Math.floorMod(quietEndHour - quietStartHour, 24);
        OffsetDateTime cutoff = now.plusHours(beforeDueHours + quietLengthHours);
        Predicate<OffsetDateTime> reminderDue = dueAt ->
                !reminderSendAt(dueAt, beforeDueHours, quietStartHour, quietEndHour).isAfter(now);
        processExerciseAssignments(now, cutoff, reminderDue);
        processReviewVideoAssignments(now, cutoff, reminderDue);
    }

    /**
     * Mốc gửi nhắc hạn BTVN: {@code dueAt − beforeDueHours}, nhưng nếu mốc đó rơi vào khung đêm
     * [quietStartHour, quietEndHour) (giờ VN, có thể vắt qua nửa đêm) thì kéo sớm về quietStartHour
     * của buổi tối ngay trước đó. quietStartHour = quietEndHour nghĩa là tắt khung đêm.
     */
    static OffsetDateTime reminderSendAt(OffsetDateTime dueAt, int beforeDueHours, int quietStartHour, int quietEndHour) {
        ZonedDateTime base = dueAt.atZoneSameInstant(APP_ZONE).minusHours(beforeDueHours);
        if (quietStartHour == quietEndHour) {
            return base.toOffsetDateTime();
        }
        int hour = base.getHour();
        boolean overMidnight = quietStartHour > quietEndHour;
        boolean inQuiet = overMidnight
                ? hour >= quietStartHour || hour < quietEndHour
                : hour >= quietStartHour && hour < quietEndHour;
        if (!inQuiet) {
            return base.toOffsetDateTime();
        }
        // Phần sau nửa đêm của khung đêm (VD 0:00–6:59) thuộc buổi tối của ngày hôm trước.
        LocalDate eveningDate = overMidnight && hour < quietEndHour ? base.toLocalDate().minusDays(1) : base.toLocalDate();
        return eveningDate.atTime(quietStartHour, 0).atZone(APP_ZONE).toOffsetDateTime();
    }

    private void processExerciseAssignments(OffsetDateTime now, OffsetDateTime cutoff, Predicate<OffsetDateTime> reminderDue) {
        List<ExerciseAssignment> dueSoon = exerciseAssignmentRepository
                .findByStatusAndDueAtBetweenAndParentReminderSentAtIsNull(ExerciseAssignment.Status.ACTIVE, now, cutoff).stream()
                .filter(a -> a.getSchoolClass().getStatus() != SchoolClass.Status.CANCELLED)
                .filter(a -> reminderDue.test(a.getDueAt()))
                .toList();
        for (ExerciseAssignment assignment : dueSoon) {
            List<Student> students = homeworkDeadlineSchedulerService.targetStudents(assignment.getSchoolClass(), assignment.getTargetStudentIds());
            for (Student s : students) {
                if (!homeworkProgressService.grammarPassed(assignment, s.getId())) {
                    notifyParents(s, assignment.getSchoolClass(), "BTVN \"" + assignment.getExercise().getTitle() + "\"", assignment.getDueAt(),
                            assignment.getId(), null);
                }
            }
            assignment.setParentReminderSentAt(now);
            exerciseAssignmentRepository.save(assignment);
        }
        if (!dueSoon.isEmpty()) {
            log.info("HomeworkDueSoonReminderSchedulerService: nhắc Phụ huynh {} lần giao Bài tập Ngữ pháp sắp hết hạn.", dueSoon.size());
        }
    }

    private void processReviewVideoAssignments(OffsetDateTime now, OffsetDateTime cutoff, Predicate<OffsetDateTime> reminderDue) {
        List<ReviewVideoAssignment> dueSoon = reviewVideoAssignmentRepository
                .findByStatusAndDueAtBetweenAndParentReminderSentAtIsNull(ReviewVideoAssignment.Status.ACTIVE, now, cutoff).stream()
                .filter(a -> a.getSchoolClass().getStatus() != SchoolClass.Status.CANCELLED)
                .filter(a -> reminderDue.test(a.getDueAt()))
                .toList();
        for (ReviewVideoAssignment assignment : dueSoon) {
            List<Student> students = homeworkDeadlineSchedulerService.targetStudents(assignment.getSchoolClass(), assignment.getTargetStudentIds());
            for (Student s : students) {
                if (!homeworkProgressService.videoPassed(assignment, s.getId(), homeworkAlertSettings.reflexPassThresholdPercent())) {
                    notifyParents(s, assignment.getSchoolClass(), "Video Ôn tập \"" + assignment.getReviewVideoSet().getTitle() + "\"", assignment.getDueAt(),
                            null, assignment.getId());
                }
            }
            assignment.setParentReminderSentAt(now);
            reviewVideoAssignmentRepository.save(assignment);
        }
        if (!dueSoon.isEmpty()) {
            log.info("HomeworkDueSoonReminderSchedulerService: nhắc Phụ huynh {} lần giao Video Ôn tập sắp hết hạn.", dueSoon.size());
        }
    }

    /**
     * exerciseAssignmentId/reviewVideoAssignmentId: đúng 1 trong 2 khác null (tuỳ kênh) — ghi vào metadata
     * dạng số cùng classId/studentId để NotificationService.toResponse() promote lên NotificationResponse,
     * Portal mở/cuộn tới đúng thẻ BTVN khi bấm thông báo (Plan link hoá thông báo, 2026-09-22).
     */
    private void notifyParents(Student student, SchoolClass schoolClass, String assignmentLabel, OffsetDateTime dueAt,
                               Long exerciseAssignmentId, Long reviewVideoAssignmentId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentName", student.getUser().getFullName());
        metadata.put("className", schoolClass.getName());
        metadata.put("assignmentLabel", assignmentLabel);
        metadata.put("dueAt", dueAt);
        metadata.put("studentId", student.getId());
        metadata.put("classId", schoolClass.getId());
        if (exerciseAssignmentId != null) {
            metadata.put("exerciseAssignmentId", exerciseAssignmentId);
        }
        if (reviewVideoAssignmentId != null) {
            metadata.put("reviewVideoAssignmentId", reviewVideoAssignmentId);
        }

        String dueAtLabel = dueAt.atZoneSameInstant(APP_ZONE).format(DUE_AT_FMT);
        String parentTitle = "Sắp tới hạn nộp " + assignmentLabel;
        String parentContent = "Con " + student.getUser().getFullName() + " (lớp " + schoolClass.getName() + ") chưa hoàn thành "
                + assignmentLabel + ", hạn nộp " + dueAtLabel + " — Phụ huynh nhắc con hoàn thành trước hạn nhé.";
        for (ParentStudent link : parentStudentRepository.findByStudentId(student.getId())) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.HOMEWORK_DUE_SOON_REMINDER,
                    parentTitle, parentContent, metadata, "STUDENT", student.getId(), Notification.Priority.NORMAL, null);
        }

        // Bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-16: trước đây chỉ nhắc Phụ huynh,
        // học sinh không thấy nhắc nhở nào trên Portal của chính mình trước khi hết hạn — gửi thêm
        // 1 bản cho tài khoản Portal của chính học sinh, cùng metadata/thời điểm với bản gửi Phụ huynh.
        String studentTitle = "Sắp tới hạn nộp " + assignmentLabel;
        String studentContent = "Bạn chưa hoàn thành " + assignmentLabel + " (lớp " + schoolClass.getName() + "), hạn nộp " + dueAtLabel
                + " — hoàn thành trước hạn nhé.";
        notificationService.notify(student.getUser().getId(), Notification.NotificationType.HOMEWORK_DUE_SOON_REMINDER,
                studentTitle, studentContent, metadata, "STUDENT", student.getId(), Notification.Priority.NORMAL, null);
    }
}
