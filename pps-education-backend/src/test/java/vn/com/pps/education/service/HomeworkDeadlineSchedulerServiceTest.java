package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.ExamResponse;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.SubmitConnectionAnswersRequest;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.NotificationRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04: khi hết hạn nộp BTVN (Ngữ pháp
 * online / Video Ôn tập), Giáo viên đã giao bài nhận thông báo % học sinh đã làm bài của cả lớp +
 * % hoàn thành từng em. Không gắn UC cụ thể (tính năng mới, chưa có mã UC) — đặt tên test theo
 * pattern "MainFlow/A" mô tả luồng thay vì mã UC.
 */
@Transactional
class HomeworkDeadlineSchedulerServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private HomeworkDeadlineSchedulerService homeworkDeadlineSchedulerService;
    @Autowired
    private ExerciseService exerciseService;
    @Autowired
    private ExerciseAttemptService exerciseAttemptService;
    @Autowired
    private ExamService examService;
    @Autowired
    private QuestionBankService questionBankService;
    @Autowired
    private ExamQuestionService examQuestionService;
    @Autowired
    private ReviewVideoService reviewVideoService;
    @Autowired
    private ClassService classService;
    @Autowired
    private CurriculumService curriculumService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ExerciseAssignmentRepository exerciseAssignmentRepository;
    @Autowired
    private ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private User headAcademic;
    private User teacher;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;
    private QuestionBankResponse bank;
    private ExamResponse defaultExam;
    private User studentDoneUser;
    private User studentNotDoneUser;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic.hw");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher.hw");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        bank = questionBankService.createBank(
                new CreateQuestionBankRequest(bankCode(), "Ngân hàng", activeCurriculum.id(), null, "A1"), teacher.getId());
        defaultExam = examService.createExam(
                new CreateExamRequest(examCode(), "Đề mặc định", activeCurriculum.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());

        studentDoneUser = enrollNewStudent("student.hw.done");
        studentNotDoneUser = enrollNewStudent("student.hw.notdone");
    }

    @Test
    void runDeadlineScan_MainFlow_notifiesTeacherWithClassCompletionRateForExercise() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("1"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        // Giao với hạn nộp CÒN Ở TƯƠNG LAI để học sinh nộp bài được bình thường (tránh
        // SubmissionPastDeadlineException) — sau đó dời hạn về quá khứ để giả lập "vừa hết hạn".
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit fixture vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        ExerciseAssignment assignment = exerciseService.deliverToClass(
                exercise.id(), schoolClass.id(), OffsetDateTime.now().plusHours(1), teacher.getId());

        ExerciseAttemptResponse attempt = exerciseAttemptService.startAttempt(exercise.id(), assignment.getId(), studentDoneUser.getId());
        answerCorrectly(attempt.id(), mc);
        exerciseAttemptService.submitAttempt(attempt.id(), studentDoneUser.getId());
        // studentNotDoneUser không làm gì -> "Chưa làm bài".

        assignment.setDueAt(OffsetDateTime.now().minusMinutes(1));
        exerciseAssignmentRepository.save(assignment);
        long before = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(teacher.getId(), PageRequest.of(0, 10))
                .getTotalElements();

        homeworkDeadlineSchedulerService.runDeadlineScan();

        var notifications = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(teacher.getId(), PageRequest.of(0, 10));
        assertThat(notifications.getTotalElements()).isEqualTo(before + 1);
        Notification created = notifications.getContent().get(0);
        assertThat(created.getNotificationType()).isEqualTo(Notification.NotificationType.HOMEWORK_DEADLINE_SUMMARY);
        assertThat(created.getContent()).contains("1/2 học sinh (50%)");
        assertThat(created.getContent()).contains("Chưa làm bài");

        ExerciseAssignment reloaded = exerciseAssignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(reloaded.getTeacherNotifiedAt()).isNotNull();
    }

    @Test
    void runDeadlineScan_A_doesNotDuplicateNotificationOnRescan() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("1"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit fixture vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        ExerciseAssignment assignment = exerciseService.deliverToClass(
                exercise.id(), schoolClass.id(), OffsetDateTime.now().minusMinutes(1), teacher.getId());

        homeworkDeadlineSchedulerService.runDeadlineScan();
        long afterFirst = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(teacher.getId(), PageRequest.of(0, 10))
                .getTotalElements();

        homeworkDeadlineSchedulerService.runDeadlineScan();
        long afterSecond = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(teacher.getId(), PageRequest.of(0, 10))
                .getTotalElements();

        assertThat(afterSecond).isEqualTo(afterFirst);
        assertThat(exerciseAssignmentRepository.findById(assignment.getId()).orElseThrow().getTeacherNotifiedAt()).isNotNull();
    }

    @Test
    void runDeadlineScan_A_skipsAssignmentNotYetDue() {
        QuestionResponse mc = createMcQuestion();
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("1"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit fixture vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        exerciseService.deliverToClass(exercise.id(), schoolClass.id(), OffsetDateTime.now().plusDays(1), teacher.getId());
        long before = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(teacher.getId(), PageRequest.of(0, 10))
                .getTotalElements();

        homeworkDeadlineSchedulerService.runDeadlineScan();

        long after = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(teacher.getId(), PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void runDeadlineScan_MainFlow_notifiesTeacherWithClassCompletionRateForReviewVideo() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bài 1: Video TKN", "CONNECTION", schoolClass.curriculumId(), "VIETNAMESE", null, 1),
                teacher.getId());
        // V98 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): curriculum trên "bộ" giờ
        // CHỈ dùng lọc/tìm kiếm — phải assignToClass tường minh trước khi deliverToClass mới thành công.
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        ReviewVideoResponse video = reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/x.mp4",
                        1_000_000L, 100, 1, null, null),
                teacher.getId());
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit set/video vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        ReviewVideoAssignment assignment = reviewVideoService.deliverToClass(
                set.id(), schoolClass.id(), OffsetDateTime.now().plusHours(1), teacher.getId());

        Long sessionId = reviewVideoService.startWatchSession(video.id(), assignment.getId(), studentDoneUser.getId()).sessionId();
        reviewVideoService.reportProgress(video.id(), new ReportVideoProgressRequest(sessionId, 100), studentDoneUser.getId());
        // CONNECTION (V83/V93/V101): xem đạt ngưỡng chỉ làm session "qualified" — phải nộp đủ câu
        // hỏi (rỗng ở đây, video chưa thêm câu hỏi) mới tính "đạt" 1 lượt (xem ghi chú tương tự ở
        // ReviewVideoServiceTest).
        reviewVideoService.submitConnectionAnswers(sessionId, new SubmitConnectionAnswersRequest(List.of()), studentDoneUser.getId());
        // studentNotDoneUser không xem gì -> 0%.

        assignment.setDueAt(OffsetDateTime.now().minusMinutes(1));
        reviewVideoAssignmentRepository.save(assignment);

        homeworkDeadlineSchedulerService.runDeadlineScan();

        var notifications = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(teacher.getId(), PageRequest.of(0, 10));
        Notification created = notifications.getContent().get(0);
        assertThat(created.getNotificationType()).isEqualTo(Notification.NotificationType.HOMEWORK_DEADLINE_SUMMARY);
        assertThat(created.getContent()).contains("1/2 học sinh (50%)");
        assertThat(reviewVideoAssignmentRepository.findById(assignment.getId()).orElseThrow().getTeacherNotifiedAt()).isNotNull();
    }

    private User enrollNewStudent(String username) {
        User studentUser = newUser(username);
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-HW-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        return studentUser;
    }

    private QuestionResponse createMcQuestion() {
        // V75 (Kho đề): mỗi Exam tự sinh 1 QuestionBank nội bộ riêng, không nhận câu hỏi qua
        // QuestionBankService#createQuestion (chỉ dành cho bank "legacy" độc lập) — phải qua
        // ExamQuestionService#createQuestion (tự resolve bank nội bộ theo examId).
        return examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
    }

    private void answerCorrectly(Long attemptId, QuestionResponse question) {
        Long correctChoiceId = question.choices().stream().filter(c -> c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attemptId,
                new SaveAnswerRequest(question.id(), null, List.of(correctChoiceId), null, null), studentDoneUser.getId());
    }

    private String curriculumCode() {
        return "CUR-HW-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-HW-" + SEQ.incrementAndGet();
    }

    private String bankCode() {
        return "QB-HW-" + SEQ.incrementAndGet();
    }

    private String exerciseCode() {
        return "EX-HW-" + SEQ.incrementAndGet();
    }

    private String examCode() {
        return "KD-HW-" + SEQ.incrementAndGet();
    }

    private String setCode() {
        return "RVS-HW-" + SEQ.incrementAndGet();
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
        s.setCode("SITE-HW-" + SEQ.incrementAndGet());
        s.setName("Test Site");
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
