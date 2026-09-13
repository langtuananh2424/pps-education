package vn.com.pps.education.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExerciseAssignment;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Room;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.SitePeriodTemplate;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddExerciseQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.ApplyClassHomeworkRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateExamQuestionRequest;
import vn.com.pps.education.dto.CreateExamRequest;
import vn.com.pps.education.dto.CreateExerciseRequest;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DailyCommentImportResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.ExerciseResponse;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.SubmitConnectionAnswersRequest;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.SaveAnswerRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.dto.UpdateStudentCommentContentRequest;
import vn.com.pps.education.dto.UpdateStudentCommentRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.MissingCommentContentException;
import vn.com.pps.education.exception.MissingLessonContentException;
import vn.com.pps.education.exception.NoUpcomingClassSessionException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.StudentCommentNotEditableException;
import vn.com.pps.education.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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

    // Khớp đúng HomeworkColumns.of(VIETNAMESE) của StudentCommentService (V130, 19 cột — mọi
    // classSession dựng trong file test này đều teacherType=VIETNAMESE, xem setUp()/nextSession()) —
    // dùng hằng số thay vì số ma thuật để tránh sai lệch khi buildCommentWorkbook()/rowForStudent()
    // tham chiếu vị trí cột. Trước V130 (2026-08-21) đây là layout FOREIGN 17 cột (Offline gộp 1
    // cột/nhóm) — buổi VIETNAMESE giờ tách thêm Reading/Writing riêng ở cả 2 nhóm BTVN.
    // V137 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23): thêm 2 cột "% tự động
    // Reading/Writing online" mỗi nhóm BTVN (buổi VIETNAMESE) — CHỈ hiển thị ở buildTemplate() (xuất),
    // KHÔNG đọc lúc importComments() (xem StudentCommentService#parseRow) — đẩy lùi mọi cột phía sau
    // thêm 2 vị trí so với layout 19 cột cũ (khớp HomeworkColumns trong StudentCommentService).
    private static final int COL_DATE = 0;
    private static final int COL_STUDENT_CODE = 1;
    private static final int COL_FULL_NAME = 2;
    private static final int COL_DOB = 3;
    private static final int COL_LESSON_CONTENT = 4;
    private static final int COL_TEACHER_NAME = 5;
    private static final int COL_ATTENDANCE = 6;
    private static final int COL_HOMEWORK_READING_PREVIOUS = 7;
    private static final int COL_HOMEWORK_WRITING_PREVIOUS = 8;
    private static final int COL_HOMEWORK_READING_ONLINE_PREVIOUS = 9;
    private static final int COL_HOMEWORK_WRITING_ONLINE_PREVIOUS = 10;
    private static final int COL_HOMEWORK_GRAMMAR_PREVIOUS = 11;
    private static final int COL_HOMEWORK_SPEAKING_PREVIOUS = 12;
    private static final int COL_HOMEWORK_READING_NEXT = 13;
    private static final int COL_HOMEWORK_WRITING_NEXT = 14;
    private static final int COL_HOMEWORK_READING_ONLINE_NEXT = 15;
    private static final int COL_HOMEWORK_WRITING_ONLINE_NEXT = 16;
    private static final int COL_HOMEWORK_GRAMMAR_NEXT = 17;
    private static final int COL_HOMEWORK_VIDEO_NEXT = 18;
    private static final int COL_DUE_DATE = 19;
    private static final int COL_ATTITUDE = 20;
    private static final int COL_CONTENT = 21;
    private static final int COL_NOTE = 22;

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
    private ExerciseService exerciseService;

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamQuestionService examQuestionService;

    @Autowired
    private ExerciseAttemptService exerciseAttemptService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private ReviewVideoService reviewVideoService;

    @Autowired
    private StudentService studentService;

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
    private SiteManagerRepository siteManagerRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ExerciseAssignmentRepository exerciseAssignmentRepository;

    @Autowired
    private ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;

    private User headAcademic;
    private User teacher;
    private User siteManagerUser;
    private ClassResponse schoolClass;
    private Student student;
    private ClassSessionResponse classSession;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

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
        // Cửa sổ bao quanh NGAY LÚC NÀY (thay vì giờ cố định 08:00-09:40) từ 2026-08-14 để né lệch
        // múi giờ CI (JVM UTC ở GitHub Actions >< máy dev Asia/Ho_Chi_Minh) và vi phạm CHECK
        // chk_session_time gần nửa đêm (xem lịch sử PR). requireSessionEndedAndAttendanceTaken (yêu
        // cầu buổi đã kết thúc + đã điểm danh xong trước khi viết nhận xét) đã bị BỎ HẲN 2026-08-18
        // (xem docs/uc/phan-he-06-hoc-thuat.md, UC-21) — buổi/điểm danh dưới đây không còn là điều
        // kiện bắt buộc, chỉ giữ lại vì nhiều test khác vẫn tiện dùng dữ liệu buổi đã điểm danh sẵn.
        // SỬA LẠI 2026-08-18: đổi từ cửa sổ ĐÃ KẾT THÚC (now-1h..now-1min) sang cửa sổ BAO QUANH now
        // (now-1min..now+1h), vì markAttendance() ở dưới giờ đòi buổi đang TRONG khung giờ diễn ra
        // (UC-15, sửa đổi nghiệp vụ 2026-08-18) — buổi đã kết thúc sẽ bị StudentAttendanceService
        // từ chối ngay tại đây.
        seedPeriod(site, 1, LocalTime.now().minusMinutes(1), LocalTime.now().plusHours(1));
        seedPeriod(site, 2, LocalTime.of(8, 0), LocalTime.of(9, 40));
        classSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), "MORNING", List.of(1), room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null),
                headAcademic.getId());
        // Bài học hôm nay mặc định đã điền — bắt buộc để submitComments() cho DAILY không bị
        // chặn bởi MissingLessonContentException (bổ sung ngoài SDD gốc, đã xác nhận với người
        // dùng 2026-07-29) trừ khi 1 test cụ thể cố tình test thiếu bài học.
        studentCommentService.updateLessonContent(classSession.id(), "Unit 1: Present simple tense.", teacher.getId());
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        studentAttendanceService.submitAttendance(classSession.id(), teacher.getId());
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
    void writeComment_rejectsWhenActorNotAssignedTeacherNorApprover() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> writeDailyComment(outsider, "Nội dung"))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    /** V107: quyền academic.comment.manage cho phép quản trị viên viết nhận xét của lớp bất kỳ (không cần là GV được phân công). */
    @Test
    void writeComment_allowsAdminWithManagePermissionBypassingAssignedTeacherCheck() {
        User admin = newUser("comment.admin");
        assignRole(admin, "SYS_ADMIN");

        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(), LocalDate.now(), "Nội dung do quản trị viên nhập hộ.", null, null, false, null, null, null, null, null, null, null, null, null),
                admin.getId());

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.commentType()).isEqualTo("DAILY");
    }

    @Test
    void writeComment_UC21_dailyCommentBlockedAfterEditWindowForTeacher() {
        Room room = newRoom(siteOf(schoolClass));
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().minusDays(8), "MORNING", List.of(2), room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), oldSession.id(), oldSession.sessionDate(), "Nội dung", null, null, false, null, null, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void writeComment_UC21_approverBypassesEditWindow() {
        Room room = newRoom(siteOf(schoolClass));
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().minusDays(8), "MORNING", List.of(2), room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null),
                headAcademic.getId());

        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), oldSession.id(), oldSession.sessionDate(), "Nội dung do quản lý nhập ngoài hạn.", null, null, false, null, null, null, null, null, null, null, null, null),
                siteManagerUser.getId());

        assertThat(comment.status()).isEqualTo("DRAFT");
    }

    /**
     * Bổ sung ngoài SDD gốc, xác nhận 2026-08-19 — trước đây writeComment() tạo StudentComment mới VÔ
     * ĐIỀU KIỆN, nên 2 lần gọi liên tiếp cho CÙNG (buổi, học sinh) khi bản đầu còn DRAFT (VD race giữa
     * request "Lưu nháp" cũ chưa kịp phản hồi và request "Gửi nhận xét" mới, hoặc gọi lại do timeout)
     * sinh ra 2 bản ghi trùng — vỡ previousComment()/buildTemplate() (đều giả định tối đa 1 dòng cho
     * mỗi buổi+học sinh). Giờ lần gọi sau phải SỬA ĐÈ đúng bản DRAFT đã có, không tạo thêm.
     */
    @Test
    void writeComment_boSung_secondCallForSameSessionAndStudentUpdatesExistingDraftInsteadOfDuplicating() {
        StudentCommentResponse first = writeDailyComment(teacher, "Nội dung lần 1.");

        StudentCommentResponse second = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(), classSession.sessionDate(), "Nội dung lần 2.", null, null, false, null, null, null, null, null, null, null, null, null),
                teacher.getId());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.content()).isEqualTo("Nội dung lần 2.");
        assertThat(studentCommentService.listComments(schoolClass.id(), student.getId())).hasSize(1);
    }

    /** Mirror test trên, khác nhánh: buổi+học sinh đó ĐÃ có nhận xét đang chờ duyệt (PENDING) — không được ghi đè/tạo thêm, phải báo lỗi rõ ràng. */
    @Test
    void writeComment_boSung_rejectsWhenSessionAlreadyHasPendingComment() {
        StudentCommentResponse first = writeDailyComment(teacher, "Nội dung đã gửi.");
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(first.id())), teacher.getId());

        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(), classSession.sessionDate(), "Nội dung khác.", null, null, false, null, null, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void updateComment_UC21_MainFlow_editableWhileDraft() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");
        assertThat(comment.status()).isEqualTo("DRAFT");

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung đã sửa.", null, null, false, "GOOD", "80%", "60%", null, null, "Unit 4", null, null, "Ghi chú"),
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
                new UpdateStudentCommentRequest("Sửa khi đang chờ duyệt.", null, null, false, null, null, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void updateComment_UC21_V56_homeworkPreviousSpeakingScoreIndependentFromGrammarScore() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung.", null, null, false, null, null, "70%", null, null, null, null, null, null),
                teacher.getId());

        assertThat(edited.homeworkPreviousSpeakingScore()).isEqualTo("70%");
        assertThat(edited.homeworkPreviousScore()).isNull();
    }

    @Test
    void submitComments_UC21_MainFlow_dailyTransitionsToPendingAndNotifiesSiteManager() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung nhận xét.");

        List<StudentCommentResponse> submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        assertThat(submitted).hasSize(1);
        assertThat(submitted.get(0).status()).isEqualTo("PENDING");
        assertThat(studentCommentService.listPendingForSite(siteManagerUser.getId()))
                .extracting(StudentCommentResponse::id).contains(comment.id());
    }

    @Test
    void submitComments_rejectsWhenNotDraft() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung nhận xét.");
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

    // ===================== Nới lỏng content bắt buộc lúc lưu nháp (bổ sung ngoài SDD gốc, 2026-08-17) =====================

    @Test
    void writeComment_boSung_savesDraftWithoutContent() {
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(), LocalDate.now(), "", null, null, false, "GOOD", null, null, null, null, null, null, null, null),
                teacher.getId());

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.content()).isEmpty();
        assertThat(comment.attitude()).isEqualTo("GOOD");
    }

    /** applyContent phải ghi "" thay vì null khi FE gửi content=null — content cột DB vẫn NOT NULL (V15). */
    @Test
    void writeComment_boSung_coercesNullContentToEmptyString() {
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(), LocalDate.now(), null, null, null, false, null, null, null, null, null, null, null, null, "Chỉ ghi chú"),
                teacher.getId());

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.content()).isEmpty();
        assertThat(comment.note()).isEqualTo("Chỉ ghi chú");
    }

    @Test
    void updateComment_boSung_savesDraftWithoutContent() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("", null, null, false, "EXCELLENT", null, null, null, null, null, null, null, null),
                teacher.getId());

        assertThat(edited.status()).isEqualTo("DRAFT");
        assertThat(edited.content()).isEmpty();
        assertThat(edited.attitude()).isEqualTo("EXCELLENT");
    }

    @Test
    void submitComments_boSung_rejectsWhenContentBlank() {
        StudentCommentResponse comment = writeDailyComment(teacher, "");

        assertThatThrownBy(() -> studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()))
                .isInstanceOf(MissingCommentContentException.class);
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
                new CreateStudentCommentRequest(student2.getId(), classSession.id(), LocalDate.now(), "Nhận xét HS2.", null, null, false, null, null, null, null, null, null, null, null, null),
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
                new UpdateStudentCommentRequest("Nội dung đã sửa lại.", null, null, false, null, null, null, null, null, null, null, null, null),
                teacher.getId());
        assertThat(edited.status()).isEqualTo("DRAFT");
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
    void updatePendingCommentContent_UC22_boSung_MainFlow_updatesContentAndKeepsPending() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung cũ.");
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        StudentCommentResponse updated = studentCommentService.updatePendingCommentContent(comment.id(),
                new UpdateStudentCommentContentRequest("Nội dung đã sửa bởi QLĐT", null), siteManagerUser.getId());

        assertThat(updated.content()).isEqualTo("Nội dung đã sửa bởi QLĐT");
        assertThat(updated.status()).isEqualTo("PENDING");
        assertThat(studentCommentService.listPendingForSite(siteManagerUser.getId())).extracting(StudentCommentResponse::id).contains(comment.id());
    }

    @Test
    void updatePendingCommentContent_UC22_boSung_A1_rejectsWhenNotPending() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung cũ.");

        assertThatThrownBy(() -> studentCommentService.updatePendingCommentContent(comment.id(),
                new UpdateStudentCommentContentRequest("Sửa thử khi DRAFT", null), siteManagerUser.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void updatePendingCommentContent_UC22_boSung_A2_rejectsWhenNotSiteManagerForSite() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung cũ.");
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());
        User outsiderManager = newUser("outsider.sitemanager");
        assignRole(outsiderManager, "SITE_MANAGER");

        assertThatThrownBy(() -> studentCommentService.updatePendingCommentContent(comment.id(),
                new UpdateStudentCommentContentRequest("Thử sửa chui", null), outsiderManager.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    // ===================== Case 3: "Bài học hôm nay" chuyển sang Nhận xét (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29) =====================

    @Test
    void updateLessonContent_boSung_savesForAssignedTeacher() {
        var result = studentCommentService.updateLessonContent(classSession.id(), "Unit 2: Past simple tense.", teacher.getId());

        assertThat(result.classSessionId()).isEqualTo(classSession.id());
        assertThat(result.lessonContent()).isEqualTo("Unit 2: Past simple tense.");
    }

    @Test
    void updateLessonContent_boSung_rejectsWhenNotAssignedTeacher() {
        User outsider = newUser("outsider.teacher.lesson");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> studentCommentService.updateLessonContent(classSession.id(), "Nội dung", outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void buildTemplate_boSung_lessonContentColumnPrefilledFromUiValue() throws IOException {
        studentCommentService.updateLessonContent(classSession.id(), "Unit 3: Future simple tense.", teacher.getId());

        byte[] template = studentCommentService.buildTemplate(classSession.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), 4)).isEqualTo("Unit 3: Future simple tense.");
    }

    @Test
    void importComments_boSung_appliesConsistentLessonContentAcrossRows() throws IOException {
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "Unit 4: Present continuous.", "",
                        "Có mặt", "", "", "", "Tốt.", "", "", "", "", "", ""),
                commentRow(classSession.sessionDate().toString(), student2.getStudentCode(), "Unit 4: Present continuous.", "",
                        "Có mặt", "", "", "", "Tốt.", "", "", "", "", "", ""),
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        List<ClassSessionResponse> sessions = classSessionService.listSessions(schoolClass.id(), teacher.getId());
        assertThat(sessions).filteredOn(s -> s.id().equals(classSession.id()))
                .extracting(ClassSessionResponse::lessonContent).containsExactly("Unit 4: Present continuous.");
    }

    @Test
    void importComments_boSung_rejectsWholeFileWhenLessonContentInconsistent() throws IOException {
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "Unit 4: Present continuous.", "",
                        "Có mặt", "", "", "", "Tốt.", "", "", "", "", "", ""),
                commentRow(classSession.sessionDate().toString(), student2.getStudentCode(), "Unit 5: Past continuous.", "",
                        "Có mặt", "", "", "", "Tốt.", "", "", "", "", "", ""),
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorSummary().get(0).get("reason").toString()).contains("không đồng nhất");
        assertThat(studentCommentService.listComments(schoolClass.id(), student.getId())).isEmpty();
        assertThat(studentCommentService.listComments(schoolClass.id(), student2.getId())).isEmpty();
    }

    @Test
    void importComments_boSung_leavesLessonContentUnchangedWhenColumnBlank() throws IOException {
        studentCommentService.updateLessonContent(classSession.id(), "Unit 1: Present simple tense.", teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Tốt.", "", "", "", "", "", ""),
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        List<ClassSessionResponse> sessions = classSessionService.listSessions(schoolClass.id(), teacher.getId());
        assertThat(sessions).filteredOn(s -> s.id().equals(classSession.id()))
                .extracting(ClassSessionResponse::lessonContent).containsExactly("Unit 1: Present simple tense.");
    }

    @Test
    void submitComments_boSung_rejectsDailyCommentWhenLessonContentMissing() throws IOException {
        Room room = newRoom(siteOf(schoolClass));
        ClassSessionResponse sessionWithoutLesson = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", List.of(2), room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null),
                headAcademic.getId());
        // siteManagerUser (có academic.comment.approve) thay vì teacher -- bổ sung 2026-08-14, sửa CI
        // fail: sessionWithoutLesson cố tình ở TƯƠNG LAI (plusDays(1), chưa "kết thúc") để tách biệt với
        // classSession của setUp() đã có sẵn lesson content; requireSessionEndedAndAttendanceTaken chặn
        // GV thường viết nhận xét buổi chưa kết thúc, nhưng actor có quyền duyệt thì bỏ qua rào này
        // (đúng như đã bỏ qua rào hạn 7 ngày/GV được phân công) -- không ảnh hưởng gì tới điều đang test
        // (MissingLessonContentException ở submitComments(), method không gọi rào này).
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), sessionWithoutLesson.id(), sessionWithoutLesson.sessionDate(), "Nội dung.", null, null, false, null, null, null, null, null, null, null, null, null),
                siteManagerUser.getId());

        assertThatThrownBy(() -> studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()))
                .isInstanceOf(MissingLessonContentException.class);
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
            // headerGroups + headerSubGroups (V130, 2026-08-21 -- buổi teacherType=VIETNAMESE, mọi
            // classSession trong file test này) -- header 3 dòng: row 0 = nhãn nhóm cấp 1 ("BTVN buổi
            // trước"/"BTVN buổi này", chỉ ở cột đầu nhóm) + row 1 = nhãn nhóm cấp 2 (Offline/Online,
            // chỉ ở cột đầu nhóm con) + row 2 = tên cột con, dữ liệu bắt đầu từ row 3. Cột không thuộc
            // nhóm nào (VD "Ngày*"/"Điểm danh*") merge dọc cả 3 dòng, giá trị ở row 0. Chỉ spot-check
            // vài cột tiêu biểu, không phải trọng tâm của test này.
            Row groupHeader = sheet.getRow(0);
            Row subGroupHeader = sheet.getRow(1);
            Row leafHeader = sheet.getRow(2);
            assertThat(groupHeader.getCell(0).getStringCellValue()).isEqualTo("Ngày*");
            assertThat(groupHeader.getCell(6).getStringCellValue()).isEqualTo("Điểm danh*");
            assertThat(groupHeader.getCell(COL_HOMEWORK_READING_PREVIOUS).getStringCellValue()).isEqualTo("BTVN buổi trước");
            assertThat(groupHeader.getCell(COL_HOMEWORK_READING_NEXT).getStringCellValue()).isEqualTo("BTVN buổi này");
            assertThat(subGroupHeader.getCell(COL_HOMEWORK_READING_PREVIOUS).getStringCellValue()).isEqualTo("Offline");
            assertThat(subGroupHeader.getCell(COL_HOMEWORK_READING_ONLINE_PREVIOUS).getStringCellValue()).isEqualTo("Online");
            // classSession giờ luôn có teacherType=VIETNAMESE -- nhãn kênh riêng "Từ vựng + Ngữ pháp"
            // (VIETNAMESE_ONLINE_GRAMMAR_LABEL, V130) thay vì grammarChannelLabel dùng chung.
            assertThat(leafHeader.getCell(COL_HOMEWORK_GRAMMAR_PREVIOUS).getStringCellValue()).isEqualTo("Từ vựng + Ngữ pháp");
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            Row row = sheet.getRow(3);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo(student.getStudentCode());
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo(student.getDateOfBirth().toString());
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("Có mặt");

            List<? extends org.apache.poi.ss.usermodel.DataValidation> validations = sheet.getDataValidations();
            assertThat(validations).hasSize(2);
            assertThat(validations).anySatisfy(v -> {
                assertThat(v.getRegions().getCellRangeAddress(0).getFirstColumn()).isEqualTo(6);
                assertThat(v.getValidationConstraint().getExplicitListValues())
                        .containsExactly("Có mặt", "Vắng", "Có phép", "Muộn", "Về sớm");
            });
            assertThat(validations).anySatisfy(v -> {
                assertThat(v.getRegions().getCellRangeAddress(0).getFirstColumn()).isEqualTo(COL_ATTITUDE);
                assertThat(v.getValidationConstraint().getExplicitListValues())
                        .containsExactly("Yếu", "Trung bình", "Khá", "Tốt", "Xuất sắc");
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
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "80%", "", "Rất tốt.", "", "", "", "", "Tốt", "Không có gì.")
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

    /** Thang thái độ chốt lại 2026-08-12 (Yếu/Trung bình/Khá/Tốt/Xuất sắc) — "Xuất sắc" là mức mới, phủ riêng 1 test. */
    @Test
    void importComments_UC21_MainFlow_parsesExcellentAttitude() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Xuất sắc.", "", "", "", "", "Xuất sắc", "")
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successRows()).isEqualTo(1);
        List<StudentCommentResponse> comments = studentCommentService.listComments(schoolClass.id(), student.getId());
        assertThat(comments.get(0).attitude()).isEqualTo("EXCELLENT");
    }

    @Test
    void importComments_UC21_approverImportAlsoSavesAsDraft() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Quản lý nhập trực tiếp.", "", "", "", "", "", "")
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
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Vắng", "", "", "", "", "", "", "", "", "", "")
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
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "", "", "", "", "", "", "")
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
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Đã đi học lại.", "", "", "", "", "", "")
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
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Muộn", "", "", "", "Đến muộn.", "", "", "", "", "", ""),
                commentRow(classSession.sessionDate().toString(), student2.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Bình thường.", "", "", "", "", "", ""),
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
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Rất tốt.", "", "", "", "", "", "")
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
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Rất tốt.", "", "", "", "", "", "")
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

    // ===================== V55/V65 (giao bài qua nhận xét, ĐÃ THAY THẾ 2026-09-12): BTVN online/offline =====================
    // Bổ sung 2026-09-12 (đã xác nhận với người dùng) — giao BTVN online (Ngữ pháp/Bài nghe, Video TKN/
    // Clip phản xạ, Reading, Writing) tách hẳn khỏi Viết/Sửa/Gửi nhận xét, chỉ còn qua
    // StudentCommentService#applyHomeworkToClass ("Áp dụng cho cả lớp"). Toàn bộ test cũ ở đây từng
    // exercise cơ chế "chọn đề ở writeComment/updateComment, giao thật lúc submitComments, chặn xung đột
    // giữa các dòng cùng buổi" (V65/V127/V150) đã bị XOÁ (không chỉ thừa mà SAI với model mới — xem
    // Javadoc applyHomeworkToClass) — thay bằng bộ test gọi thẳng applyHomeworkToClass bên dưới. BTVN
    // OFFLINE (chữ tự do, homeworkNext/homeworkNextReading/homeworkNextWriting) KHÔNG thuộc phạm vi tách
    // này, vẫn qua writeComment/updateComment/Excel như cũ.

    private record GrammarFixture(ExerciseResponse exercise, QuestionResponse question) {}

    private record VideoFixture(ReviewVideoSetResponse set, ReviewVideoResponse video) {}

    /**
     * Chỉ tạo + thêm câu hỏi + Publish (đủ điều kiện dùng làm nguồn cho
     * applyHomeworkToClass) — KHÔNG giao lớp ở đây, việc giao chỉ xảy ra khi
     * gọi applyHomework(...)/applyHomeworkToClass bên dưới.
     *
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * Bài giờ thuộc 1 Đề (Exam) — tạo Đề mới + gán cho schoolClass ngay ở
     * đây (applyHomeworkToClass đòi Đề đã gán lớp mới giao được).
     */
    private GrammarFixture createGrammarOnlineExercise() {
        var exam = examService.createExam(
                new CreateExamRequest(examCode(), "Đề Ngữ pháp V55", schoolClass.curriculumId(), "VIETNAMESE", "HOMEWORK", null), teacher.getId());
        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());
        // V75 (Kho đề): mỗi Exam tự sinh 1 QuestionBank nội bộ riêng, không nhận câu hỏi qua
        // QuestionBankService#createQuestion (chỉ dành cho bank "legacy" độc lập) — phải qua
        // ExamQuestionService#createQuestion (tự resolve bank nội bộ theo examId), nếu không
        // ExerciseService#addQuestion từ chối vì câu hỏi khác Kho đề với exercise.exam.
        QuestionResponse question = examQuestionService.createQuestion(exam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY", "She ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", null, false, 1), new QuestionChoiceRequest("B", "goes", null, true, 2)), null, null),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Bài ngữ pháp V55", exam.id(), null,
                        "ASSIGNED", new BigDecimal("1"), null, false, 1, true, null, "VOCAB_GRAMMAR"), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(question.id(), 1, new BigDecimal("1.0")), teacher.getId());
        ExerciseResponse published = exerciseService.publishExercise(exercise.id(), teacher.getId());
        // applyHomeworkToClass gọi homeworkSkillBatchService.assignBatchToClass bên trong bằng
        // PROPAGATION_REQUIRES_NEW — phải commit Đề/Bài vừa tạo trước, nếu không giao dịch lồng
        // không thấy được → FK fail.
        commitCurrentTransactionAndStartNew();
        return new GrammarFixture(published, question);
    }

    private void answerGrammarCorrectly(GrammarFixture fixture) {
        List<ExerciseAssignment> assignments = exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                fixture.exercise().id(), schoolClass.id(), ExerciseAssignment.Status.ACTIVE);
        Long assignmentId = assignments.get(0).getId();
        var attempt = exerciseAttemptService.startAttempt(fixture.exercise().id(), assignmentId, student.getUser().getId());
        Long correctChoiceId = fixture.question().choices().stream().filter(c -> c.isCorrect()).findFirst().orElseThrow().id();
        exerciseAttemptService.saveAnswer(attempt.id(),
                new SaveAnswerRequest(fixture.question().id(), null, List.of(correctChoiceId), null, null), student.getUser().getId());
        exerciseAttemptService.submitAttempt(attempt.id(), student.getUser().getId());
    }

    /**
     * V98 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06):
     * curriculum trên "bộ" giờ CHỈ dùng lọc/tìm kiếm — điều kiện hiển thị
     * DUY NHẤT cho 1 lớp là gán tường minh qua assignToClass (mirror Kho
     * đề), nên phải gán trước khi applyHomeworkToClass mới thành công.
     */
    private VideoFixture createConnectionVideoAssignedToClass(int durationSeconds) {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video V55", "CONNECTION", schoolClass.curriculumId(), "VIETNAMESE", null, 1, null),
                teacher.getId());
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        ReviewVideoSetResponse published = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED", null), teacher.getId());
        ReviewVideoResponse video = reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/v55.mp4",
                        1_000_000L, durationSeconds, 1, null, null, null),
                teacher.getId());
        // applyHomeworkToClass gọi reviewVideoService.deliverToClass bên trong bằng
        // PROPAGATION_REQUIRES_NEW — phải commit Bộ video vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        return new VideoFixture(published, video);
    }

    /** "Áp dụng cho cả lớp" cho buổi mặc định (classSession, actor=teacher, hạn nộp tự động = buổi kế tiếp). */
    private List<StudentCommentResponse> applyHomework(ClassSessionResponse session, Long grammarExamId, Long videoSetId) {
        return studentCommentService.applyHomeworkToClass(session.id(),
                new ApplyClassHomeworkRequest(grammarExamId, videoSetId, null, null, null, null),
                teacher.getId());
    }

    /** Gán 1 giáo viên chính loại FOREIGN cho lớp (UC-18) -- trả về để caller dùng làm primaryTeacherId khi xếp buổi FOREIGN (chọn tay, không còn tự suy ra — xác nhận 2026-08-19). */
    private User assignForeignTeacher() {
        User foreignTeacher = newUser("foreign.teacher");
        assignRole(foreignTeacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(foreignTeacher.getId(), "PRIMARY", null, LocalDate.now(), "FOREIGN"), headAcademic.getId());
        return foreignTeacher;
    }

    private ClassSessionResponse nextSession() {
        Room room2 = newRoom(siteOf(schoolClass));
        return classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(classSession.sessionDate().plusDays(1), "MORNING", List.of(2), room2.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null),
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

    @Test
    void applyHomeworkToClass_MainFlow_showsGrammarOnlinePercentFromPreviousSessionAttempt() throws IOException {
        GrammarFixture fixture = createGrammarOnlineExercise();
        ClassSessionResponse session2 = nextSession();
        applyHomework(classSession, fixture.exercise().examId(), null);
        answerGrammarCorrectly(fixture);

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_GRAMMAR_PREVIOUS)).isEqualTo("100%");
    }

    @Test
    void applyHomeworkToClass_MainFlow_showsNotYetDoneForAssignedButUnattemptedGrammar() throws IOException {
        GrammarFixture fixture = createGrammarOnlineExercise();
        ClassSessionResponse session2 = nextSession();
        applyHomework(classSession, fixture.exercise().examId(), null);

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_GRAMMAR_PREVIOUS)).isEqualTo("Chưa làm bài");
    }

    @Test
    void applyHomeworkToClass_MainFlow_showsVideoWatchPercentFromPreviousSessionAssignment() throws IOException {
        VideoFixture fixture = createConnectionVideoAssignedToClass(100);
        ClassSessionResponse session2 = nextSession();
        List<StudentCommentResponse> applied = applyHomework(classSession, null, fixture.set().id());
        StudentCommentResponse comment = applied.stream().filter(c -> c.studentId().equals(student.getId())).findFirst().orElseThrow();
        Long sessionId = reviewVideoService.startWatchSession(fixture.video().id(), comment.homeworkNextReviewVideoAssignmentId(), student.getUser().getId()).sessionId();
        reviewVideoService.reportProgress(fixture.video().id(), new ReportVideoProgressRequest(sessionId, 100), student.getUser().getId());
        // CONNECTION (V83/V93/V101): % hiển thị ở cột này là viewCount/requiredViewCount (không
        // còn phải % thời lượng đã xem) — xem HomeworkProgressService#connectionPercent. Bổ sung
        // ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 — CONNECTION giờ LUÔN yêu cầu xem HẾT
        // 100% (cố định) mới làm session "qualified", còn cần nộp đủ câu hỏi (rỗng ở đây, video chưa
        // thêm câu hỏi) mới tính vào viewCount. requiredViewCount mặc định 1 → 1 lượt đạt = 100%.
        reviewVideoService.submitConnectionAnswers(sessionId, new SubmitConnectionAnswersRequest(List.of()), student.getUser().getId());

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_SPEAKING_PREVIOUS)).isEqualTo("100%");
    }

    /**
     * Fix bug cốt lõi của lần tách này (bổ sung 2026-09-12, đã xác nhận với người dùng) — trước đây chỉ
     * nhận xét ĐÃ GỬI (có content) mới được gán FK BTVN dù cơ chế giao luôn giao CẢ LỚP, nên học sinh
     * chưa từng viết Nhận xét buổi này vẫn nhận bài thật nhưng xem lại lịch sử nhận xét của mình thì
     * "mất" thông tin BTVN. Giờ applyHomeworkToClass tự tạo 1 StudentComment DRAFT nội dung rỗng cho MỌI
     * học sinh ACTIVE của lớp (kể cả chưa từng viết Nhận xét) và gán đúng FK.
     */
    @Test
    void applyHomeworkToClass_MainFlow_createsDraftCommentForEveryActiveStudentEvenWithoutExistingComment() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();

        applyHomework(classSession, fixture.exercise().examId(), null);

        List<StudentCommentResponse> comments2 = studentCommentService.listComments(schoolClass.id(), student2.getId());
        assertThat(comments2).hasSize(1);
        assertThat(comments2.get(0).status()).isEqualTo("DRAFT");
        assertThat(comments2.get(0).content()).isEmpty();
        assertThat(comments2.get(0).homeworkNextExerciseAssignmentId()).isEqualTo(fixture.exercise().examId());
    }

    /** Bài giao (ExerciseAssignment) vẫn là 1 bản DUY NHẤT cho CẢ LỚP (targetStudentIds=null) — bất kỳ học sinh ACTIVE nào cũng tự làm được, không chỉ học sinh có StudentComment. */
    @Test
    void applyHomeworkToClass_MainFlow_deliversExerciseToWholeClassAllowingAnyActiveStudentToAttempt() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();

        applyHomework(classSession, fixture.exercise().examId(), null);

        List<ExerciseAssignment> assignments = exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                fixture.exercise().id(), schoolClass.id(), ExerciseAssignment.Status.ACTIVE);
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getTargetStudentIds()).isNull();
        var attempt = exerciseAttemptService.startAttempt(fixture.exercise().id(), assignments.get(0).getId(), student2.getUser().getId());
        assertThat(attempt.exerciseId()).isEqualTo(fixture.exercise().id());
    }

    /** Mirror test trên cho kênh Video Ôn tập. */
    @Test
    void applyHomeworkToClass_MainFlow_deliversVideoToWholeClassAllowingAnyActiveStudentToWatch() {
        VideoFixture fixture = createConnectionVideoAssignedToClass(100);
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();

        List<StudentCommentResponse> applied = applyHomework(classSession, null, fixture.set().id());
        StudentCommentResponse comment = applied.stream().filter(c -> c.studentId().equals(student.getId())).findFirst().orElseThrow();

        ReviewVideoAssignment assignment = reviewVideoAssignmentRepository.findById(comment.homeworkNextReviewVideoAssignmentId()).orElseThrow();
        assertThat(assignment.getTargetStudentIds()).isNull();
        Long sessionId = reviewVideoService.startWatchSession(fixture.video().id(), assignment.getId(), student2.getUser().getId()).sessionId();
        assertThat(sessionId).isNotNull();
    }

    /**
     * A1: học sinh đã Gửi/Duyệt nhận xét buổi này bị BỎ QUA (không đụng FK/nội dung) khi Áp dụng cho cả
     * lớp -- cần thêm student2 (chưa Gửi) để lớp còn dòng editable, không thì rơi vào nhánh A2 (mọi học
     * sinh đều đã khoá) chứ không thật sự test được hành vi "bỏ qua 1 dòng, áp dụng cho dòng khác".
     */
    @Test
    void applyHomeworkToClass_A1_skipsStudentAlreadySubmittedForThisSession() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(writeDailyComment(teacher, "Đã gửi trước.").id())), teacher.getId()).get(0);

        applyHomework(classSession, fixture.exercise().examId(), null);

        StudentCommentResponse unchanged = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(unchanged.id()).isEqualTo(submitted.id());
        assertThat(unchanged.status()).isEqualTo("PENDING");
        assertThat(unchanged.homeworkNextExerciseAssignmentId()).isNull();
        StudentCommentResponse student2Comment = studentCommentService.listComments(schoolClass.id(), student2.getId()).get(0);
        assertThat(student2Comment.homeworkNextExerciseAssignmentId()).isEqualTo(fixture.exercise().examId());
    }

    /** A2: lớp không có học sinh ACTIVE nào -- không có ai để giao BTVN. */
    @Test
    void applyHomeworkToClass_A2_rejectsWhenNoActiveEnrollments() {
        ClassResponse emptyClass = classService.create(
                new CreateClassRequest(classCode(), "8A3", siteOf(schoolClass).getId(), schoolClass.curriculumId(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        classService.assignTeacher(emptyClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());
        Room room = newRoom(siteOf(schoolClass));
        // Bổ sung ngoài SDD gốc — period 2 (không phải 1) để tránh trùng khung giờ với `classSession`
        // chính (setUp(), cùng teacher, cùng LocalDate.now(), period 1) — 2 buổi CÙNG giáo viên CÙNG
        // ngày CÙNG tiết học là xung đột lịch dạy thật (TeacherScheduleConflict), không phải bug ngẫu
        // nhiên theo ngày chạy CI.
        ClassSessionResponse emptySession = classSessionService.createSession(emptyClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), "MORNING", List.of(2), room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> studentCommentService.applyHomeworkToClass(emptySession.id(),
                new ApplyClassHomeworkRequest(null, null, null, null, null, null), teacher.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Mirror A2 -- có học sinh ACTIVE nhưng TẤT CẢ đã Gửi/Duyệt (không còn dòng nào editable để giao BTVN mới). */
    @Test
    void applyHomeworkToClass_A2_rejectsWhenAllActiveStudentsAlreadyLocked() {
        studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(writeDailyComment(teacher, "Đã gửi.").id())), teacher.getId());
        // Chỉ 1 học sinh ACTIVE (student, xem setUp) và đã Gửi -- không còn dòng nào editable.

        assertThatThrownBy(() -> studentCommentService.applyHomeworkToClass(classSession.id(),
                new ApplyClassHomeworkRequest(null, null, null, null, null, null), teacher.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Gọi lại với đề KHÁC -- huỷ Lô cũ (CANCELLED) + giao Lô mới (ACTIVE), áp dụng đồng nhất cho mọi dòng editable. */
    @Test
    void applyHomeworkToClass_MainFlow_changingExamCancelsPreviousBatchAndCreatesNew() {
        GrammarFixture fixture1 = createGrammarOnlineExercise();
        GrammarFixture fixture2 = createGrammarOnlineExercise();
        nextSession();
        applyHomework(classSession, fixture1.exercise().examId(), null);

        applyHomework(classSession, fixture2.exercise().examId(), null);

        assertThat(exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                fixture1.exercise().id(), schoolClass.id(), ExerciseAssignment.Status.ACTIVE)).isEmpty();
        assertThat(exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                fixture1.exercise().id(), schoolClass.id(), ExerciseAssignment.Status.CANCELLED)).hasSize(1);
        assertThat(exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                fixture2.exercise().id(), schoolClass.id(), ExerciseAssignment.Status.ACTIVE)).hasSize(1);
        StudentCommentResponse comment = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(comment.homeworkNextExerciseAssignmentId()).isEqualTo(fixture2.exercise().examId());
    }

    /**
     * Fix bug: trước đây điều kiện "giữ nguyên, không giao lại" chỉ so exam id (bỏ sót trường hợp CHỈ
     * đổi hạn nộp mà giữ nguyên đề) -- gọi lại CÙNG đề nhưng KHÁC hạn nộp phải thật sự cập nhật hạn nộp,
     * không âm thầm giữ hạn nộp cũ.
     */
    @Test
    void applyHomeworkToClass_MainFlow_sameExamDifferentDueDateUpdatesDueDate() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        LocalDateTime dueDate1 = LocalDate.now().plusDays(3).atTime(9, 0);
        studentCommentService.applyHomeworkToClass(classSession.id(),
                new ApplyClassHomeworkRequest(fixture.exercise().examId(), null, null, null, dueDate1, false), teacher.getId());
        StudentCommentResponse first = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);

        LocalDateTime dueDate2 = LocalDate.now().plusDays(5).atTime(9, 0);
        studentCommentService.applyHomeworkToClass(classSession.id(),
                new ApplyClassHomeworkRequest(fixture.exercise().examId(), null, null, null, dueDate2, false), teacher.getId());
        StudentCommentResponse second = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);

        assertThat(second.homeworkNextDueAt()).isNotEqualTo(first.homeworkNextDueAt());
        // Vẫn chỉ 1 Bài giao ACTIVE (huỷ bản do đổi hạn nộp + giao lại bản mới, không cộng dồn).
        assertThat(exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                fixture.exercise().id(), schoolClass.id(), ExerciseAssignment.Status.ACTIVE)).hasSize(1);
    }

    /** submitComments() giờ CHỈ chuyển trạng thái -- nhận xét có content nhưng chưa từng qua applyHomeworkToClass thì Gửi xong không có side-effect BTVN nào. */
    @Test
    void submitComments_MainFlow_doesNotMaterializeHomeworkWhenNeverAppliedToClass() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung có sẵn, chưa từng Áp dụng BTVN.");

        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);

        assertThat(submitted.status()).isEqualTo("PENDING");
        assertThat(submitted.homeworkNextExerciseAssignmentId()).isNull();
        assertThat(submitted.homeworkNextReviewVideoAssignmentId()).isNull();
        assertThat(exerciseAssignmentRepository.count()).isZero();
        assertThat(reviewVideoAssignmentRepository.count()).isZero();
    }

    /** Câu hỏi mở #4 (đã chốt 2026-07-30): lớp chưa có buổi kế tiếp -- chặn hẳn, không cho Áp dụng BTVN online với hạn nộp tự động. */
    @Test
    void applyHomeworkToClass_A_rejectsGrammarChoiceWhenNoUpcomingSession() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        // Không tạo buổi kế tiếp -- classSession (từ setUp) là buổi duy nhất/cuối cùng của lớp.

        assertThatThrownBy(() -> applyHomework(classSession, fixture.exercise().examId(), null))
                .isInstanceOf(NoUpcomingClassSessionException.class);
    }

    /**
     * Sửa lại 2026-08-14 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng -- phát hiện khi lớp có
     * buổi VIETNAMESE/FOREIGN xen kẽ nhau): "buổi kế tiếp" dùng tính hạn nộp mặc định phải CÙNG loại
     * giáo viên với buổi đang nhận xét -- buổi kế tiếp KHÁC loại GV xen giữa (VD classSession=VIETNAMESE,
     * buổi liền sau=FOREIGN) không được coi là "buổi kế tiếp", vẫn chặn NoUpcomingClassSessionException
     * dù về lịch có buổi sau đó. Mirror đúng cách previousComment() tra "buổi trước" (cùng loại GV).
     */
    @Test
    void applyHomeworkToClass_A_rejectsWhenUpcomingSessionIsDifferentTeacherType() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        User foreignTeacher = assignForeignTeacher();
        Room room = newRoom(siteOf(schoolClass));
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(classSession.sessionDate().plusDays(1), "MORNING", List.of(2), room.getId(), "REGULAR", "FOREIGN",
                        foreignTeacher.getId(), null, null, null, null),
                headAcademic.getId());
        // classSession (setUp) = VIETNAMESE; buổi kế tiếp vừa tạo = FOREIGN -- khác loại GV, không tính.

        assertThatThrownBy(() -> applyHomework(classSession, fixture.exercise().examId(), null))
                .isInstanceOf(NoUpcomingClassSessionException.class);
    }

    /**
     * Mirror test trên -- buổi kế tiếp CÙNG loại GV (VIETNAMESE) nằm SAU buổi khác loại (FOREIGN) xen
     * giữa vẫn phải được dùng làm hạn nộp mặc định (bỏ qua buổi FOREIGN ở giữa), khớp đúng nơi điểm/%
     * của bài giao này sẽ hiển thị (previousComment() cũng bỏ qua buổi FOREIGN khi tra ngược).
     */
    @Test
    void applyHomeworkToClass_MainFlow_defaultDueDateSkipsUpcomingSessionOfDifferentTeacherType() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        User foreignTeacher = assignForeignTeacher();
        Room foreignRoom = newRoom(siteOf(schoolClass));
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(classSession.sessionDate().plusDays(1), "MORNING", List.of(2), foreignRoom.getId(), "REGULAR", "FOREIGN",
                        foreignTeacher.getId(), null, null, null, null),
                headAcademic.getId());
        Room vietnameseRoom = newRoom(siteOf(schoolClass));
        ClassSessionResponse nextVietnameseSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(classSession.sessionDate().plusDays(2), "MORNING", List.of(2), vietnameseRoom.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null),
                headAcademic.getId());

        List<StudentCommentResponse> applied = applyHomework(classSession, fixture.exercise().examId(), null);

        // Khớp APP_ZONE (Asia/Ho_Chi_Minh) cố định trong StudentCommentService — không dùng ZoneId.systemDefault()
        // vì múi giờ JVM chạy test (CI/local) có thể khác múi giờ nghiệp vụ, gây lệch giả (xem StudentCommentService).
        OffsetDateTime expectedDueAt = nextVietnameseSession.sessionDate().atTime(nextVietnameseSession.startTime())
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        StudentCommentResponse comment = applied.stream().filter(c -> c.studentId().equals(student.getId())).findFirst().orElseThrow();
        assertThat(comment.homeworkNextDueAt()).isEqualTo(expectedDueAt);
    }

    /**
     * V167 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-05) — fix bug thật: "buổi kế
     * tiếp" dùng tính hạn nộp mặc định trước đây chỉ so `sessionDate > sessionDate`, KHÔNG phân biệt
     * được 2 buổi CÙNG NGÀY (VD lớp có buổi sáng + buổi chiều cùng ngày, rất phổ biến khi có cả GV
     * Việt Nam lẫn GV nước ngoài dạy cùng 1 ngày) — buổi sáng sẽ NHẢY QUA buổi chiều cùng ngày (cùng
     * loại GV) để tìm sang tận ngày lịch SAU đó, sai hoàn toàn "buổi liền kế tiếp". Test: buổi kế tiếp
     * CÙNG NGÀY, giờ sau, cùng loại GV, phải được chọn làm hạn nộp mặc định — không nhảy xa hơn.
     */
    @Test
    void applyHomeworkToClass_MainFlow_defaultDueDateUsesSameDayLaterSessionOfSameTeacherType() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        Site site = siteOf(schoolClass);
        // withNano(0): classSession.startTime() gốc từ LocalTime.now() (setUp) mang theo nanosecond thật
        // của đồng hồ máy chạy test — Postgres timestamptz chỉ lưu tới microsecond, nên nếu giữ nguyên
        // nanosecond đó, expectedDueAt tính tay dưới đây (chưa qua DB) sẽ lệch 3 chữ số cuối so với
        // giá trị đã qua DB — vỡ assertEquals dù logic đúng. Cắt về giây tròn (mirror seedPeriod(site,
        // 2, LocalTime.of(8, 0), ...) đã dùng hằng số sạch).
        LocalTime period3Start = classSession.startTime().withNano(0).plusHours(2);
        seedPeriod(site, 3, period3Start, period3Start.plusHours(1).plusMinutes(35));
        Room room = newRoom(site);
        ClassSessionResponse sameDayLaterSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(classSession.sessionDate(), "MORNING", List.of(3), room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null, null),
                headAcademic.getId());

        List<StudentCommentResponse> applied = applyHomework(classSession, fixture.exercise().examId(), null);

        OffsetDateTime expectedDueAt = sameDayLaterSession.sessionDate().atTime(sameDayLaterSession.startTime())
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        StudentCommentResponse comment = applied.stream().filter(c -> c.studentId().equals(student.getId())).findFirst().orElseThrow();
        assertThat(comment.homeworkNextDueAt()).isEqualTo(expectedDueAt);
    }

    /** Câu hỏi mở #3 (đã chốt 2026-07-30): duyệt/từ chối nhận xét (UC-22) không liên quan tới bài đã giao -- REJECTED vẫn giữ nguyên assignment ACTIVE. */
    @Test
    void decideComments_regression_rejectedDoesNotCancelAlreadyDeliveredAssignment() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        nextSession();
        applyHomework(classSession, fixture.exercise().examId(), null);
        StudentCommentResponse draft = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        studentCommentService.updateComment(draft.id(),
                new UpdateStudentCommentRequest("Nội dung.", null, null, false, null, null, null, null, null, null, null, null, null),
                teacher.getId());
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(draft.id())), teacher.getId()).get(0);

        studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(submitted.id()), "REJECTED", "Chưa đạt"), siteManagerUser.getId());

        List<ExerciseAssignment> assignments = exerciseAssignmentRepository.findByExerciseIdAndSchoolClassIdAndStatus(
                fixture.exercise().id(), schoolClass.id(), ExerciseAssignment.Status.ACTIVE);
        assertThat(assignments).hasSize(1);
    }

    /**
     * BTVN offline (cột riêng "BTVN offline", tách khỏi cột "BTVN online" — bổ sung ngoài SDD gốc,
     * đã xác nhận với người dùng 2026-08-06): text tự do, không liên quan gì tới applyHomeworkToClass.
     *
     * V130 (2026-08-21, xem HomeworkColumns): buổi teacherType=VIETNAMESE (mọi classSession trong
     * file test này) KHÔNG còn cột "BTVN offline" gộp — tách thành Reading/Writing riêng, đọc vào
     * {@code homeworkNextReading}/{@code homeworkNextWriting} (không phải {@code homeworkNext} chung
     * nữa, field đó giờ chỉ populate được qua API JSON hoặc buổi teacherType=FOREIGN). commentRow()
     * điền cột "Reading" của nhóm BTVN buổi sau, nên assert đúng homeworkNextReading().
     */
    @Test
    void importComments_V55_MainFlow_savesOfflineHomeworkTextWhenOfflineColumnFilled() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Nội dung.", "Ôn lại Unit 3 ở nhà", "", "", "", "", "")
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.homeworkNextReading()).isEqualTo("Ôn lại Unit 3 ở nhà");
        assertThat(saved.homeworkNextExerciseAssignmentId()).isNull();
    }

    @Test
    void buildTemplate_V56_MainFlow_exportsHomeworkPreviousSpeakingScoreIndependentlyFromGrammarScore() throws IOException {
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(), classSession.sessionDate(), "Nội dung.", null, null, false, null, "80%", "60%", null, null, null, null, null, null),
                teacher.getId());

        byte[] template = studentCommentService.buildTemplate(classSession.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_GRAMMAR_PREVIOUS)).isEqualTo("80%");
        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_SPEAKING_PREVIOUS)).isEqualTo("60%");
    }

    @Test
    void importComments_V56_MainFlow_importsHomeworkPreviousSpeakingScoreIndependentlyFromGrammarScore() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "80%", "60%", "Nội dung.", "", "", "", "", "", "")
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
        GrammarFixture fixture = createGrammarOnlineExercise();
        ClassSessionResponse session2 = nextSession();
        applyHomework(classSession, fixture.exercise().examId(), null);
        answerGrammarCorrectly(fixture);
        writeDailyCommentWithHomeworkPrevious(student, session2, "50% (tay)");

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_GRAMMAR_PREVIOUS)).isEqualTo("50% (tay)");
    }

    /** Nhập giá trị ghi đè qua Excel rồi tải lại mẫu — giá trị ghi đè phải còn nguyên, không bị % tự động ghi đè ngược lại. */
    @Test
    void importComments_MainFlow_persistsManualOverrideForGrammarPreviousGoingForward() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "45% (tay)", "", "Nội dung.", "", "", "", "", "", "")
        });

        studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.homeworkPreviousScore()).isEqualTo("45% (tay)");

        byte[] template = studentCommentService.buildTemplate(classSession.id(), teacher.getId());
        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_GRAMMAR_PREVIOUS)).isEqualTo("45% (tay)");
    }

    // ===================== UC-64: học sinh tự xem nhận xét của chính mình =====================

    @Test
    void listMyComments_UC64_MainFlow_onlyReturnsApprovedForOwnClass() {
        // DAILY nay dùng chung luồng DRAFT->Gửi->PENDING->duyệt (2026-07-29) -- ghi rồi phải Gửi+duyệt mới APPROVED.
        StudentCommentResponse toApprove = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(), classSession.sessionDate(), "Nội dung đã duyệt.", null, null, false, null, null, null, null, null, null, null, null, null),
                teacher.getId());
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(toApprove.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(toApprove.id()), "APPROVED", null), siteManagerUser.getId());
        ClassSessionResponse session2 = nextSession();
        // siteManagerUser thay vì teacher -- bổ sung 2026-08-14: session2 (nextSession(), cố tình ở
        // TƯƠNG LAI) chưa "kết thúc" nên GV thường bị requireSessionEndedAndAttendanceTaken chặn; actor
        // có quyền duyệt bỏ qua rào này, không ảnh hưởng gì tới điều đang test (danh sách chỉ APPROVED).
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), session2.id(), session2.sessionDate(), "Nội dung chờ duyệt.", null, null, false, null, null, null, null, null, null, null, null, null),
                siteManagerUser.getId());

        List<StudentCommentResponse> result = studentCommentService.listMyComments(schoolClass.id(), student.getUser().getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("APPROVED");
        assertThat(result.get(0).content()).isEqualTo("Nội dung đã duyệt.");
    }

    @Test
    void listMyComments_rejectsWhenNotEnrolledInClass() {
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "9A1", siteOf(schoolClass).getId(), schoolClass.curriculumId(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        assertThatThrownBy(() -> studentCommentService.listMyComments(otherClass.id(), student.getUser().getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): nhận xét lớp cũ vẫn tự xem được sau khi chuyển lớp. */
    @Test
    void listMyComments_boSung_stillVisibleAfterTransferToAnotherClass() {
        StudentCommentResponse toApprove = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(), classSession.sessionDate(), "Nội dung đã duyệt.", null, null, false, null, null, null, null, null, null, null, null, null),
                teacher.getId());
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(toApprove.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(toApprove.id()), "APPROVED", null), siteManagerUser.getId());
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "9A2", siteOf(schoolClass).getId(), schoolClass.curriculumId(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        studentService.recordTransfer(student.getId(),
                new RecordTransferRequest("CLASS_CHANGE", schoolClass.id(), otherClass.id(), null, LocalDate.now(), "Chuyển lớp test"),
                headAcademic.getId());

        List<StudentCommentResponse> result = studentCommentService.listMyComments(schoolClass.id(), student.getUser().getId());

        assertThat(result).extracting(StudentCommentResponse::id).contains(toApprove.id());
    }

    /**
     * siteManagerUser (có academic.comment.approve) thay vì teacher -- bổ sung 2026-08-14: `session`
     * truyền vào đây luôn là nextSession() (cố tình ở TƯƠNG LAI so với classSession, để test đúng luồng
     * "buổi trước") nên chưa "kết thúc" -- requireSessionEndedAndAttendanceTaken sẽ chặn GV thường,
     * nhưng actor có quyền duyệt thì bỏ qua rào này, không ảnh hưởng gì tới điều đang test.
     */
    private void writeDailyCommentWithHomeworkPrevious(Student targetStudent, ClassSessionResponse session, String homeworkPreviousScore) {
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(targetStudent.getId(), session.id(), session.sessionDate(), "Nội dung buổi.", null, null, false, null, homeworkPreviousScore, null, null, null, null, null, null, null),
                siteManagerUser.getId());
    }

    private String exerciseCode() {
        return "EX-" + SEQ.incrementAndGet();
    }

    private String examCode() {
        return "KD-" + SEQ.incrementAndGet();
    }

    private String bankCode() {
        return "QB-" + SEQ.incrementAndGet();
    }

    private String setCode() {
        return "RVS-" + SEQ.incrementAndGet();
    }

    /**
     * Dựng 1 dòng Excel nhập nhận xét theo tham số CÓ TÊN (thay cho mảng vị trí dễ đếm nhầm) --
     * khớp đúng 19 cột {@code HomeworkColumns.of(VIETNAMESE)} (V130). Cột "Họ và tên"/"Ngày sinh"
     * (2,3) chỉ hiển thị đối chiếu, KHÔNG đọc lại khi import (mirror production) nên không có tham
     * số riêng, luôn để trống. {@code offlinePrevious}/{@code homeworkOffline} chỉ điền cột "Reading"
     * của mỗi nhóm (cột "Writing" luôn để trống) — không test nào trong file này cần phân biệt riêng
     * Reading/Writing, mirror production {@code hc.previousWriting}/{@code hc.nextWriting} đều đọc
     * được nhưng chưa có test nào assert giá trị 2 cột đó.
     */
    private String[] commentRow(String date, String studentCode, String lessonContent, String teacherName,
                                 String attendance, String offlinePrevious, String grammarPrevious, String speakingPrevious,
                                 String content, String homeworkOffline, String grammarNext, String videoNext,
                                 String dueDate, String attitude, String note) {
        String[] row = new String[23];
        row[COL_DATE] = date;
        row[COL_STUDENT_CODE] = studentCode;
        row[COL_LESSON_CONTENT] = lessonContent;
        row[COL_TEACHER_NAME] = teacherName;
        row[COL_ATTENDANCE] = attendance;
        row[COL_HOMEWORK_READING_PREVIOUS] = offlinePrevious;
        row[COL_HOMEWORK_GRAMMAR_PREVIOUS] = grammarPrevious;
        row[COL_HOMEWORK_SPEAKING_PREVIOUS] = speakingPrevious;
        row[COL_CONTENT] = content;
        row[COL_HOMEWORK_READING_NEXT] = homeworkOffline;
        row[COL_HOMEWORK_GRAMMAR_NEXT] = grammarNext;
        row[COL_HOMEWORK_VIDEO_NEXT] = videoNext;
        row[COL_DUE_DATE] = dueDate;
        row[COL_ATTITUDE] = attitude;
        row[COL_NOTE] = note;
        for (int i = 0; i < row.length; i++) {
            if (row[i] == null) {
                row[i] = "";
            }
        }
        return row;
    }

    /**
     * importComments() đọc dữ liệu từ dòng index 3 trở đi cho buổi teacherType=VIETNAMESE (mọi
     * classSession dựng trong file test này) — V130 (2026-08-21) thêm 1 DÒNG header cấp 2 (Offline/
     * Online) giữa dòng nhóm cấp 1 và dòng tên cột con, thành 3 dòng header thay vì 2 (mirror
     * buildThreeLevelHeader/parseImportWorkbook#dataStartRowIndex). Dựng đủ 3 dòng header giả (nội
     * dung không quan trọng, import không đọc lại header) để dữ liệu rơi đúng từ dòng index 3.
     */
    private byte[] buildCommentWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("NhanXet");
            Row groupHeader = sheet.createRow(0);
            Row subGroupHeader = sheet.createRow(1);
            Row leafHeader = sheet.createRow(2);
            String[] headers = {"Ngày", "Mã học viên", "Họ và tên", "Ngày sinh", "Tên bài học", "Tên giáo viên giảng dạy",
                    "Điểm danh", "Reading", "Writing", "Reading", "Writing", "Từ vựng + Ngữ pháp", "Video TKN",
                    "Reading", "Writing", "Reading", "Writing", "Từ vựng + Ngữ pháp", "Video TKN", "Hạn nộp bài",
                    "Thái độ học tập", "Nhận xét học sinh", "Ghi chú"};
            for (int i = 0; i < headers.length; i++) {
                groupHeader.createCell(i);
                subGroupHeader.createCell(i);
                leafHeader.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 3);
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
                new CreateStudentCommentRequest(student.getId(), classSession.id(), LocalDate.now(), content, null, null, false, null, null, null, null, null, null, null, null, null),
                actor.getId());
    }

    private Site siteOf(ClassResponse classResponse) {
        return siteRepository.findById(classResponse.siteId()).orElseThrow();
    }

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-19 — session_periods giờ sinh từ site_period_templates thay vì chia đều theo phút. */
    private void seedPeriod(Site site, int periodNumber, LocalTime start, LocalTime end) {
        SitePeriodTemplate template = new SitePeriodTemplate();
        template.setSite(site);
        template.setPeriodNumber(periodNumber);
        template.setDayPart(SitePeriodTemplate.DayPart.MORNING);
        template.setStartTime(start);
        template.setEndTime(end);
        template.setCreatedBy(headAcademic);
        sitePeriodTemplateRepository.save(template);
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
