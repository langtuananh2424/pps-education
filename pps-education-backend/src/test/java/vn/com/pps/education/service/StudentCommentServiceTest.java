package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Room;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.AssignExerciseRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DailyCommentImportResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.ExerciseAssignmentResponse;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.dto.UpdateStudentCommentRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.InvalidCommentContextException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.StudentCommentNotEditableException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.RoomRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-21: Viết nhận xét học sinh + UC-22: Duyệt nhận xét. Xem
 * docs/uc/phan-he-06-hoc-thuat.md.
 *
 * Nhận xét Hàng ngày (comment_type=DAILY — bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng 2026-07-29, thay quyết định 2026-07-24): dùng chung
 * 100% luồng DRAFT→submit→PENDING→duyệt với Giữa/Cuối kỳ (MID_TERM/
 * END_TERM) — không còn tự động route trạng thái khi ghi/sửa/import Excel.
 */
@Transactional
class StudentCommentServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private StudentCommentService studentCommentService;

    @Autowired
    private ClassService classService;

    @Autowired
    private ClassSessionService classSessionService;

    @Autowired
    private StudentAttendanceService studentAttendanceService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ExerciseAttemptService exerciseAttemptService;

    @Autowired
    private QuestionBankService questionBankService;

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
    private SiteManagerRepository siteManagerRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoomRepository roomRepository;

    private User headAcademic;
    private User teacher;
    private User siteManagerUser;
    private ClassResponse schoolClass;
    private Student student;
    private ClassSessionResponse classSession;
    private GradePeriodResponse gradePeriod;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());

        siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);

        student = newStudent();

        Room room = newRoom(site);
        classSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR"),
                headAcademic.getId());
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        gradePeriod = gradeService.createGradePeriod(activeCurriculum.id(),
                new CreateGradePeriodRequest("MID_1", "Giữa kỳ 1", 1, new BigDecimal("50"), null, null), headAcademic.getId());
    }

    @Test
    void writeComment_UC21_MainFlow_dailyCommentSavesAsDraft() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Chăm chỉ, tích cực phát biểu.");

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.commentType()).isEqualTo("DAILY");
        assertThat(comment.classSessionId()).isEqualTo(classSession.id());
        assertThat(comment.severity()).isEqualTo("NORMAL");
        assertThat(studentCommentService.listPendingForSite(siteManagerUser.getId()))
                .extracting(StudentCommentResponse::id).doesNotContain(comment.id());
    }

    @Test
    void writeComment_UC21_dailyCommentSavesAsDraftEvenForActorWithApprovePermission() {
        StudentCommentResponse comment = writeDailyComment(siteManagerUser, "Nội dung do quản lý nhập.");

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.visibleToParentAt()).isNull();
    }

    @Test
    void writeComment_UC21_MainFlow_savesMidTermCommentWithWarningFlag() {
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "MID_TERM", null, gradePeriod.id(),
                        LocalDate.now(), "Cần cải thiện kỹ năng nghe.", null, "CONCERN", true, null, null, null, null, null, null, null),
                teacher.getId());

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.commentType()).isEqualTo("MID_TERM");
        assertThat(comment.gradePeriodId()).isEqualTo(gradePeriod.id());
        assertThat(comment.severity()).isEqualTo("CONCERN");
        assertThat(comment.isWarning()).isTrue();
    }

    @Test
    void writeComment_rejectsInvalidContextForDailyWithoutSession() {
        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", null, null,
                        LocalDate.now(), "Nội dung", null, null, false, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(InvalidCommentContextException.class);
    }

    @Test
    void writeComment_rejectsInvalidContextForMidTermWithSessionInsteadOfPeriod() {
        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "MID_TERM", classSession.id(), null,
                        LocalDate.now(), "Nội dung", null, null, false, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(InvalidCommentContextException.class);
    }

    @Test
    void writeComment_rejectsWhenActorNotAssignedTeacherNorApprover() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> writeDailyComment(outsider, "Nội dung"))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void writeComment_UC21_dailyCommentBlockedAfterEditWindowForTeacher() {
        Room room = newRoom(siteOf(schoolClass));
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().minusDays(8), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR"),
                headAcademic.getId());

        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", oldSession.id(), null,
                        oldSession.sessionDate(), "Nội dung", null, null, false, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void writeComment_UC21_approverBypassesEditWindow() {
        Room room = newRoom(siteOf(schoolClass));
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().minusDays(8), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR"),
                headAcademic.getId());

        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", oldSession.id(), null,
                        oldSession.sessionDate(), "Nội dung do quản lý nhập ngoài hạn.", null, null, false, null, null, null, null, null, null, null),
                siteManagerUser.getId());

        assertThat(comment.status()).isEqualTo("DRAFT");
    }

    @Test
    void updateComment_UC21_MainFlow_editableWhileDraft() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");
        assertThat(comment.status()).isEqualTo("DRAFT");

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung đã sửa.", null, null, false, "GOOD", "80%", "60%", "Unit 4", null, null, "Ghi chú"),
                teacher.getId());

        assertThat(edited.status()).isEqualTo("DRAFT");
        assertThat(edited.content()).isEqualTo("Nội dung đã sửa.");
        assertThat(edited.attitude()).isEqualTo("GOOD");
        assertThat(edited.homeworkPreviousScore()).isEqualTo("80%");
        assertThat(edited.homeworkPreviousSpeakingScore()).isEqualTo("60%");
        assertThat(edited.homeworkNext()).isEqualTo("Unit 4");
        assertThat(edited.note()).isEqualTo("Ghi chú");
    }

    @Test
    void updateComment_UC21_rejectsWhenDailyCommentPending() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        assertThatThrownBy(() -> studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Sửa khi đang chờ duyệt.", null, null, false, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void updateComment_UC21_V56_homeworkPreviousSpeakingScoreIndependentFromGrammarScore() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung.", null, null, false, null, null, "70%", null, null, null, null),
                teacher.getId());

        assertThat(edited.homeworkPreviousSpeakingScore()).isEqualTo("70%");
        assertThat(edited.homeworkPreviousScore()).isNull();
    }

    @Test
    void submitComments_UC21_MainFlow_midTermTransitionsToPendingAndNotifiesSiteManager() {
        StudentCommentResponse comment = writeMidTermComment();

        List<StudentCommentResponse> submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        assertThat(submitted).hasSize(1);
        assertThat(submitted.get(0).status()).isEqualTo("PENDING");
        assertThat(studentCommentService.listPendingForSite(siteManagerUser.getId()))
                .extracting(StudentCommentResponse::id).contains(comment.id());
    }

    @Test
    void submitComments_rejectsWhenNotDraft() {
        StudentCommentResponse comment = writeMidTermComment();
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        assertThatThrownBy(() -> studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    /**
     * Hệ quả của quyết định 2026-07-29 (bỏ hẳn bypass duyệt-thẳng khi ghi):
     * Quản lý điểm trường không kiêm GV lớp đó tự viết 1 nhận xét DAILY
     * (DRAFT) thì KHÔNG tự Gửi được — submitComments() luôn yêu cầu actor
     * là GV được phân công lớp (requireAssignedTeacher, dùng chung với
     * MID_TERM/END_TERM, không mở rào riêng cho DAILY).
     */
    @Test
    void submitComments_UC21_rejectsWhenActorNotAssignedTeacherForDaily() {
        StudentCommentResponse comment = writeDailyComment(siteManagerUser, "Nội dung do quản lý tự viết.");

        assertThatThrownBy(() -> studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), siteManagerUser.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void decideComments_UC22_MainFlow_approvedMakesVisibleToParent() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung nhận xét.");
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        List<StudentCommentResponse> decided = studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "APPROVED", "Đạt"), siteManagerUser.getId());

        assertThat(decided.get(0).status()).isEqualTo("APPROVED");
        assertThat(decided.get(0).visibleToParentAt()).isNotNull();
    }

    @Test
    void decideComments_UC22_A1_batchApprovalForMultipleComments() {
        Student student2 = newStudent();
        StudentCommentResponse comment1 = writeDailyComment(teacher, "Nhận xét HS1.");
        StudentCommentResponse comment2 = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student2.getId(), "DAILY", classSession.id(), null,
                        LocalDate.now(), "Nhận xét HS2.", null, null, false, null, null, null, null, null, null, null),
                teacher.getId());
        studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment1.id(), comment2.id())), teacher.getId());

        List<StudentCommentResponse> decided = studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment1.id(), comment2.id()), "APPROVED", null), siteManagerUser.getId());

        assertThat(decided).hasSize(2);
        assertThat(decided).allSatisfy(c -> assertThat(c.status()).isEqualTo("APPROVED"));
        assertThat(studentCommentService.listPendingForSite(siteManagerUser.getId())).isEmpty();
    }

    @Test
    void decideComments_UC22_MainFlow_rejectedReturnsToTeacherWithReasonAndUC21_A1_editableAgain() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        List<StudentCommentResponse> decided = studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "REJECTED", "Nội dung chưa rõ ràng"), siteManagerUser.getId());
        assertThat(decided.get(0).status()).isEqualTo("REJECTED");
        assertThat(decided.get(0).visibleToParentAt()).isNull();

        // DAILY: sửa lại sau khi bị từ chối -- quay lại DRAFT (dùng chung logic MID_TERM/END_TERM), phải Gửi lại mới sang PENDING.
        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung đã sửa lại.", null, null, false, null, null, null, null, null, null, null),
                teacher.getId());
        assertThat(edited.status()).isEqualTo("DRAFT");
    }

    @Test
    void updateComment_midTerm_rejectsWhenPending() {
        StudentCommentResponse comment = writeMidTermComment();
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        assertThatThrownBy(() -> studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Sửa khi đang chờ duyệt.", null, null, false, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void decideComments_rejectsWhenActorNotSiteManagerForSite() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung.");
        User outsiderManager = newUser("outsider.sitemanager");
        assignRole(outsiderManager, "SITE_MANAGER");

        assertThatThrownBy(() -> studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "APPROVED", null), outsiderManager.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void decideComments_rejectsWhenAlreadyDecided() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung.");
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(comment.id()), "APPROVED", null), siteManagerUser.getId());

        assertThatThrownBy(() -> studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "APPROVED", null), siteManagerUser.getId()))
                .isInstanceOf(ApprovalAlreadyDecidedException.class);
    }

    @Test
    void buildTemplate_hasOneRowPerActiveStudentWithAttendancePrefilled() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());

        byte[] template = studentCommentService.buildTemplate(classSession.id(), teacher.getId());

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            List<String> headers = new java.util.ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                headers.add(header.getCell(i).getStringCellValue());
            }
            assertThat(headers).containsExactly("Ngày*", "Mã học viên*", "Họ và tên", "Điểm danh*",
                    "Thái độ học tập", "BTVN Ngữ pháp buổi trước", "BTVN Nghe-nói buổi trước",
                    "Nhận xét học sinh*", "BTVN Ngữ pháp buổi sau", "BTVN Nghe-nói buổi sau", "Ghi chú");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            Row row = sheet.getRow(1);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo(student.getStudentCode());
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("Có mặt");

            List<? extends org.apache.poi.ss.usermodel.DataValidation> validations = sheet.getDataValidations();
            assertThat(validations).hasSize(2);
            assertThat(validations).anySatisfy(v -> {
                assertThat(v.getRegions().getCellRangeAddress(0).getFirstColumn()).isEqualTo(3);
                assertThat(v.getValidationConstraint().getExplicitListValues())
                        .containsExactly("Có mặt", "Vắng", "Có phép", "Muộn", "Về sớm");
            });
            assertThat(validations).anySatisfy(v -> {
                assertThat(v.getRegions().getCellRangeAddress(0).getFirstColumn()).isEqualTo(4);
                assertThat(v.getValidationConstraint().getExplicitListValues())
                        .containsExactly("Kém", "Yếu", "Trung bình", "Trung bình khá", "Khá", "Tốt");
            });
        }
    }

    @Test
    void importComments_UC21_MainFlow_teacherImportSavesAsDraft() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "Tốt", "80%", "", "Rất tốt.", "Unit 5", "", "Không có gì."}
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
        List<StudentCommentResponse> comments = studentCommentService.listComments(schoolClass.id(), student.getId());
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).status()).isEqualTo("DRAFT");
        assertThat(comments.get(0).attitude()).isEqualTo("GOOD");
        assertThat(comments.get(0).homeworkPreviousScore()).isEqualTo("80%");
    }

    @Test
    void importComments_UC21_approverImportAlsoSavesAsDraft() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "", "Quản lý nhập trực tiếp.", "", "", ""}
        });

        studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), siteManagerUser.getId());

        List<StudentCommentResponse> comments = studentCommentService.listComments(schoolClass.id(), student.getId());
        assertThat(comments.get(0).status()).isEqualTo("DRAFT");
        assertThat(comments.get(0).visibleToParentAt()).isNull();
    }

    @Test
    void importComments_UC21_skipsAbsentStudentWithBlankCommentFields() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "ABSENT", null, null, "Ốm"))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Vắng", "", "", "", "", "", "", ""}
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(studentCommentService.listComments(schoolClass.id(), student.getId())).isEmpty();
    }

    @Test
    void importComments_UC21_A2_rejectsRowMissingContentWhenPresent() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "", "", "", "", ""}
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
    }

    @Test
    void importComments_UC21_changesAttendanceWhenActorAllowed() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "ABSENT", null, null, "Ốm"))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "", "Đã đi học lại.", "", "", ""}
        });

        studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(studentCommentService.listComments(schoolClass.id(), student.getId())).hasSize(1);
    }

    /**
     * Regression: actor có academic.comment.approve nhưng KHÔNG được ghi điểm
     * danh (không phải GV được phân công buổi, không có quyền quản trị điểm
     * danh) cố đổi điểm danh qua Excel — trước đây gọi thẳng
     * StudentAttendanceService.markAttendance() rồi bắt exception làm cả
     * transaction ngoài bị đánh dấu rollback-only (UnexpectedRollbackException
     * khi commit, dù đã catch) — phát hiện qua verify curl thật, xem
     * StudentAttendanceService.canWriteAttendance(). Giờ phải trả lỗi RÕ RÀNG
     * cho đúng dòng đó, KHÔNG được ném UnexpectedRollbackException, và dòng
     * khác không bị ảnh hưởng.
     */
    @Test
    void importComments_regression_approverWithoutAttendancePermissionGetsRowErrorNotTransactionCrash() throws IOException {
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null),
                        new EnterAttendanceMarkRequest(student2.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        // siteManagerUser có academic.comment.approve nhưng KHÔNG phải GV được phân công buổi
        // này và không có academic.attendance.create/update -- đổi điểm danh của student sẽ bị từ
        // chối, nhưng dòng của student2 (điểm danh không đổi) vẫn phải xử lý bình thường.
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Muộn", "", "", "", "Đến muộn.", "", "", ""},
                {classSession.sessionDate().toString(), student2.getStudentCode(), "", "Có mặt", "", "", "", "Bình thường.", "", "", ""},
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), siteManagerUser.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("Không sửa được điểm danh");
        // student2 (không đổi điểm danh) vẫn được lưu Nháp bình thường (import không còn tự động duyệt).
        assertThat(studentCommentService.listComments(schoolClass.id(), student2.getId()).get(0).status()).isEqualTo("DRAFT");
    }

    /** Excel import khôi phục DRAFT + submitComments() dùng lại nguyên -- 2 mảnh ghép phải ăn khớp thành luồng hoàn chỉnh. */
    @Test
    void importComments_UC21_MainFlow_thenSubmitTransitionsToPending() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "", "Rất tốt.", "", "", ""}
        });
        studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());
        StudentCommentResponse imported = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(imported.status()).isEqualTo("DRAFT");

        List<StudentCommentResponse> submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(imported.id())), teacher.getId());

        assertThat(submitted.get(0).status()).isEqualTo("PENDING");
    }

    /** Excel không được âm thầm ghi đè 1 dòng đã PENDING/APPROVED -- chỉ sửa được khi DRAFT/REJECTED (giống hệt updateComment). */
    @Test
    void importComments_rejectsReimportWhenExistingCommentPending() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "", "Rất tốt.", "", "", ""}
        });
        studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());
        StudentCommentResponse imported = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(imported.id())), teacher.getId());

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("chỉ sửa được khi DRAFT hoặc REJECTED");
    }

    // ===================== V55: BTVN online/offline theo học sinh =====================

    private record GrammarFixture(ExerciseAssignmentResponse assignment, QuestionResponse question) {}

    private record VideoFixture(ReviewVideoSetResponse set, ReviewVideoResponse video) {}

    private GrammarFixture createGrammarOnlineAssignment() {
        QuestionBankResponse bank = questionBankService.createBank(
                new CreateQuestionBankRequest(bankCode(), "Ngân hàng V55", null, null, null), teacher.getId());
        QuestionResponse question = questionBankService.createQuestion(
                new CreateQuestionRequest(bank.id(), "MULTIPLE_CHOICE", "GRAMMAR", "EASY", "She ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2))),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Bài ngữ pháp V55", schoolClass.curriculumId(), null,
                        "ASSIGNED", new BigDecimal("1"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(question.id(), 1, new BigDecimal("1.0")), teacher.getId());
        ExerciseAssignmentResponse assignment = exerciseService.assignExercise(exercise.id(),
                new AssignExerciseRequest(schoolClass.id(), null, null, false, null, null), teacher.getId());
        return new GrammarFixture(assignment, question);
    }

    private void answerGrammarCorrectly(GrammarFixture fixture) {
        var attempt = exerciseAttemptService.startAttempt(fixture.assignment().exerciseId(), student.getUser().getId());
        Long correctChoiceId = fixture.question().choices().stream().filter(c -> c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(fixture.question().id(), null, List.of(correctChoiceId), null), student.getUser().getId());
        exerciseAttemptService.submitAttempt(attempt.id(), student.getUser().getId());
    }

    private VideoFixture createConnectionVideoAssignedToClass(int durationSeconds) {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video V55", "CONNECTION", null, schoolClass.id(), null, 1),
                teacher.getId());
        ReviewVideoSetResponse published = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), null, 1, "PUBLISHED"), teacher.getId());
        ReviewVideoResponse video = reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/v55.mp4",
                        1_000_000L, durationSeconds, 1, null, null),
                teacher.getId());
        return new VideoFixture(published, video);
    }

    private void writeDailyCommentWithHomeworkNext(Student targetStudent, ClassSessionResponse session,
                                                    Long grammarAssignmentId, Long videoSetId) {
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(targetStudent.getId(), "DAILY", session.id(), null,
                        session.sessionDate(), "Nội dung buổi.", null, null, false, null, null, null, null,
                        grammarAssignmentId, videoSetId, null),
                teacher.getId());
    }

    private ClassSessionResponse nextSession() {
        Room room2 = newRoom(siteOf(schoolClass));
        return classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(classSession.sessionDate().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room2.getId(), teacher.getId(), "REGULAR"),
                headAcademic.getId());
    }

    /** Tìm dòng đúng theo mã học sinh (thứ tự roster không đảm bảo) rồi đọc giá trị 1 cột. */
    private String rowForStudent(byte[] excelBytes, String studentCode, int col) throws IOException {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null && row.getCell(1) != null && studentCode.equals(row.getCell(1).getStringCellValue())) {
                    var cell = row.getCell(col);
                    return cell == null ? null : cell.getStringCellValue();
                }
            }
            return null;
        }
    }

    private List<String> dropdownValues(byte[] excelBytes, int col) throws IOException {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            for (var validation : workbook.getSheetAt(0).getDataValidations()) {
                if (validation.getRegions().getCellRangeAddress(0).getFirstColumn() == col) {
                    return List.of(validation.getValidationConstraint().getExplicitListValues());
                }
            }
            return List.of();
        }
    }

    @Test
    void buildTemplate_V55_MainFlow_showsGrammarOnlinePercentFromPreviousSessionAttempt() throws IOException {
        GrammarFixture fixture = createGrammarOnlineAssignment();
        writeDailyCommentWithHomeworkNext(student, classSession, fixture.assignment().id(), null);
        answerGrammarCorrectly(fixture);
        ClassSessionResponse session2 = nextSession();

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), 5)).isEqualTo("100%");
    }

    @Test
    void buildTemplate_V55_MainFlow_showsNotYetDoneForAssignedButUnattemptedGrammar() throws IOException {
        GrammarFixture fixture = createGrammarOnlineAssignment();
        writeDailyCommentWithHomeworkNext(student, classSession, fixture.assignment().id(), null);
        ClassSessionResponse session2 = nextSession();

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), 5)).isEqualTo("Chưa làm bài");
    }

    @Test
    void buildTemplate_V55_MainFlow_showsVideoWatchPercentFromPreviousSessionAssignment() throws IOException {
        VideoFixture fixture = createConnectionVideoAssignedToClass(100);
        writeDailyCommentWithHomeworkNext(student, classSession, null, fixture.set().id());
        Long sessionId = reviewVideoService.startWatchSession(fixture.video().id(), student.getUser().getId()).sessionId();
        reviewVideoService.reportProgress(fixture.video().id(), new ReportVideoProgressRequest(sessionId, 80), student.getUser().getId());
        ClassSessionResponse session2 = nextSession();

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), 6)).isEqualTo("80%");
    }

    /** Homework theo TỪNG học sinh (không theo cả lớp) — học sinh không được giao thì cột tự động để trống, không phải "Chưa làm bài". */
    @Test
    void buildTemplate_V55_MainFlow_leavesAutoColumnNullForStudentNotAssigned() throws IOException {
        GrammarFixture fixture = createGrammarOnlineAssignment();
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        writeDailyCommentWithHomeworkNext(student, classSession, fixture.assignment().id(), null);
        writeDailyCommentWithHomeworkNext(student2, classSession, null, null);
        ClassSessionResponse session2 = nextSession();

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), 5)).isEqualTo("Chưa làm bài");
        assertThat(rowForStudent(template, student2.getStudentCode(), 5)).isNull();
    }

    @Test
    void buildTemplate_V55_MainFlow_dropdownOnlyListsAssignmentsForThisClass() throws IOException {
        GrammarFixture fixture = createGrammarOnlineAssignment();
        VideoFixture video = createConnectionVideoAssignedToClass(100);

        byte[] template = studentCommentService.buildTemplate(classSession.id(), teacher.getId());

        assertThat(dropdownValues(template, 8))
                .containsExactly(fixture.assignment().exerciseTitle() + " (" + fixture.assignment().exerciseCode() + ")");
        assertThat(dropdownValues(template, 9))
                .containsExactly(video.set().title() + " (" + video.set().code() + ")");
    }

    @Test
    void importComments_V55_MainFlow_resolvesGrammarAssignmentByUuid() throws IOException {
        GrammarFixture fixture = createGrammarOnlineAssignment();
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "",
                        "Nội dung.", fixture.assignment().uuid().toString(), "", ""}
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.homeworkNextExerciseAssignmentId()).isEqualTo(fixture.assignment().id());
        assertThat(saved.homeworkNext()).isNull();
    }

    @Test
    void importComments_V55_MainFlow_resolvesGrammarAssignmentByDropdownLabel() throws IOException {
        GrammarFixture fixture = createGrammarOnlineAssignment();
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        String label = fixture.assignment().exerciseTitle() + " (" + fixture.assignment().exerciseCode() + ")";
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "",
                        "Nội dung.", label, "", ""}
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.homeworkNextExerciseAssignmentId()).isEqualTo(fixture.assignment().id());
        assertThat(saved.homeworkNext()).isNull();
    }

    /** Gộp cột: không khớp đề nào trong kho thì KHÔNG báo lỗi — coi là text thông báo offline như cũ. */
    @Test
    void importComments_V55_MainFlow_fallsBackToOfflineTextWhenGrammarSelectionUnmatched() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "",
                        "Nội dung.", "Ôn lại Unit 3 ở nhà", "", ""}
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.homeworkNext()).isEqualTo("Ôn lại Unit 3 ở nhà");
        assertThat(saved.homeworkNextExerciseAssignmentId()).isNull();
    }

    /** Cột "BTVN Nghe-nói buổi sau" (chỉ đổi tên, vẫn thuần online) — vẫn báo lỗi khi không khớp, không fallback text như cột Ngữ pháp. */
    @Test
    void importComments_V55_A_rejectsUnmatchedVideoSelectionWithRowError() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "",
                        "Nội dung.", "", "Bộ video không tồn tại (XX)", ""}
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("Không khớp bộ video");
    }

    @Test
    void buildTemplate_V56_MainFlow_exportsHomeworkPreviousSpeakingScoreIndependentlyFromGrammarScore() throws IOException {
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", classSession.id(), null,
                        classSession.sessionDate(), "Nội dung.", null, null, false, null, "80%", "60%", null, null, null, null),
                teacher.getId());

        byte[] template = studentCommentService.buildTemplate(classSession.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), 5)).isEqualTo("80%");
        assertThat(rowForStudent(template, student.getStudentCode(), 6)).isEqualTo("60%");
    }

    @Test
    void importComments_V56_MainFlow_importsHomeworkPreviousSpeakingScoreIndependentlyFromGrammarScore() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "80%", "60%", "Nội dung.", "", "", ""}
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.homeworkPreviousScore()).isEqualTo("80%");
        assertThat(saved.homeworkPreviousSpeakingScore()).isEqualTo("60%");
    }

    // ===================== Gộp cột "buổi trước" (auto + manual, ghi đè tay thắng) =====================

    /** Ghi đè tay thắng: nếu GV đã nhập tay ở buổi này thì hiện đúng giá trị nhập, không phải % tự động (dù % tự động khác). */
    @Test
    void buildTemplate_MainFlow_manualOverrideWinsOverAutoGrammarPercent() throws IOException {
        GrammarFixture fixture = createGrammarOnlineAssignment();
        writeDailyCommentWithHomeworkNext(student, classSession, fixture.assignment().id(), null);
        answerGrammarCorrectly(fixture);
        ClassSessionResponse session2 = nextSession();
        writeDailyCommentWithHomeworkPrevious(student, session2, "50% (tay)");

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), 5)).isEqualTo("50% (tay)");
    }

    /** Nhập giá trị ghi đè qua Excel rồi tải lại mẫu — giá trị ghi đè phải còn nguyên, không bị % tự động ghi đè ngược lại. */
    @Test
    void importComments_MainFlow_persistsManualOverrideForGrammarPreviousGoingForward() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "45% (tay)", "",
                        "Nội dung.", "", "", ""}
        });

        studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.homeworkPreviousScore()).isEqualTo("45% (tay)");

        byte[] template = studentCommentService.buildTemplate(classSession.id(), teacher.getId());
        assertThat(rowForStudent(template, student.getStudentCode(), 5)).isEqualTo("45% (tay)");
    }

    // ===================== UC-64: học sinh tự xem nhận xét của chính mình =====================

    @Test
    void listMyComments_UC64_MainFlow_onlyReturnsApprovedForOwnClass() {
        // DAILY nay dùng chung luồng DRAFT->Gửi->PENDING->duyệt (2026-07-29) -- ghi rồi phải Gửi+duyệt mới APPROVED.
        StudentCommentResponse toApprove = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", classSession.id(), null,
                        classSession.sessionDate(), "Nội dung đã duyệt.", null, null, false, null, null, null, null, null, null, null),
                teacher.getId());
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(toApprove.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(toApprove.id()), "APPROVED", null), siteManagerUser.getId());
        ClassSessionResponse session2 = nextSession();
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", session2.id(), null,
                        session2.sessionDate(), "Nội dung chờ duyệt.", null, null, false, null, null, null, null, null, null, null),
                teacher.getId());

        List<StudentCommentResponse> result = studentCommentService.listMyComments(schoolClass.id(), student.getUser().getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("APPROVED");
        assertThat(result.get(0).content()).isEqualTo("Nội dung đã duyệt.");
    }

    @Test
    void listMyComments_rejectsWhenNotEnrolledInClass() {
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "9A1", siteOf(schoolClass).getId(), schoolClass.curriculumId(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());

        assertThatThrownBy(() -> studentCommentService.listMyComments(otherClass.id(), student.getUser().getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void writeDailyCommentWithHomeworkPrevious(Student targetStudent, ClassSessionResponse session, String homeworkPreviousScore) {
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(targetStudent.getId(), "DAILY", session.id(), null,
                        session.sessionDate(), "Nội dung buổi.", null, null, false, null, homeworkPreviousScore, null, null, null, null, null),
                teacher.getId());
    }

    private String exerciseCode() {
        return "EX-" + SEQ.incrementAndGet();
    }

    private String bankCode() {
        return "QB-" + SEQ.incrementAndGet();
    }

    private String setCode() {
        return "RVS-" + SEQ.incrementAndGet();
    }

    private byte[] buildCommentWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("NhanXet");
            Row header = sheet.createRow(0);
            String[] headers = {"Ngày", "Mã học viên", "Họ và tên", "Điểm danh", "Thái độ học tập",
                    "BTVN Ngữ pháp buổi trước", "BTVN Nghe-nói buổi trước", "Nhận xét học sinh",
                    "BTVN Ngữ pháp buổi sau", "BTVN Nghe-nói buổi sau", "Ghi chú"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private StudentCommentResponse writeDailyComment(User actor, String content) {
        return studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", classSession.id(), null,
                        LocalDate.now(), content, null, null, false, null, null, null, null, null, null, null),
                actor.getId());
    }

    private StudentCommentResponse writeMidTermComment() {
        return studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "MID_TERM", null, gradePeriod.id(),
                        LocalDate.now(), "Nội dung nhận xét giữa kỳ.", null, null, false, null, null, null, null, null, null, null),
                teacher.getId());
    }

    private Site siteOf(ClassResponse classResponse) {
        return siteRepository.findById(classResponse.siteId()).orElseThrow();
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

    private Room newRoom(Site site) {
        Room r = new Room();
        r.setSite(site);
        r.setCode("ROOM-" + SEQ.incrementAndGet());
        r.setName("Test Room");
        r.setRoomType(Room.RoomType.THEORY);
        r.setCapacity(30);
        r.setFlexible(false);
        return roomRepository.save(r);
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
