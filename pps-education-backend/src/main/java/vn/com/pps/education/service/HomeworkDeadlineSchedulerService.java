package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentHomeworkAlertState;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04: khi hết hạn nộp BTVN (Bài tập Ngữ
 * pháp online / Video Ôn tập, giao qua UC-21) tự động báo Giáo viên đã giao bài % học sinh đã làm
 * bài của cả lớp + % hoàn thành từng em (Portal + Email — dùng chung
 * NotificationService#dispatchByPreference, giống ATTENDANCE_ABSENT).
 *
 * Quét mỗi 5 phút thay vì lập lịch chính xác theo từng due_at — đơn giản/bền hơn (không mất lịch
 * khi app restart giữa chừng), đủ "gần thời gian thực" theo đúng yêu cầu.
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: CÙNG lượt quét này thu thập luôn
 * đạt/không đạt từng học sinh (2 kênh) để báo Phụ huynh cảnh báo thiếu BTVN theo lộ trình — xem
 * {@link HomeworkAlertTrackingService}. Vì 2 kênh luôn cùng hạn nộp (đã xác nhận), gom kết quả cả
 * 2 vòng quét vào 1 map theo (học sinh, lớp) rồi đánh giá 1 LẦN ở cuối {@link #runDeadlineScan()}
 * để có thể gộp thành 1 thông báo duy nhất khi cả 2 kênh cùng chạm mốc.
 */
@Service
public class HomeworkDeadlineSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(HomeworkDeadlineSchedulerService.class);
    private static final String NOT_DONE_LABEL = "Chưa làm bài";

    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final HomeworkProgressService homeworkProgressService;
    private final NotificationService notificationService;
    private final HomeworkAlertTrackingService homeworkAlertTrackingService;
    private final HomeworkAlertSettings homeworkAlertSettings;

    public HomeworkDeadlineSchedulerService(ExerciseAssignmentRepository exerciseAssignmentRepository,
                                             ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                                             ClassEnrollmentRepository classEnrollmentRepository,
                                             HomeworkProgressService homeworkProgressService,
                                             NotificationService notificationService,
                                             HomeworkAlertTrackingService homeworkAlertTrackingService,
                                             HomeworkAlertSettings homeworkAlertSettings) {
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.homeworkProgressService = homeworkProgressService;
        this.notificationService = notificationService;
        this.homeworkAlertTrackingService = homeworkAlertTrackingService;
        this.homeworkAlertSettings = homeworkAlertSettings;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void runDeadlineScan() {
        OffsetDateTime now = OffsetDateTime.now();
        Map<StudentClassKey, MissAccumulator> missByStudentClass = new LinkedHashMap<>();
        processExerciseAssignments(now, missByStudentClass);
        processReviewVideoAssignments(now, missByStudentClass);

        if (homeworkAlertSettings.isEnabled()) {
            for (MissAccumulator acc : missByStudentClass.values()) {
                homeworkAlertTrackingService.evaluateAndNotify(acc.student, acc.schoolClass, acc.results);
            }
        }
    }

    private void processExerciseAssignments(OffsetDateTime now, Map<StudentClassKey, MissAccumulator> missByStudentClass) {
        List<ExerciseAssignment> due = exerciseAssignmentRepository
                .findByStatusAndDueAtLessThanEqualAndTeacherNotifiedAtIsNull(ExerciseAssignment.Status.ACTIVE, now);
        for (ExerciseAssignment assignment : due) {
            List<Student> students = targetStudents(assignment.getSchoolClass(), assignment.getTargetStudentIds());
            if (!students.isEmpty()) {
                List<StudentProgress> progress = students.stream()
                        .map(s -> new StudentProgress(s, homeworkProgressService.grammarProgressLabel(assignment, s.getId())))
                        .toList();
                sendSummary(assignment.getAssignedBy().getId(), assignment.getSchoolClass(),
                        "BTVN \"" + assignment.getExercise().getTitle() + "\"", progress,
                        "EXERCISE_ASSIGNMENT", assignment.getId());
                for (Student s : students) {
                    boolean passed = homeworkProgressService.grammarPassed(assignment, s.getId());
                    accumulate(missByStudentClass, s, assignment.getSchoolClass(), StudentHomeworkAlertState.Channel.GRAMMAR, passed);
                }
            }
            assignment.setTeacherNotifiedAt(now);
            exerciseAssignmentRepository.save(assignment);
        }
        if (!due.isEmpty()) {
            log.info("HomeworkDeadlineSchedulerService: báo GV {} lần giao Bài tập Ngữ pháp vừa hết hạn.", due.size());
        }
    }

    private void processReviewVideoAssignments(OffsetDateTime now, Map<StudentClassKey, MissAccumulator> missByStudentClass) {
        List<ReviewVideoAssignment> due = reviewVideoAssignmentRepository
                .findByStatusAndDueAtLessThanEqualAndTeacherNotifiedAtIsNull(ReviewVideoAssignment.Status.ACTIVE, now);
        for (ReviewVideoAssignment assignment : due) {
            List<Student> students = targetStudents(assignment.getSchoolClass(), assignment.getTargetStudentIds());
            if (!students.isEmpty()) {
                List<StudentProgress> progress = students.stream()
                        .map(s -> new StudentProgress(s, homeworkProgressService.videoProgressLabel(assignment, s.getId())))
                        .toList();
                sendSummary(assignment.getAssignedBy().getId(), assignment.getSchoolClass(),
                        "Video Ôn tập \"" + assignment.getReviewVideoSet().getTitle() + "\"", progress,
                        "REVIEW_VIDEO_ASSIGNMENT", assignment.getId());
                for (Student s : students) {
                    boolean passed = homeworkProgressService.videoPassed(assignment, s.getId(), homeworkAlertSettings.reflexPassThresholdPercent());
                    accumulate(missByStudentClass, s, assignment.getSchoolClass(), StudentHomeworkAlertState.Channel.VIDEO, passed);
                }
            }
            assignment.setTeacherNotifiedAt(now);
            reviewVideoAssignmentRepository.save(assignment);
        }
        if (!due.isEmpty()) {
            log.info("HomeworkDeadlineSchedulerService: báo GV {} lần giao Video Ôn tập vừa hết hạn.", due.size());
        }
    }

    private void accumulate(Map<StudentClassKey, MissAccumulator> missByStudentClass, Student student, SchoolClass schoolClass,
                             StudentHomeworkAlertState.Channel channel, boolean passed) {
        StudentClassKey key = new StudentClassKey(student.getId(), schoolClass.getId());
        MissAccumulator acc = missByStudentClass.computeIfAbsent(key, k -> new MissAccumulator(student, schoolClass));
        acc.results.add(new HomeworkAlertTrackingService.ChannelMissResult(channel, passed));
    }

    private record StudentClassKey(Long studentId, Long schoolClassId) {}

    private static final class MissAccumulator {
        private final Student student;
        private final SchoolClass schoolClass;
        private final List<HomeworkAlertTrackingService.ChannelMissResult> results = new ArrayList<>();

        private MissAccumulator(Student student, SchoolClass schoolClass) {
            this.student = student;
            this.schoolClass = schoolClass;
        }
    }

    /** targetStudentIds NULL/rỗng = giao cả lớp (SDD) — lấy toàn bộ enrollment ACTIVE hiện tại. */
    List<Student> targetStudents(SchoolClass schoolClass, List<Long> targetStudentIds) {
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(schoolClass.getId(), ClassEnrollment.Status.ACTIVE);
        if (targetStudentIds == null || targetStudentIds.isEmpty()) {
            return enrollments.stream().map(ClassEnrollment::getStudent).toList();
        }
        Set<Long> targetSet = Set.copyOf(targetStudentIds);
        return enrollments.stream().map(ClassEnrollment::getStudent)
                .filter(s -> targetSet.contains(s.getId())).toList();
    }

    private void sendSummary(Long teacherUserId, SchoolClass schoolClass, String assignmentLabel,
                              List<StudentProgress> progress, String entityType, Long entityId) {
        long completedCount = progress.stream().filter(StudentProgress::completed).count();
        int total = progress.size();
        int ratePercent = Math.round(completedCount * 100f / total);

        String title = "Hết hạn " + assignmentLabel + " — lớp " + schoolClass.getName();
        String detail = progress.stream()
                .sorted((a, b) -> a.student().getUser().getFullName().compareToIgnoreCase(b.student().getUser().getFullName()))
                .map(p -> "- " + p.student().getUser().getFullName() + ": " + (p.label() == null ? NOT_DONE_LABEL : p.label()))
                .collect(Collectors.joining("\n"));
        String content = "Tỷ lệ hoàn thành: %d/%d học sinh (%d%%).\n\nChi tiết từng em:\n%s"
                .formatted(completedCount, total, ratePercent, detail);

        notificationService.notify(teacherUserId, Notification.NotificationType.HOMEWORK_DEADLINE_SUMMARY,
                title, content, null, entityType, entityId, Notification.Priority.NORMAL, null);
    }

    /**
     * "Đã làm bài" = có nộp/có tiến độ, KHÔNG yêu cầu hoàn thành 100% — khớp đúng ví dụ nghiệp vụ
     * người dùng đưa ra ("10 em 7 em làm thì 70%"). Ngữ pháp: khác "Chưa làm bài" (kể cả đang chờ
     * chấm vẫn tính là đã làm). Video: label luôn dạng "NN%" kể cả 0% (chưa xem/chưa trả lời gì) —
     * phải tách riêng, coi 0% là CHƯA làm, > 0% là đã làm.
     */
    private record StudentProgress(Student student, String label) {
        boolean completed() {
            if (label == null || NOT_DONE_LABEL.equals(label)) {
                return false;
            }
            if (label.endsWith("%")) {
                try {
                    return Integer.parseInt(label.substring(0, label.length() - 1)) > 0;
                } catch (NumberFormatException e) {
                    return true;
                }
            }
            return true;
        }
    }
}
