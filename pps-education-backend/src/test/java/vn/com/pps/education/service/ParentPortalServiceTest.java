package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.AttendanceMarkResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateGradeComponentSetupRequest;
import vn.com.pps.education.dto.CreateGradeEvaluationComponentRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.EnterGradeEvaluationResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.GradeComponentSetupResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradeEvaluationComponentResponse;
import vn.com.pps.education.dto.GradeEvaluationResultResponse;
import vn.com.pps.education.dto.HomeworkProgressResponse;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-25: Xem Portal Phụ huynh — Main Flow (bước 2-4), A1 (dữ liệu chưa công bố/chưa duyệt không hiển thị). */
@Transactional
class ParentPortalServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ParentPortalService parentPortalService;

    @Autowired
    private ClassService classService;

    @Autowired
    private ClassSessionService classSessionService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private StudentCommentService studentCommentService;

    @Autowired
    private StudentAttendanceService studentAttendanceService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ExamQuestionService examQuestionService;

    @Autowired
    private ExamService examService;

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
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentRepository parentStudentRepository;

    @Autowired
    private SiteManagerRepository siteManagerRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    private User headAcademic;
    private User teacher;
    private User siteManagerUser;
    private ClassResponse schoolClass;
    private AcademicTerm academicTerm;
    private Student student;
    private User parentUser;
    private ClassSessionResponse session;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        academicTerm = newAcademicTerm(site);
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        vn.com.pps.education.domain.SiteManager siteManager = new vn.com.pps.education.domain.SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);

        student = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        parentUser = newUser("parent");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        parentStudentRepository.save(link);

        session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(9, 40), null, teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
        // Bắt buộc để submitComments() cho DAILY không bị chặn bởi MissingLessonContentException
        // (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29).
        studentCommentService.updateLessonContent(session.id(), "Unit 1: Present simple tense.", teacher.getId());
    }

    @Test
    void listMyChildren_UC25_MainFlow_returnsLinkedChild() {
        List<vn.com.pps.education.dto.ChildResponse> children = parentPortalService.listMyChildren(parentUser.getId());

        assertThat(children).extracting(vn.com.pps.education.dto.ChildResponse::studentId).contains(student.getId());
    }

    @Test
    void listGrades_UC25_A1_onlyOfficialGradesVisible() {
        GradeComponentSetupResponse setup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "MID_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        GradeEvaluationComponentResponse component = gradeService.addGradeEvaluationComponent(setup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "SPEAKING", "Nói", new BigDecimal("10.00"), null, null, 1),
                headAcademic.getId());
        GradeEntryResponse publishedEntry = gradeService.enterGrade(schoolClass.id(), component.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId());
        gradeService.submitGradesForApproval(new SubmitGradesRequest(List.of(publishedEntry.id()), null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest("APPROVE", List.of(publishedEntry.id()), null, null, null, null), siteManagerUser.getId());

        // A1 -- 1 bản ghi khác vẫn DRAFT, chưa duyệt.
        GradeEvaluationComponentResponse component2 = gradeService.addGradeEvaluationComponent(setup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "WRITING", "Viết", new BigDecimal("10.00"), null, null, 2),
                headAcademic.getId());
        gradeService.enterGrade(schoolClass.id(), component2.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("7"), false, null), teacher.getId());

        List<GradeEntryResponse> grades = parentPortalService.listGrades(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(grades).hasSize(1);
        assertThat(grades.get(0).status()).isEqualTo("OFFICIAL");
        assertThat(grades.get(0).id()).isEqualTo(publishedEntry.id());
    }

    @Test
    void getEvaluationResult_UC25_UC53_MainFlow_returnsOfficialOverallLevel() {
        GradeComponentSetupResponse setup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "MID_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        var enteredResult = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), setup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.5"), "BAND", "B2", null, null), teacher.getId());
        gradeService.submitGradesForApproval(new SubmitGradesRequest(null, List.of(enteredResult.id())), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest("APPROVE", null, List.of(enteredResult.id()), null, null, null), siteManagerUser.getId());

        GradeEvaluationResultResponse result = parentPortalService.getEvaluationResult(
                student.getId(), schoolClass.id(), academicTerm.getId(), "MID_TERM", parentUser.getId());

        assertThat(result.status()).isEqualTo("OFFICIAL");
        assertThat(result.overallScore()).isEqualByComparingTo("7.5");
        assertThat(result.level()).isEqualTo("B2");
    }

    @Test
    void getEvaluationResult_UC25_A1_rejectsWhenResultNotPublishedYet() {
        GradeComponentSetupResponse setup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "MID_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), setup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.5"), "BAND", "B2", null, null), teacher.getId());
        // Chưa công bố (còn DRAFT) -- Phụ huynh chưa được xem.

        assertThatThrownBy(() -> parentPortalService.getEvaluationResult(
                student.getId(), schoolClass.id(), academicTerm.getId(), "MID_TERM", parentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listComments_UC25_A1_onlyApprovedCommentsVisible() {
        // DAILY dùng chung luồng DRAFT->Gửi->PENDING->duyệt với MID_TERM/END_TERM (2026-07-29).
        StudentCommentResponse approved = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", session.id(), null,
                        LocalDate.now(), "Chăm chỉ.", null, null, false, null, null, null, null, null, null, null, null), teacher.getId());
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(approved.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(approved.id()), "APPROVED", null), siteManagerUser.getId());

        // A1 -- nhận xét khác vẫn DRAFT (chưa gửi/chưa duyệt).
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", session.id(), null,
                        LocalDate.now(), "Nội dung chưa duyệt.", null, null, false, null, null, null, null, null, null, null, null), teacher.getId());

        List<StudentCommentResponse> comments = parentPortalService.listComments(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).status()).isEqualTo("APPROVED");
    }

    @Test
    void listAttendance_UC25_MainFlow_returnsAttendanceForClass() {
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "ABSENT", null, null, "Ốm"))),
                teacher.getId());

        List<AttendanceMarkResponse> attendance = parentPortalService.listAttendance(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(attendance).hasSize(1);
        assertThat(attendance.get(0).status()).isEqualTo("ABSENT");
    }

    @Test
    void listSchedule_UC25_MainFlow_returnsClassSessions() {
        List<ClassSessionResponse> schedule = parentPortalService.listSchedule(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(schedule).extracting(ClassSessionResponse::id).contains(session.id());
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): mapper riêng của Cổng phụ huynh cũng phải trả đúng teacherType. */
    @Test
    void listSchedule_boSung_includesTeacherType() {
        ClassSessionResponse foreignSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        null, teacher.getId(), "REGULAR", "FOREIGN", null),
                headAcademic.getId());

        List<ClassSessionResponse> schedule = parentPortalService.listSchedule(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(schedule).filteredOn(s -> s.id().equals(foreignSession.id()))
                .extracting(ClassSessionResponse::teacherType).containsExactly("FOREIGN");
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): mapper riêng của Cổng phụ huynh cũng phải trả đúng sessionNumber. */
    @Test
    void listSchedule_boSung_includesSessionNumber() {
        ClassSessionResponse secondSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(session.sessionDate().plusDays(2), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        null, teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());

        List<ClassSessionResponse> schedule = parentPortalService.listSchedule(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(schedule).filteredOn(s -> s.id().equals(session.id()))
                .extracting(ClassSessionResponse::sessionNumber).containsExactly(1);
        assertThat(schedule).filteredOn(s -> s.id().equals(secondSession.id()))
                .extracting(ClassSessionResponse::sessionNumber).containsExactly(2);
    }

    // ===================== Xem tiến độ BTVN của con (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29) =====================

    /**
     * V65: chỉ tạo + thêm câu hỏi, KHÔNG giao lớp nữa — việc giao (deliverToClass) giờ chỉ xảy ra khi GV chọn làm "BTVN buổi sau" ở writeDailyComment.
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * Bài giờ thuộc 1 Đề (Exam) — tạo Đề mới + gán cho schoolClass ngay ở
     * đây (deliverToClass gọi sau này bên trong StudentCommentService cần
     * Đề đã gán lớp mới thành công).
     */
    private ExerciseResponse createGrammarOnlineExercise() {
        var exam = examService.createExam(
                new CreateExamRequest(examCode(), "Đề Ngữ pháp homework", schoolClass.curriculumId(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());
        // V75 (Kho đề): mỗi Exam tự sinh 1 QuestionBank nội bộ riêng, không nhận câu hỏi qua
        // QuestionBankService#createQuestion (chỉ dành cho bank "legacy" độc lập) — phải qua
        // ExamQuestionService#createQuestion (tự resolve bank nội bộ theo examId).
        QuestionResponse question = examQuestionService.createQuestion(exam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY", "She ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Bài ngữ pháp homework", exam.id(), null,
                        "ASSIGNED", new BigDecimal("1"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(question.id(), 1, new BigDecimal("1.0")), teacher.getId());
        // V71: writeComment gọi deliverToClass bên trong bằng PROPAGATION_REQUIRES_NEW — phải
        // commit Đề/Bài vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        return exercise;
    }

    /** V65: hạn nộp BTVN buổi sau = buổi kế tiếp — cần 1 buổi trong tương lai để resolveNextSessionDueAt không chặn. */
    private void createNextSession() {
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(session.sessionDate().plusDays(2), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        null, teacher.getId(), "REGULAR", null, null),
                headAcademic.getId());
    }

    private ReviewVideoSetResponse createConnectionVideoAssignedToClass() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video homework", "CONNECTION", null, schoolClass.id(), null, 1),
                teacher.getId());
        ReviewVideoSetResponse published = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), null, 1, "PUBLISHED"), teacher.getId());
        reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/homework.mp4",
                        1_000_000L, 100, 1, null, null),
                teacher.getId());
        // V71: writeComment gọi deliverToClass bên trong bằng PROPAGATION_REQUIRES_NEW — phải
        // commit Bộ video vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        return published;
    }

    /**
     * DAILY nay dùng chung luồng DRAFT->Gửi->PENDING->duyệt (2026-07-29) -- ghi rồi phải Gửi+duyệt mới APPROVED để lộ ra Cổng phụ huynh.
     * V65: grammarExerciseId/videoSetId khác null tự động giao (deliverToClass) cho cả lớp ngay khi writeComment.
     * Trả về comment lúc tạo (DRAFT) vì homeworkNext*AssignmentId không đổi qua submit/decide (không cascade).
     */
    private StudentCommentResponse writeDailyComment(Long grammarExerciseId, Long videoSetId, String homeworkNext) {
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", session.id(), null,
                        session.sessionDate(), "Nội dung buổi.", null, null, false, null, null, null,
                        homeworkNext, grammarExerciseId, videoSetId, null, null),
                teacher.getId());
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(comment.id()), "APPROVED", null), siteManagerUser.getId());
        return comment;
    }

    @Test
    void listHomeworkProgress_MainFlow_offlineGrammarHasTextButNoProgress() {
        writeDailyComment(null, null, "Ôn lại Unit 3 ở nhà");

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).grammarOfflineText()).isEqualTo("Ôn lại Unit 3 ở nhà");
        assertThat(result.get(0).grammarAssignmentId()).isNull();
        assertThat(result.get(0).grammarProgress()).isNull();
    }

    @Test
    void listHomeworkProgress_MainFlow_onlineGrammarNotYetAttemptedShowsChuaLamBai() {
        ExerciseResponse exercise = createGrammarOnlineExercise();
        createNextSession();
        StudentCommentResponse comment = writeDailyComment(exercise.id(), null, null);

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).hasSize(1);
        assertThat(comment.homeworkNextExerciseAssignmentId()).isNotNull();
        assertThat(result.get(0).grammarAssignmentId()).isEqualTo(comment.homeworkNextExerciseAssignmentId());
        assertThat(result.get(0).grammarOfflineText()).isNull();
        assertThat(result.get(0).grammarProgress()).isEqualTo("Chưa làm bài");
    }

    @Test
    void listHomeworkProgress_MainFlow_connectionVideoShowsWatchPercent() {
        ReviewVideoSetResponse set = createConnectionVideoAssignedToClass();
        createNextSession();
        StudentCommentResponse comment = writeDailyComment(null, set.id(), null);

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).hasSize(1);
        assertThat(comment.homeworkNextReviewVideoAssignmentId()).isNotNull();
        assertThat(result.get(0).videoAssignmentId()).isEqualTo(comment.homeworkNextReviewVideoAssignmentId());
        assertThat(result.get(0).videoProgress()).isEqualTo("0%");
    }

    @Test
    void listHomeworkProgress_skipsSessionsWithNoHomeworkAssigned() {
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", session.id(), null,
                        session.sessionDate(), "Nội dung buổi, không giao BTVN.", null, null, false, null, null, null, null, null, null, null, null),
                siteManagerUser.getId());

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).isEmpty();
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

    @Test
    void listGrades_rejectsWhenActorNotLinkedParent() {
        User outsider = newUser("outsider.parent");
        Parent outsiderParent = new Parent();
        outsiderParent.setUser(outsider);
        parentRepository.save(outsiderParent);

        assertThatThrownBy(() -> parentPortalService.listGrades(student.getId(), schoolClass.id(), outsider.getId()))
                .isInstanceOf(NotAuthorizedForPortalAccessException.class);
    }

    @Test
    void listGrades_rejectsWhenStudentNeverEnrolledInClass() {
        Site otherSite = newSite();
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "9B1", otherSite.getId(), schoolClass.curriculumId(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        assertThatThrownBy(() -> parentPortalService.listGrades(student.getId(), otherClass.id(), parentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
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

    private AcademicTerm newAcademicTerm(Site site) {
        AcademicTerm term = new AcademicTerm();
        term.setSite(site);
        term.setCode("TERM-" + SEQ.incrementAndGet());
        term.setName("Kỳ test");
        term.setStartDate(LocalDate.now().minusMonths(1));
        term.setEndDate(LocalDate.now().plusMonths(2));
        term.setCreatedBy(headAcademic);
        return academicTermRepository.save(term);
    }

    private Student newStudent() {
        User user = newUser("student");
        Student s = new Student();
        s.setUser(user);
        s.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        s.setDateOfBirth(LocalDate.of(2012, 5, 1));
        s.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(s);
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
