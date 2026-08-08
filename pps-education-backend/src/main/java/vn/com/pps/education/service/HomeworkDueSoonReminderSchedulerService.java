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

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: nhắc Phụ
 * huynh trước hạn nộp BTVN ~12 tiếng
 * (`system_settings.homework_alert.reminder_before_due_hours`) để nhắc
 * con làm bài, mirror cấu trúc {@link HomeworkDeadlineSchedulerService}
 * (cùng cron 5 phút, cùng cách quét theo `due_at`, tái dùng thẳng
 * {@code targetStudents(...)} của service đó thay vì lặp lại logic
 * enrollment). Chỉ nhắc học sinh CHƯA đạt (tái dùng
 * {@link HomeworkProgressService#grammarPassed}/{@code videoPassed}) —
 * học sinh đã làm xong không cần nhắc.
 */
@Service
public class HomeworkDueSoonReminderSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(HomeworkDueSoonReminderSchedulerService.class);

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
        OffsetDateTime cutoff = now.plusHours(homeworkAlertSettings.reminderBeforeDueHours());
        processExerciseAssignments(now, cutoff);
        processReviewVideoAssignments(now, cutoff);
    }

    private void processExerciseAssignments(OffsetDateTime now, OffsetDateTime cutoff) {
        List<ExerciseAssignment> dueSoon = exerciseAssignmentRepository
                .findByStatusAndDueAtBetweenAndParentReminderSentAtIsNull(ExerciseAssignment.Status.ACTIVE, now, cutoff);
        for (ExerciseAssignment assignment : dueSoon) {
            List<Student> students = homeworkDeadlineSchedulerService.targetStudents(assignment.getSchoolClass(), assignment.getTargetStudentIds());
            for (Student s : students) {
                if (!homeworkProgressService.grammarPassed(assignment, s.getId())) {
                    notifyParents(s, assignment.getSchoolClass(), "BTVN \"" + assignment.getExercise().getTitle() + "\"", assignment.getDueAt());
                }
            }
            assignment.setParentReminderSentAt(now);
            exerciseAssignmentRepository.save(assignment);
        }
        if (!dueSoon.isEmpty()) {
            log.info("HomeworkDueSoonReminderSchedulerService: nhắc Phụ huynh {} lần giao Bài tập Ngữ pháp sắp hết hạn.", dueSoon.size());
        }
    }

    private void processReviewVideoAssignments(OffsetDateTime now, OffsetDateTime cutoff) {
        List<ReviewVideoAssignment> dueSoon = reviewVideoAssignmentRepository
                .findByStatusAndDueAtBetweenAndParentReminderSentAtIsNull(ReviewVideoAssignment.Status.ACTIVE, now, cutoff);
        for (ReviewVideoAssignment assignment : dueSoon) {
            List<Student> students = homeworkDeadlineSchedulerService.targetStudents(assignment.getSchoolClass(), assignment.getTargetStudentIds());
            for (Student s : students) {
                if (!homeworkProgressService.videoPassed(assignment, s.getId(), homeworkAlertSettings.reflexPassThresholdPercent())) {
                    notifyParents(s, assignment.getSchoolClass(), "Video Ôn tập \"" + assignment.getReviewVideoSet().getTitle() + "\"", assignment.getDueAt());
                }
            }
            assignment.setParentReminderSentAt(now);
            reviewVideoAssignmentRepository.save(assignment);
        }
        if (!dueSoon.isEmpty()) {
            log.info("HomeworkDueSoonReminderSchedulerService: nhắc Phụ huynh {} lần giao Video Ôn tập sắp hết hạn.", dueSoon.size());
        }
    }

    private void notifyParents(Student student, SchoolClass schoolClass, String assignmentLabel, OffsetDateTime dueAt) {
        String title = "Sắp tới hạn nộp " + assignmentLabel;
        String content = "Con " + student.getUser().getFullName() + " (lớp " + schoolClass.getName() + ") chưa hoàn thành "
                + assignmentLabel + ", hạn nộp " + dueAt + " — nhắc con hoàn thành trước hạn nhé.";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentName", student.getUser().getFullName());
        metadata.put("className", schoolClass.getName());
        for (ParentStudent link : parentStudentRepository.findByStudentId(student.getId())) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.HOMEWORK_DUE_SOON_REMINDER,
                    title, content, metadata, "STUDENT", student.getId(), Notification.Priority.NORMAL, null);
        }
    }
}
