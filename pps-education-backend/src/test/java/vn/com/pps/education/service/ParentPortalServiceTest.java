package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SitePeriodTemplate;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.ApplyClassHomeworkRequest;
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
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
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
    private ExerciseAssignmentRepository exerciseAssignmentRepository;

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
    private vn.com.pps.education.repository.SitePeriodTemplateRepository sitePeriodTemplateRepository;

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
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        academicTerm = newAcademicTerm(site);
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

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

        // Cửa sổ bao quanh NGAY LÚC NÀY (không phải giờ cố định, không dùng LocalTime.MIN) -- bổ
        // sung 2026-08-14, SỬA LẠI 2026-08-18: trước đây đặt buổi ĐÃ KẾT THÚC (now-1h..now-1min) vì
        // requireSessionEndedAndAttendanceTaken đòi buổi đã kết thúc -- điều kiện đó đã bị BỎ HẲN
        // 2026-08-18 (xem docs/uc/phan-he-06-hoc-thuat.md UC-21), nhưng StudentAttendanceService.
        // markAttendance() giờ lại đòi buổi đang TRONG khung giờ diễn ra (UC-15, sửa đổi nghiệp vụ
        // 2026-08-18) nên buổi phải bao quanh "now" thay vì đã kết thúc. LocalTime.MIN vẫn không
        // dùng được vì bị hibernate.jdbc.time_zone=UTC quy đổi lệch, vi phạm CHECK chk_session_time.
        seedPeriod(site, 1, LocalTime.now().minusMinutes(1), LocalTime.now().plusHours(1), headAcademic);
        seedPeriod(site, 2, LocalTime.of(8, 0), LocalTime.of(9, 40), headAcademic);
        session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), "MORNING", List.of(1), null, "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
        // Bắt buộc để submitComments() cho DAILY không bị chặn bởi MissingLessonContentException
        // (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29).
        studentCommentService.updateLessonContent(session.id(), "Unit 1: Present simple tense.", teacher.getId());
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        studentAttendanceService.submitAttendance(session.id(), teacher.getId());
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
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.5"), "BAND", "B2", null, null, null), teacher.getId());
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
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.5"), "BAND", "B2", null, null, null), teacher.getId());
        // Chưa công bố (còn DRAFT) -- Phụ huynh chưa được xem.

        assertThatThrownBy(() -> parentPortalService.getEvaluationResult(
                student.getId(), schoolClass.id(), academicTerm.getId(), "MID_TERM", parentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listComments_UC25_A1_onlyApprovedCommentsVisible() {
        // DAILY dùng chung luồng DRAFT->Gửi->PENDING->duyệt với MID_TERM/END_TERM (2026-07-29).
        StudentCommentResponse approved = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), session.id(), LocalDate.now(), "Chăm chỉ.", null, null, false, null, null, null, null, null, null, null, null, null), teacher.getId());
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(approved.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(approved.id()), "APPROVED", null), siteManagerUser.getId());

        // A1 -- nhận xét khác vẫn DRAFT (chưa gửi/chưa duyệt). Phải ở 1 buổi KHÁC session gốc — từ
        // 2026-08-19 writeComment chặn tạo thêm nhận xét thứ 2 cho cùng 1 buổi đã APPROVED
        // (StudentCommentNotEditableException, xem StudentCommentService#writeComment).
        ClassSessionResponse otherSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", List.of(2), null, "REGULAR", "VIETNAMESE",
                        headAcademic.getId(), null, null, null, null, null),
                headAcademic.getId());
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), otherSession.id(), LocalDate.now(), "Nội dung chưa duyệt.", null, null, false, null, null, null, null, null, null, null, null, null), teacher.getId());

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
        User foreignTeacher = newUser("teacher.foreign.schedule");
        assignRole(foreignTeacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(foreignTeacher.getId(), "PRIMARY", null, LocalDate.now(), "FOREIGN"), headAcademic.getId());
        ClassSessionResponse foreignSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", List.of(2), null, "REGULAR", "FOREIGN",
                        foreignTeacher.getId(), null, null, null, null, null),
                headAcademic.getId());

        List<ClassSessionResponse> schedule = parentPortalService.listSchedule(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(schedule).filteredOn(s -> s.id().equals(foreignSession.id()))
                .extracting(ClassSessionResponse::teacherType).containsExactly("FOREIGN");
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): mapper riêng của Cổng phụ huynh cũng phải trả đúng sessionNumber. */
    @Test
    void listSchedule_boSung_includesSessionNumber() {
        ClassSessionResponse secondSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(session.sessionDate().plusDays(2), "MORNING", List.of(2), null, "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
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
        return createOnlineExercise("VOCAB_GRAMMAR", "Ngữ pháp homework", examCode());
    }

    /**
     * Mirror {@link #createGrammarOnlineExercise()} — tổng quát hoá theo skillCategory để tái dùng cho
     * Reading/Writing (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22, khi bổ sung 2 kênh
     * này cho Cổng phụ huynh — trước đó chỉ có sẵn cho Ngữ pháp).
     */
    private ExerciseResponse createOnlineExercise(String skillCategory, String label, String examCode) {
        var exam = examService.createExam(
                new CreateExamRequest(examCode, "Đề " + label, schoolClass.curriculumId(), "VIETNAMESE", "HOMEWORK", null), teacher.getId());
        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());
        // V75 (Kho đề): mỗi Exam tự sinh 1 QuestionBank nội bộ riêng, không nhận câu hỏi qua
        // QuestionBankService#createQuestion (chỉ dành cho bank "legacy" độc lập) — phải qua
        // ExamQuestionService#createQuestion (tự resolve bank nội bộ theo examId).
        QuestionResponse question = examQuestionService.createQuestion(exam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY", "She ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", null, false, 1), new QuestionChoiceRequest("B", "goes", null, true, 2)), null, null),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Bài " + label, exam.id(), null,
                        "ASSIGNED", new BigDecimal("1"), null, false, 1, true, null, skillCategory), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(question.id(), 1, new BigDecimal("1.0")), teacher.getId());
        // V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25): assignBatchToClass
        // (giao BTVN theo "Lô kỹ năng") chỉ nhận Bài đã PUBLISHED cùng skillCategory với kênh buổi
        // học (session VIETNAMESE -> VOCAB_GRAMMAR, xem StudentCommentService#grammarChannelSkillCategory).
        exercise = exerciseService.publishExercise(exercise.id(), teacher.getId());
        // V71: writeComment gọi deliverToClass bên trong bằng PROPAGATION_REQUIRES_NEW — phải
        // commit Đề/Bài vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        return exercise;
    }

    /**
     * Mirror {@link #createOnlineExercise} nhưng thêm 1 Bài THỨ 2 cùng skillCategory VÀO CÙNG 1 Exam đã
     * có sẵn — dùng để dựng 1 Lô (HomeworkSkillBatch) gồm nhiều Bài thật (bổ sung ngoài SDD gốc, đã xác
     * nhận với người dùng 2026-09-22, kiểm tra breakdown từng Bài trong Lô ở Cổng phụ huynh).
     */
    private void addSecondExerciseToExam(Long examId, String skillCategory, String label) {
        QuestionResponse question = examQuestionService.createQuestion(examId,
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY", "They ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", null, true, 1), new QuestionChoiceRequest("B", "goes", null, false, 2)), null, null),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Bài " + label + " 2", examId, null,
                        "ASSIGNED", new BigDecimal("1"), null, false, 2, true, null, skillCategory), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(question.id(), 1, new BigDecimal("1.0")), teacher.getId());
        exerciseService.publishExercise(exercise.id(), teacher.getId());
        commitCurrentTransactionAndStartNew();
    }

    /** V65: hạn nộp BTVN buổi sau = buổi kế tiếp — cần 1 buổi trong tương lai để resolveNextSessionDueAt không chặn. */
    private void createNextSession() {
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(session.sessionDate().plusDays(2), "MORNING", List.of(2), null, "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null, null),
                headAcademic.getId());
    }

    /**
     * V98 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06):
     * curriculum trên "bộ" giờ CHỈ dùng lọc/tìm kiếm — điều kiện hiển thị
     * DUY NHẤT cho 1 lớp là gán tường minh qua assignToClass (mirror Kho
     * đề), nên phải gán trước khi writeComment (gọi deliverToClass bên
     * trong) mới thành công.
     */
    private ReviewVideoSetResponse createConnectionVideoAssignedToClass() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video homework", "CONNECTION", schoolClass.curriculumId(), "VIETNAMESE", null, 1, null),
                teacher.getId());
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        ReviewVideoSetResponse published = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED", null), teacher.getId());
        reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/homework.mp4",
                        1_000_000L, 100, 1, null, null, null),
                teacher.getId());
        // V71: writeComment gọi deliverToClass bên trong bằng PROPAGATION_REQUIRES_NEW — phải
        // commit Bộ video vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        return published;
    }

    /**
     * DAILY nay dùng chung luồng DRAFT->Gửi->PENDING->duyệt (2026-07-29) -- ghi rồi phải Gửi+duyệt mới APPROVED để lộ ra Cổng phụ huynh.
     *
     * Bổ sung 2026-09-12 (đã xác nhận với người dùng) — giao BTVN online (Ngữ pháp/Video) đã tách hẳn
     * khỏi Viết/Gửi nhận xét, chỉ còn qua {@code StudentCommentService#applyHomeworkToClass} ("Áp dụng
     * cho cả lớp"). writeComment()/submitComments() KHÔNG còn nhận/materialize BTVN online nữa — gọi
     * applyHomeworkToClass TRƯỚC khi viết nhận xét (tạo sẵn dòng StudentComment DRAFT + gán FK cho
     * student), rồi writeComment() chỉ ghi đè content/status của ĐÚNG dòng đó (không đụng
     * homeworkNextGrammarBatch/homeworkNextReviewVideoAssignment đã gán) — xem Javadoc
     * StudentCommentService#writeComment (không setter field BTVN online).
     */
    private StudentCommentResponse writeDailyComment(Long grammarExerciseId, Long videoSetId, String homeworkNext) {
        return writeDailyComment(grammarExerciseId, videoSetId, null, null, homeworkNext, null, null);
    }

    /**
     * Mirror bản 3-tham số ở trên, mở rộng thêm kênh Reading/Writing (bổ sung ngoài SDD gốc, đã xác
     * nhận với người dùng 2026-09-22, khi bổ sung 2 kênh này cho Cổng phụ huynh).
     */
    private StudentCommentResponse writeDailyComment(Long grammarExerciseId, Long videoSetId, Long readingExamId, Long writingExamId,
                                                       String homeworkNext, String homeworkNextReading, String homeworkNextWriting) {
        if (grammarExerciseId != null || videoSetId != null || readingExamId != null || writingExamId != null) {
            studentCommentService.applyHomeworkToClass(session.id(),
                    new ApplyClassHomeworkRequest(grammarExerciseId, videoSetId, readingExamId, writingExamId, null, null), teacher.getId());
        }
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), session.id(),
                        session.sessionDate(), "Nội dung buổi.", null, null, false, null, null, null, null, null,
                        homeworkNext, homeworkNextReading, homeworkNextWriting, null),
                teacher.getId());
        List<StudentCommentResponse> submitted = studentCommentService.submitComments(
                schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(comment.id()), "APPROVED", null), siteManagerUser.getId());
        return submitted.get(0);
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
        writeDailyComment(exercise.examId(), null, null);

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).hasSize(1);
        // V150: HomeworkProgressResponse.grammarAssignmentId() giờ thực ra là id của HomeworkSkillBatch
        // (xem ParentPortalService#toHomeworkProgressResponse), không còn là id của chính
        // ExerciseAssignment — tra ngược qua exerciseId+classId+status rồi đọc getHomeworkBatch().
        List<ExerciseAssignment> assignments = exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                exercise.id(), schoolClass.id(), ExerciseAssignment.Status.ACTIVE);
        assertThat(assignments).hasSize(1);
        assertThat(result.get(0).grammarAssignmentId()).isEqualTo(assignments.get(0).getHomeworkBatch().getId());
        assertThat(result.get(0).grammarOfflineText()).isNull();
        assertThat(result.get(0).grammarProgress()).isEqualTo("Chưa làm bài");
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 (fix bug thật) — "Chưa làm bài"
        // KHÔNG được coi là "Chưa đạt" (trước đây grammarPassed() trả về false thay vì null ở case này,
        // khiến FE hiện nhầm pill đỏ — xem Javadoc ParentPortalService#aggregatePassed).
        assertThat(result.get(0).grammarPassed()).isNull();
        assertThat(result.get(0).grammarItems()).hasSize(1);
        assertThat(result.get(0).grammarItems().get(0).progress()).isEqualTo("Chưa làm bài");
        assertThat(result.get(0).grammarItems().get(0).passed()).isNull();
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 — Cổng phụ huynh trước đây CHỈ lộ
     * kênh Ngữ pháp/Video (dữ liệu Reading/Writing đã có ở StudentComment từ V137 nhưng chưa lộ ra API
     * Cổng phụ huynh). Mirror listHomeworkProgress_MainFlow_onlineGrammarNotYetAttemptedShowsChuaLamBai
     * cho kênh Reading.
     */
    @Test
    void listHomeworkProgress_boSung_readingOnlineNotYetAttemptedShowsChuaLamBai() {
        ExerciseResponse exercise = createOnlineExercise("READING", "Đọc hiểu homework", examCode());
        createNextSession();
        writeDailyComment(null, null, exercise.examId(), null, null, null, null);

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).hasSize(1);
        List<ExerciseAssignment> assignments = exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                exercise.id(), schoolClass.id(), ExerciseAssignment.Status.ACTIVE);
        assertThat(assignments).hasSize(1);
        assertThat(result.get(0).readingAssignmentId()).isEqualTo(assignments.get(0).getHomeworkBatch().getId());
        assertThat(result.get(0).readingOfflineText()).isNull();
        assertThat(result.get(0).readingProgress()).isEqualTo("Chưa làm bài");
        assertThat(result.get(0).readingPassed()).isNull();
        // Kênh khác không được giao ở buổi này vẫn phải null hết, không "ăn nhầm" dữ liệu Reading.
        assertThat(result.get(0).grammarAssignmentId()).isNull();
        assertThat(result.get(0).writingAssignmentId()).isNull();
    }

    /** Mirror listHomeworkProgress_MainFlow_offlineGrammarHasTextButNoProgress cho kênh Writing (V135, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22). */
    @Test
    void listHomeworkProgress_boSung_writingOfflineHasTextButNoProgress() {
        writeDailyComment(null, null, null, null, null, null, "Viết đoạn văn 5 câu về gia đình");

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).writingOfflineText()).isEqualTo("Viết đoạn văn 5 câu về gia đình");
        assertThat(result.get(0).writingAssignmentId()).isNull();
        assertThat(result.get(0).writingProgress()).isNull();
    }

    /**
     * V150 mở rộng (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — 1 Lô gồm NHIỀU Bài
     * cùng kỹ năng: % gộp trước đây có thể che mất 1 Bài làm kém bị Bài khác kéo điểm lên. Kiểm tra
     * grammarItems trả đủ TỪNG Bài kèm % riêng, không chỉ 1 con số gộp.
     */
    @Test
    void listHomeworkProgress_boSung_grammarBatchWithMultipleExercisesExposesPerItemProgress() {
        ExerciseResponse exercise1 = createGrammarOnlineExercise();
        addSecondExerciseToExam(exercise1.examId(), "VOCAB_GRAMMAR", "Ngữ pháp homework");
        createNextSession();
        writeDailyComment(exercise1.examId(), null, null);

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).grammarTitle()).contains("2 bài");
        assertThat(result.get(0).grammarItems()).hasSize(2);
        assertThat(result.get(0).grammarItems()).allSatisfy(item -> assertThat(item.progress()).isEqualTo("Chưa làm bài"));
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

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 (fix bug thật) — trước đây BTVN
     * online (giao qua applyHomeworkToClass, tách hẳn khỏi Viết/Gửi nhận xét từ 2026-09-12) bị ẩn khỏi
     * Cổng phụ huynh nếu GV chưa viết/gửi/duyệt XONG phần nhận xét text của ĐÚNG buổi đó — dù học sinh
     * vẫn thấy và làm được bình thường ở Portal Học sinh. Ca thật gặp: GV nước ngoài giao Video phản xạ
     * xong nhưng chưa viết nhận xét ngày hôm đó, Phụ huynh thấy "Chưa có bài tập nào được giao" sai sự
     * thật. Test này KHÔNG gọi writeDailyComment (luôn submit+duyệt) — chỉ gọi thẳng
     * applyHomeworkToClass rồi dừng ở DRAFT, mirror đúng ca lỗi.
     */
    @Test
    void listHomeworkProgress_boSung_onlineVideoVisibleEvenWhenDailyCommentNotApprovedYet() {
        ReviewVideoSetResponse set = createConnectionVideoAssignedToClass();
        createNextSession();
        List<StudentCommentResponse> applied = studentCommentService.applyHomeworkToClass(session.id(),
                new ApplyClassHomeworkRequest(null, set.id(), null, null, null, null), teacher.getId());
        assertThat(applied).hasSize(1);
        assertThat(applied.get(0).status()).isEqualTo("DRAFT");

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).videoAssignmentId()).isEqualTo(applied.get(0).homeworkNextReviewVideoAssignmentId());
        assertThat(result.get(0).videoProgress()).isEqualTo("0%");
    }

    /**
     * Mirror trực tiếp {@link #listHomeworkProgress_boSung_onlineVideoVisibleEvenWhenDailyCommentNotApprovedYet}
     * cho kênh Ngữ pháp ONLINE (Lô) — chứng minh fix áp dụng chung 1 code path cho cả 4 kênh online, không
     * chỉ riêng Video.
     */
    @Test
    void listHomeworkProgress_boSung_onlineGrammarVisibleEvenWhenDailyCommentNotApprovedYet() {
        ExerciseResponse exercise = createGrammarOnlineExercise();
        createNextSession();
        List<StudentCommentResponse> applied = studentCommentService.applyHomeworkToClass(session.id(),
                new ApplyClassHomeworkRequest(exercise.examId(), null, null, null, null, null), teacher.getId());
        assertThat(applied.get(0).status()).isEqualTo("DRAFT");

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).grammarProgress()).isEqualTo("Chưa làm bài");
    }

    /**
     * Đối chứng — BTVN OFFLINE (chữ tự do gõ CHUNG lúc viết nhận xét, chưa tách khỏi luồng Viết/Gửi/
     * Duyệt) vẫn PHẢI đợi APPROVED mới lộ ra, khác hẳn 4 kênh online ở 2 test trên (bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng 2026-09-22) — tránh lộ nội dung GV còn đang gõ dở/chưa duyệt.
     */
    @Test
    void listHomeworkProgress_boSung_offlineGrammarTextHiddenWhileDailyCommentNotApprovedYet() {
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), session.id(), session.sessionDate(), "Nội dung buổi.",
                        null, null, false, null, null, null, null, null, "Ôn lại Unit 3 ở nhà", null, null, null),
                teacher.getId());
        assertThat(comment.status()).isEqualTo("DRAFT");

        List<HomeworkProgressResponse> result = parentPortalService.listHomeworkProgress(student.getId(), schoolClass.id(), parentUser.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void listHomeworkProgress_skipsSessionsWithNoHomeworkAssigned() {
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), session.id(), session.sessionDate(), "Nội dung buổi, không giao BTVN.", null, null, false, null, null, null, null, null, null, null, null, null),
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

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-19 — session_periods giờ sinh từ site_period_templates thay vì chia đều theo phút. */
    private void seedPeriod(Site site, int periodNumber, LocalTime start, LocalTime end, User createdBy) {
        SitePeriodTemplate template = new SitePeriodTemplate();
        template.setSite(site);
        template.setPeriodNumber(periodNumber);
        template.setDayPart(SitePeriodTemplate.DayPart.MORNING);
        template.setStartTime(start);
        template.setEndTime(end);
        template.setCreatedBy(createdBy);
        sitePeriodTemplateRepository.save(template);
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
