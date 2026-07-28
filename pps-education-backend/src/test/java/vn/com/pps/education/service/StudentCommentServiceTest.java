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
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DailyCommentImportResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateStudentCommentRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.InvalidCommentContextException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
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
 * Nhận xét Hàng ngày kiểu mới (comment_type=DAILY — bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng 2026-07-24): ghi xong tự động chuyển
 * PENDING (Giáo viên thường, chờ Quản lý điểm trường — UC-22 không đổi)
 * hoặc APPROVED ngay (actor có academic.comment.approve), không còn bước
 * DRAFT→submit riêng. Giữa/Cuối kỳ (MID_TERM/END_TERM) giữ nguyên 100%
 * luồng DRAFT→submit→PENDING cũ.
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
    void writeComment_UC21_MainFlow_dailyCommentAutoRoutesToPendingForTeacherAndNotifiesSiteManager() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Chăm chỉ, tích cực phát biểu.");

        assertThat(comment.status()).isEqualTo("PENDING");
        assertThat(comment.commentType()).isEqualTo("DAILY");
        assertThat(comment.classSessionId()).isEqualTo(classSession.id());
        assertThat(comment.severity()).isEqualTo("NORMAL");
        assertThat(studentCommentService.listPendingForSite(siteManagerUser.getId()))
                .extracting(StudentCommentResponse::id).contains(comment.id());
    }

    @Test
    void writeComment_UC21_dailyCommentAutoApprovedForActorWithApprovePermission() {
        StudentCommentResponse comment = writeDailyComment(siteManagerUser, "Nội dung do quản lý nhập.");

        assertThat(comment.status()).isEqualTo("APPROVED");
        assertThat(comment.visibleToParentAt()).isNotNull();
        assertThat(studentCommentService.listPendingForSite(siteManagerUser.getId())).isEmpty();
    }

    @Test
    void writeComment_UC21_MainFlow_savesMidTermCommentWithWarningFlag() {
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "MID_TERM", null, gradePeriod.id(),
                        LocalDate.now(), "Cần cải thiện kỹ năng nghe.", null, "CONCERN", true, null, null, null, null),
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
                        LocalDate.now(), "Nội dung", null, null, false, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(InvalidCommentContextException.class);
    }

    @Test
    void writeComment_rejectsInvalidContextForMidTermWithSessionInsteadOfPeriod() {
        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "MID_TERM", classSession.id(), null,
                        LocalDate.now(), "Nội dung", null, null, false, null, null, null, null),
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
                        oldSession.sessionDate(), "Nội dung", null, null, false, null, null, null, null),
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
                        oldSession.sessionDate(), "Nội dung do quản lý nhập ngoài hạn.", null, null, false, null, null, null, null),
                siteManagerUser.getId());

        assertThat(comment.status()).isEqualTo("APPROVED");
    }

    @Test
    void updateComment_UC21_dailyCommentEditableEvenWhenPendingWithinWindow() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");
        assertThat(comment.status()).isEqualTo("PENDING");

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung đã sửa.", null, null, false, "GOOD", "80%", "Unit 4", "Ghi chú"),
                teacher.getId());

        assertThat(edited.status()).isEqualTo("PENDING");
        assertThat(edited.content()).isEqualTo("Nội dung đã sửa.");
        assertThat(edited.attitude()).isEqualTo("GOOD");
        assertThat(edited.homeworkPreviousScore()).isEqualTo("80%");
        assertThat(edited.homeworkNext()).isEqualTo("Unit 4");
        assertThat(edited.note()).isEqualTo("Ghi chú");
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

    @Test
    void decideComments_UC22_MainFlow_approvedMakesVisibleToParent() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung nhận xét.");

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
                        LocalDate.now(), "Nhận xét HS2.", null, null, false, null, null, null, null),
                teacher.getId());

        List<StudentCommentResponse> decided = studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment1.id(), comment2.id()), "APPROVED", null), siteManagerUser.getId());

        assertThat(decided).hasSize(2);
        assertThat(decided).allSatisfy(c -> assertThat(c.status()).isEqualTo("APPROVED"));
        assertThat(studentCommentService.listPendingForSite(siteManagerUser.getId())).isEmpty();
    }

    @Test
    void decideComments_UC22_MainFlow_rejectedReturnsToTeacherWithReasonAndUC21_A1_editableAgain() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");

        List<StudentCommentResponse> decided = studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "REJECTED", "Nội dung chưa rõ ràng"), siteManagerUser.getId());
        assertThat(decided.get(0).status()).isEqualTo("REJECTED");
        assertThat(decided.get(0).visibleToParentAt()).isNull();

        // DAILY: sửa lại sau khi bị từ chối -- tự động quay lại PENDING ngay (không còn bước submit riêng).
        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung đã sửa lại.", null, null, false, null, null, null, null),
                teacher.getId());
        assertThat(edited.status()).isEqualTo("PENDING");
    }

    @Test
    void updateComment_midTerm_rejectsWhenPending() {
        StudentCommentResponse comment = writeMidTermComment();
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        assertThatThrownBy(() -> studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Sửa khi đang chờ duyệt.", null, null, false, null, null, null, null),
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
                    "Thái độ học tập", "BTVN buổi trước", "Nhận xét học sinh*", "BTVN buổi sau", "Ghi chú");
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
    void importComments_UC21_MainFlow_teacherImportRoutesToPending() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "Tốt", "80%", "Rất tốt.", "Unit 5", "Không có gì."}
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
        List<StudentCommentResponse> comments = studentCommentService.listComments(schoolClass.id(), student.getId());
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).status()).isEqualTo("PENDING");
        assertThat(comments.get(0).attitude()).isEqualTo("GOOD");
        assertThat(comments.get(0).homeworkPreviousScore()).isEqualTo("80%");
    }

    @Test
    void importComments_UC21_approverImportAutoApproves() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "Quản lý nhập trực tiếp.", "", ""}
        });

        studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), siteManagerUser.getId());

        List<StudentCommentResponse> comments = studentCommentService.listComments(schoolClass.id(), student.getId());
        assertThat(comments.get(0).status()).isEqualTo("APPROVED");
        assertThat(comments.get(0).visibleToParentAt()).isNotNull();
    }

    @Test
    void importComments_UC21_skipsAbsentStudentWithBlankCommentFields() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "ABSENT", null, null, "Ốm"))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Vắng", "", "", "", "", ""}
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
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "", "", ""}
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
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Có mặt", "", "", "Đã đi học lại.", "", ""}
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
                {classSession.sessionDate().toString(), student.getStudentCode(), "", "Muộn", "", "", "Đến muộn.", "", ""},
                {classSession.sessionDate().toString(), student2.getStudentCode(), "", "Có mặt", "", "", "Bình thường.", "", ""},
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), siteManagerUser.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("Không sửa được điểm danh");
        // student2 (không đổi điểm danh) vẫn được duyệt luôn vì actor là approver.
        assertThat(studentCommentService.listComments(schoolClass.id(), student2.getId()).get(0).status()).isEqualTo("APPROVED");
    }

    private byte[] buildCommentWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("NhanXet");
            Row header = sheet.createRow(0);
            String[] headers = {"Ngày", "Mã học viên", "Họ và tên", "Điểm danh", "Thái độ học tập",
                    "BTVN buổi trước", "Nhận xét học sinh", "BTVN buổi sau", "Ghi chú"};
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
                        LocalDate.now(), content, null, null, false, null, null, null, null),
                actor.getId());
    }

    private StudentCommentResponse writeMidTermComment() {
        return studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "MID_TERM", null, gradePeriod.id(),
                        LocalDate.now(), "Nội dung nhận xét giữa kỳ.", null, null, false, null, null, null, null),
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
