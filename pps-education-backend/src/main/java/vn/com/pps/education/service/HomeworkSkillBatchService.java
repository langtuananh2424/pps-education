package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Exam;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.HomeworkSkillBatch;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.HomeworkSkillGroupResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ExamRepository;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.ExerciseQuestionRepository;
import vn.com.pps.education.repository.ExerciseRepository;
import vn.com.pps.education.repository.HomeworkSkillBatchRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — "Lô giao BTVN theo kỹ năng":
 * gom N Bài (Exercise) cùng (Đề, kỹ năng) thành 1 lần giao ở Nhận xét học viên (UC-21), thay cho cơ chế
 * gộp câu hỏi cũ (V145, đã bỏ). Mỗi Bài trong lô vẫn là 1 {@link ExerciseAssignment} THẬT, tạo qua
 * {@link ExerciseService#deliverToClass} nguyên vẹn — Service này chỉ là lớp điều phối MỎNG ở trên,
 * KHÔNG đụng gì tới nội dung câu hỏi/chấm điểm. Tách riêng khỏi ExerciseService theo SRP (xem
 * .claude/rules/solid.md — 2 lý do thay đổi khác nhau: soạn Bài vs điều phối giao theo lô).
 */
@Service
public class HomeworkSkillBatchService {

    private final HomeworkSkillBatchRepository homeworkSkillBatchRepository;
    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final ExamRepository examRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserRepository userRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final NotificationService notificationService;
    private final ExerciseService exerciseService;

    public HomeworkSkillBatchService(HomeworkSkillBatchRepository homeworkSkillBatchRepository,
                                      ExerciseAssignmentRepository exerciseAssignmentRepository,
                                      ExerciseRepository exerciseRepository,
                                      ExerciseQuestionRepository exerciseQuestionRepository,
                                      ExamRepository examRepository,
                                      SchoolClassRepository schoolClassRepository,
                                      UserRepository userRepository,
                                      ClassEnrollmentRepository classEnrollmentRepository,
                                      NotificationService notificationService,
                                      ExerciseService exerciseService) {
        this.homeworkSkillBatchRepository = homeworkSkillBatchRepository;
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.examRepository = examRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userRepository = userRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.notificationService = notificationService;
        this.exerciseService = exerciseService;
    }

    /**
     * Giao TOÀN BỘ Bài PUBLISHED cùng (examId, skillCategory) cho 1 lớp — gọi từ StudentCommentService
     * khi Giáo viên chọn kênh kỹ năng làm "BTVN buổi sau" ở UC-21. Mirror {@code deliverToClass}: gọi
     * lại chính method đó cho TỪNG Bài (đã tự dedupe/reuse bản giao ACTIVE cùng buổi nguồn) — không lặp
     * logic due-date/conflict, chỉ thêm bước gắn {@code homeworkBatchId} sau khi có assignment.
     *
     * V150 sửa lỗi thật (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25, xem ảnh chụp
     * Portal học sinh) — gọi {@code deliverToClass} với {@code notify=false} cho TỪNG Bài (khác trước:
     * mỗi Bài tự gửi 1 thông báo, học sinh nhận N thông báo gần như giống hệt nhau cho cùng 1 Lô), rồi
     * tự gửi đúng 1 thông báo GỘP cho cả Lô sau khi giao xong toàn bộ N Bài.
     */
    @Transactional
    public HomeworkSkillBatch assignBatchToClass(Long examId, Exercise.SkillCategory skillCategory, Long classId,
                                                   OffsetDateTime dueAt, Long actorUserId, ClassSession sourceClassSession) {
        List<Exercise> sources = exerciseRepository.findByExamIdAndSkillCategoryAndStatus(
                examId, skillCategory, Exercise.Status.PUBLISHED);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lesson này chưa có Bài nào thuộc kỹ năng " + skillCategory + " đã Publish.");
        }

        Exam exam = examOrThrow(examId);
        SchoolClass schoolClass = classOrThrow(classId);
        User actor = userOrThrow(actorUserId);

        HomeworkSkillBatch batch = new HomeworkSkillBatch();
        batch.setExam(exam);
        batch.setSkillCategory(skillCategory);
        batch.setSchoolClass(schoolClass);
        batch.setAssignedBy(actor);
        batch.setSourceClassSession(sourceClassSession);
        batch = homeworkSkillBatchRepository.save(batch);

        ExerciseAssignment representativeAssignment = null;
        for (Exercise source : sources) {
            ExerciseAssignment assignment = exerciseService.deliverToClass(
                    source.getId(), classId, dueAt, actorUserId, sourceClassSession, false);
            assignment.setHomeworkBatch(batch);
            exerciseAssignmentRepository.save(assignment);
            if (representativeAssignment == null) {
                representativeAssignment = assignment;
            }
        }
        notifyAssignedStudents(schoolClass, exam, skillCategory, sources.size(), representativeAssignment);
        return batch;
    }

    /** Mirror {@code ExerciseService#notifyAssignedStudents} — 1 thông báo GỘP/Lô thay vì 1 thông báo/Bài (xem Javadoc assignBatchToClass). entityId trỏ về 1 Bài đại diện trong Lô — FE (NotificationBell) đã tự tra ngược đúng thẻ gộp qua homeworkBatchId của Bài đó, xem AssignmentsTab.tsx. */
    private void notifyAssignedStudents(SchoolClass schoolClass, Exam exam, Exercise.SkillCategory skillCategory,
                                         int exerciseCount, ExerciseAssignment representativeAssignment) {
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(schoolClass.getId(), ClassEnrollment.Status.ACTIVE);
        String title = "Bài kiểm tra mới được giao";
        String content = "Đề \"" + exam.getTitle() + " – " + skillCategoryLabel(skillCategory) + " (" + exerciseCount + " bài)\""
                + " đã được giao cho lớp " + schoolClass.getName() + ".";
        for (ClassEnrollment enrollment : enrollments) {
            notificationService.notify(enrollment.getStudent().getUser().getId(),
                    Notification.NotificationType.OTHER, title, content,
                    null, "EXERCISE_ASSIGNMENT", representativeAssignment.getId(),
                    Notification.Priority.NORMAL, null);
        }
    }

    /** Nhãn tiếng Việt ngắn cho 1 skillCategory — mirror ExerciseReportService/StudentCommentService (đúng tiền lệ mirror-không-generalize của codebase). */
    private static String skillCategoryLabel(Exercise.SkillCategory skillCategory) {
        return switch (skillCategory) {
            case READING -> "Reading";
            case WRITING -> "Writing";
            case VOCAB_GRAMMAR -> "Ngữ pháp";
            case LISTENING -> "Nghe";
        };
    }

    /** Huỷ toàn bộ N bản giao thuộc 1 lô (VD Giáo viên đổi lựa chọn kênh kỹ năng khi comment còn DRAFT) — mirror {@code ExerciseService#cancelAssignment}. */
    @Transactional
    public void cancelBatch(HomeworkSkillBatch batch) {
        exerciseAssignmentRepository.findByHomeworkBatchId(batch.getId())
                .forEach(exerciseService::cancelAssignment);
    }

    /**
     * Danh sách nhóm kỹ năng khả dụng làm nguồn "BTVN buổi sau" cho 1 lớp, đúng 1 skillCategory (mỗi
     * kênh UC-21 cố định 1 skillCategory) — nguồn cho dropdown, thay thế danh sách Exercise lẻ/bản gộp
     * cũ. Mỗi nhóm = 1 Lesson (Exam) có >=1 Bài PUBLISHED cùng skillCategory, thuộc Đề đã gán cho lớp.
     */
    @Transactional(readOnly = true)
    public List<HomeworkSkillGroupResponse> listSkillGroupsForClass(Long classId, Exercise.SkillCategory skillCategory, Long actorUserId) {
        Map<Long, List<vn.com.pps.education.dto.ExerciseResponse>> byExam = exerciseService
                .listPublishedForClass(classId, actorUserId).stream()
                .filter(e -> skillCategory.name().equals(e.skillCategory()))
                .collect(Collectors.groupingBy(vn.com.pps.education.dto.ExerciseResponse::examId));
        return byExam.values().stream()
                .map(list -> new HomeworkSkillGroupResponse(
                        list.get(0).examId(), list.get(0).examCode(), list.get(0).examTitle(), list.get(0).examTeacherType(), skillCategory.name(),
                        list.size(),
                        list.stream().mapToLong(e -> exerciseQuestionRepository.countByExerciseId(e.id())).sum(),
                        list.get(0).unitTitle(), list.get(0).subTopicTitle()))
                .toList();
    }

    private Exam examOrThrow(Long id) {
        return examRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.homeworkSkillBatch.examNotFound",
                        new Object[]{id}, "Không tìm thấy Đề id=" + id));
    }

    private SchoolClass classOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.homeworkSkillBatch.classNotFound",
                        new Object[]{id}, "Không tìm thấy lớp học id=" + id));
    }

    private User userOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.homeworkSkillBatch.userNotFound",
                        new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }
}
