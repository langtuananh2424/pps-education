package vn.com.pps.education.service.integrity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttemptIntegrityEvent.AttemptType;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateParentRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.ExamResponse;
import vn.com.pps.education.dto.ExerciseAttemptResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.IntegrityEventBatchResponse;
import vn.com.pps.education.dto.IntegritySummaryResponse;
import vn.com.pps.education.dto.LinkParentRequest;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.RecordIntegrityEventsRequest;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.ReviewVideoSubmissionResponse;
import vn.com.pps.education.dto.SubmitReviewVideoAudioRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.NotificationRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.service.ClassService;
import vn.com.pps.education.service.CurriculumService;
import vn.com.pps.education.service.ExamQuestionService;
import vn.com.pps.education.service.ExamService;
import vn.com.pps.education.service.ExerciseAttemptService;
import vn.com.pps.education.service.ExerciseService;
import vn.com.pps.education.service.QuestionBankService;
import vn.com.pps.education.service.ReviewVideoService;
import vn.com.pps.education.service.StudentService;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — giám sát
 * học sinh thoát ra ngoài khi làm bài (UC-24/UC-27/UC-23b). Main Flow, A
 * (lọc sự kiện dưới ngưỡng), A (chặn học sinh khác gửi hộ), A (báo đúng 1
 * lần khi vượt ngưỡng, không báo trùng). Xem docs/uc/phan-he-07-lms-portal.md.
 */
@Transactional
class AttemptIntegrityServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private AttemptIntegrityService attemptIntegrityService;

    @Autowired
    private ExerciseAttemptService exerciseAttemptService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamQuestionService examQuestionService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private ReviewVideoService reviewVideoService;

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
    private NotificationRepository notificationRepository;

    private User headAcademic;
    private User teacher;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;
    private QuestionBankResponse bank;
    private User studentUser;
    private Student student;
    private ExamResponse defaultExam;
    private User parentUser;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        bank = questionBankService.createBank(
                new CreateQuestionBankRequest(bankCode(), "Ngân hàng", activeCurriculum.id(), null, "A1"), teacher.getId());

        studentUser = newUser("student");
        student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-INT-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        var parent = studentService.createParent(
                new CreateParentRequest(null, new vn.com.pps.education.dto.CreateUserRequest(
                        "parent." + SEQ.incrementAndGet(), "parent" + SEQ.incrementAndGet() + "@pps.edu.vn",
                        "Phụ huynh Test", null, null), null, null, null, null, null),
                headAcademic.getId());
        studentService.linkParent(student.getId(), new LinkParentRequest(parent.id(), "FATHER", true, true, null));
        parentUser = userRepository.findById(parent.userId()).orElseThrow();

        defaultExam = examService.createExam(
                new CreateExamRequest(examCode(), "Đề mặc định", activeCurriculum.id(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
    }

    @Test
    void recordEvents_MainFlow_savesEventsUnderThresholdWithoutNotifying() {
        ExerciseAttemptResponse attempt = startAssignedAttempt();

        IntegrityEventBatchResponse result = attemptIntegrityService.recordEvents(AttemptType.EXERCISE, attempt.id(),
                oneEvent(10), studentUser.getId());

        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.totalViolationCount()).isEqualTo(1);
        assertThat(result.notifiedByThisBatch()).isFalse();
    }

    /** A: sự kiện ngắn hơn ngưỡng min_violation_duration_seconds (mặc định 3s) bị lọc bỏ, không lưu. */
    @Test
    void recordEvents_A_filtersOutEventsShorterThanMinimumDuration() {
        ExerciseAttemptResponse attempt = startAssignedAttempt();

        IntegrityEventBatchResponse result = attemptIntegrityService.recordEvents(AttemptType.EXERCISE, attempt.id(),
                oneEvent(1), studentUser.getId());

        assertThat(result.savedCount()).isEqualTo(0);
        assertThat(result.totalViolationCount()).isEqualTo(0);
    }

    /** A: vượt ngưỡng số lần (mặc định 3) -- báo phụ huynh + giáo viên đúng 1 lần, lô tiếp theo không báo lại. */
    @Test
    void recordEvents_A_notifiesParentAndTeacherExactlyOnceWhenCountThresholdCrossed() {
        ExerciseAttemptResponse attempt = startAssignedAttempt();

        attemptIntegrityService.recordEvents(AttemptType.EXERCISE, attempt.id(), oneEvent(10), studentUser.getId());
        attemptIntegrityService.recordEvents(AttemptType.EXERCISE, attempt.id(), oneEvent(10), studentUser.getId());
        IntegrityEventBatchResponse thirdBatch = attemptIntegrityService.recordEvents(AttemptType.EXERCISE, attempt.id(),
                oneEvent(10), studentUser.getId());

        assertThat(thirdBatch.notifiedByThisBatch()).isTrue();
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(parentUser.getId(), PageRequest.of(0, 10)))
                .as("phụ huynh nhận đúng 1 thông báo").hasSize(1);
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(teacher.getId(), PageRequest.of(0, 10)))
                .as("giáo viên phụ trách lớp cũng nhận thông báo").hasSize(1);

        // Lô thứ 4 vẫn vượt ngưỡng nhưng đã báo rồi -- không báo trùng.
        IntegrityEventBatchResponse fourthBatch = attemptIntegrityService.recordEvents(AttemptType.EXERCISE, attempt.id(),
                oneEvent(10), studentUser.getId());
        assertThat(fourthBatch.notifiedByThisBatch()).isFalse();
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(parentUser.getId(), PageRequest.of(0, 10))).hasSize(1);
    }

    /** A: học sinh khác không phải chủ bài làm gửi sự kiện hộ bị từ chối. */
    @Test
    void recordEvents_A_rejectsWhenActorDoesNotOwnAttempt() {
        ExerciseAttemptResponse attempt = startAssignedAttempt();
        User outsider = newUser("outsider.student");
        Student outsiderStudent = new Student();
        outsiderStudent.setUser(outsider);
        outsiderStudent.setStudentCode("HS-OUT-" + SEQ.incrementAndGet());
        outsiderStudent.setDateOfBirth(LocalDate.of(2012, 5, 1));
        outsiderStudent.setEnrollmentDate(LocalDate.now());
        studentRepository.save(outsiderStudent);

        assertThatThrownBy(() -> attemptIntegrityService.recordEvents(AttemptType.EXERCISE, attempt.id(), oneEvent(10), outsider.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSummary_returnsAggregatedViolationDataForTeacher() {
        ExerciseAttemptResponse attempt = startAssignedAttempt();
        attemptIntegrityService.recordEvents(AttemptType.EXERCISE, attempt.id(), oneEvent(10), studentUser.getId());
        attemptIntegrityService.recordEvents(AttemptType.EXERCISE, attempt.id(), oneEvent(20), studentUser.getId());

        IntegritySummaryResponse summary = attemptIntegrityService.getSummary(AttemptType.EXERCISE, attempt.id());

        assertThat(summary.violationCount()).isEqualTo(2);
        assertThat(summary.violationTotalDurationSeconds()).isEqualTo(30);
        assertThat(summary.parentAndTeacherNotified()).isFalse();
    }

    /**
     * UC-23b không có "phiên bắt đầu ghi âm" ở backend -- sự kiện chỉ lưu
     * khi ĐI KÈM 1 lần nộp audio thành công (submitQuestionAudio), không
     * có endpoint ghi sự kiện độc lập cho nhánh này (xem Javadoc
     * ReviewVideoQuestionIntegrityContextResolver).
     */
    @Test
    void recordEvents_boSung_reviewVideoQuestionEventsPersistOnlyAlongsideSuccessfulSubmission() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video phản xạ", "REFLEX", schoolClass.curriculumId(), "VIETNAMESE", null, 1),
                teacher.getId());
        // V98 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): curriculum trên "bộ" giờ
        // CHỈ dùng lọc/tìm kiếm — phải assignToClass tường minh trước khi deliverToClass mới thành công.
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit set vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        reviewVideoService.deliverToClass(set.id(), schoolClass.id(), null, teacher.getId());
        ReviewVideoResponse video = reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_AUDIO", "Audio", "https://media.pps.edu.vn/lms/review-videos/audio/x.mp3",
                        1_000_000L, 100, 1, null, null),
                teacher.getId());
        ReviewVideoQuestionResponse question = reviewVideoService.addQuestion(video.id(),
                new vn.com.pps.education.dto.AddReviewVideoQuestionRequest(53, null, 15, null, null), teacher.getId());

        ReviewVideoSubmissionResponse submission = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/answer.mp3", oneEvent(10)),
                studentUser.getId());

        IntegritySummaryResponse summary = attemptIntegrityService.getSummary(AttemptType.REVIEW_VIDEO_QUESTION, submission.id());
        assertThat(summary.violationCount()).isEqualTo(1);
    }

    // ===================== Helpers =====================

    private ExerciseAttemptResponse startAssignedAttempt() {
        // V75 (Kho đề): mỗi Exam tự sinh 1 QuestionBank nội bộ riêng, không nhận câu hỏi qua
        // QuestionBankService#createQuestion (chỉ dành cho bank "legacy" độc lập, xem
        // QuestionBankService#getLegacyBankOrThrow) — phải qua ExamQuestionService#createQuestion
        // (tự resolve bank nội bộ theo examId), không dùng "bank" ở setUp (không liên kết defaultExam).
        QuestionResponse mc = examQuestionService.createQuestion(defaultExam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY",
                        "She ___ to school.", null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Kiểm tra", defaultExam.id(), null, "ASSIGNED",
                        new BigDecimal("1"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(mc.id(), 1, new BigDecimal("1.0")), teacher.getId());
        examService.assignToClass(defaultExam.id(), schoolClass.id(), teacher.getId());
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW (connection riêng) — phải commit fixture
        // vừa tạo trước, nếu không giao dịch lồng không thấy được exercise/exam vừa tạo → FK fail.
        commitCurrentTransactionAndStartNew();
        ExerciseAssignment assignment = exerciseService.deliverToClass(exercise.id(), schoolClass.id(), null, teacher.getId());
        exerciseAssignmentRepository.save(assignment);
        return exerciseAttemptService.startAttempt(exercise.id(), studentUser.getId());
    }

    private RecordIntegrityEventsRequest oneEvent(int durationSeconds) {
        OffsetDateTime start = OffsetDateTime.now().minusSeconds(durationSeconds);
        OffsetDateTime end = OffsetDateTime.now();
        return new RecordIntegrityEventsRequest(List.of(
                new RecordIntegrityEventsRequest.Event("OUT_OF_FOCUS", start, end, "test-agent")));
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private String bankCode() {
        return "QB-" + SEQ.incrementAndGet();
    }

    private String exerciseCode() {
        return "EX-" + SEQ.incrementAndGet();
    }

    private String examCode() {
        return "KD-" + SEQ.incrementAndGet();
    }

    private String setCode() {
        return "RVS-" + SEQ.incrementAndGet();
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
        s.setCode("SITE-" + SEQ.incrementAndGet());
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
