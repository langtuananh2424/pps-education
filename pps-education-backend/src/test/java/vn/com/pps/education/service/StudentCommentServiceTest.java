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
import vn.com.pps.education.exception.HomeworkNextConflictException;
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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    // Khớp đúng COL_* private của StudentCommentService (header 17 cột, bổ sung ngoài SDD gốc, đã
    // xác nhận với người dùng 2026-08-06) — dùng hằng số thay vì số ma thuật để tránh sai lệch khi
    // buildCommentWorkbook()/rowForStudent() tham chiếu vị trí cột.
    private static final int COL_DATE = 0;
    private static final int COL_STUDENT_CODE = 1;
    private static final int COL_FULL_NAME = 2;
    private static final int COL_DOB = 3;
    private static final int COL_LESSON_CONTENT = 4;
    private static final int COL_TEACHER_NAME = 5;
    private static final int COL_ATTENDANCE = 6;
    private static final int COL_HOMEWORK_OFFLINE_PREVIOUS = 7;
    private static final int COL_HOMEWORK_GRAMMAR_PREVIOUS = 8;
    private static final int COL_HOMEWORK_SPEAKING_PREVIOUS = 9;
    private static final int COL_HOMEWORK_OFFLINE = 10;
    private static final int COL_HOMEWORK_GRAMMAR_NEXT = 11;
    private static final int COL_HOMEWORK_VIDEO_NEXT = 12;
    private static final int COL_DUE_DATE = 13;
    private static final int COL_ATTITUDE = 14;
    private static final int COL_CONTENT = 15;
    private static final int COL_NOTE = 16;

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
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

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
                        teacher.getId(), null, null, null),
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
                new CreateStudentCommentRequest(student.getId(), classSession.id(),
                        LocalDate.now(), "Nội dung do quản trị viên nhập hộ.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
                admin.getId());

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.commentType()).isEqualTo("DAILY");
    }

    @Test
    void writeComment_UC21_dailyCommentBlockedAfterEditWindowForTeacher() {
        Room room = newRoom(siteOf(schoolClass));
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().minusDays(8), "MORNING", List.of(2), room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null),
                headAcademic.getId());

        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), oldSession.id(),
                        oldSession.sessionDate(), "Nội dung", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void writeComment_UC21_approverBypassesEditWindow() {
        Room room = newRoom(siteOf(schoolClass));
        ClassSessionResponse oldSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().minusDays(8), "MORNING", List.of(2), room.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null),
                headAcademic.getId());

        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), oldSession.id(),
                        oldSession.sessionDate(), "Nội dung do quản lý nhập ngoài hạn.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
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
                new CreateStudentCommentRequest(student.getId(), classSession.id(),
                        classSession.sessionDate(), "Nội dung lần 2.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
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
                new CreateStudentCommentRequest(student.getId(), classSession.id(),
                        classSession.sessionDate(), "Nội dung khác.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void updateComment_UC21_MainFlow_editableWhileDraft() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");
        assertThat(comment.status()).isEqualTo("DRAFT");

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung đã sửa.", null, null, false, "GOOD", "80%", "60%", null, null, "Unit 4", null, null, null, null, null, "Ghi chú"),
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
                new UpdateStudentCommentRequest("Sửa khi đang chờ duyệt.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
                teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void updateComment_UC21_V56_homeworkPreviousSpeakingScoreIndependentFromGrammarScore() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung.", null, null, false, null, null, "70%", null, null, null, null, null, null, null, null, null),
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
                new CreateStudentCommentRequest(student.getId(), classSession.id(),
                        LocalDate.now(), "", null, null, false, "GOOD", null, null, null, null, null, null, null, null, null, null, null),
                teacher.getId());

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.content()).isEmpty();
        assertThat(comment.attitude()).isEqualTo("GOOD");
    }

    /** applyContent phải ghi "" thay vì null khi FE gửi content=null — content cột DB vẫn NOT NULL (V15). */
    @Test
    void writeComment_boSung_coercesNullContentToEmptyString() {
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(),
                        LocalDate.now(), null, null, null, false, null, null, null, null, null, null, null, null, null, null, null, "Chỉ ghi chú"),
                teacher.getId());

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.content()).isEmpty();
        assertThat(comment.note()).isEqualTo("Chỉ ghi chú");
    }

    @Test
    void updateComment_boSung_savesDraftWithoutContent() {
        StudentCommentResponse comment = writeDailyComment(teacher, "Nội dung ban đầu.");

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("", null, null, false, "EXCELLENT", null, null, null, null, null, null, null, null, null, null, null),
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
                new CreateStudentCommentRequest(student2.getId(), classSession.id(),
                        LocalDate.now(), "Nhận xét HS2.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
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
                new UpdateStudentCommentRequest("Nội dung đã sửa lại.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
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
                        teacher.getId(), null, null, null),
                headAcademic.getId());
        // siteManagerUser (có academic.comment.approve) thay vì teacher -- bổ sung 2026-08-14, sửa CI
        // fail: sessionWithoutLesson cố tình ở TƯƠNG LAI (plusDays(1), chưa "kết thúc") để tách biệt với
        // classSession của setUp() đã có sẵn lesson content; requireSessionEndedAndAttendanceTaken chặn
        // GV thường viết nhận xét buổi chưa kết thúc, nhưng actor có quyền duyệt thì bỏ qua rào này
        // (đúng như đã bỏ qua rào hạn 7 ngày/GV được phân công) -- không ảnh hưởng gì tới điều đang test
        // (MissingLessonContentException ở submitComments(), method không gọi rào này).
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), sessionWithoutLesson.id(),
                        sessionWithoutLesson.sessionDate(), "Nội dung.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
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
            // headerGroups (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) -- header
            // 2 dòng: row 0 = nhãn nhóm ("BTVN buổi trước"/"BTVN online", chỉ ở cột đầu nhóm, merge
            // dọc 2 dòng cho cột không thuộc nhóm nào) + row 1 = nhãn cột con (chỉ có giá trị ở các
            // cột thuộc nhóm), dữ liệu bắt đầu từ row 2. Không ép về 1 danh sách phẳng vì bản chất là
            // 2 dòng gộp — chỉ spot-check vài cột tiêu biểu, không phải trọng tâm của test này.
            Row groupHeader = sheet.getRow(0);
            Row subHeader = sheet.getRow(1);
            assertThat(groupHeader.getCell(0).getStringCellValue()).isEqualTo("Ngày*");
            assertThat(groupHeader.getCell(6).getStringCellValue()).isEqualTo("Điểm danh*");
            assertThat(groupHeader.getCell(7).getStringCellValue()).isEqualTo("BTVN buổi trước");
            assertThat(groupHeader.getCell(11).getStringCellValue()).isEqualTo("BTVN online");
            assertThat(subHeader.getCell(7).getStringCellValue()).isEqualTo("Offline");
            // classSession giờ luôn có teacherType=VIETNAMESE (bắt buộc, xác nhận 2026-08-13) -- nhãn kênh
            // "Ngữ pháp" thay vì fallback "Bài" (chỉ áp dụng buổi cũ chưa xác định teacherType).
            assertThat(subHeader.getCell(11).getStringCellValue()).isEqualTo("Ngữ pháp");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            Row row = sheet.getRow(2);
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
                assertThat(v.getRegions().getCellRangeAddress(0).getFirstColumn()).isEqualTo(14);
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

    // ===================== V55/V65: BTVN online/offline (V65: chọn đề/video ở nhận xét tự động giao cả lớp) =====================

    private record GrammarFixture(ExerciseResponse exercise, QuestionResponse question) {}

    private record VideoFixture(ReviewVideoSetResponse set, ReviewVideoResponse video) {}

    /**
     * V65: chỉ tạo + thêm câu hỏi + Publish (đủ điều kiện dùng làm nguồn,
     * hiện trong dropdown "BTVN buổi sau") — KHÔNG còn giao lớp ở đây nữa.
     * Việc giao (deliverToClass) giờ chỉ xảy ra khi GV chọn đề này làm
     * "BTVN buổi sau" cho 1 học sinh (writeDailyCommentWithHomeworkNext).
     *
     * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * Bài giờ thuộc 1 Đề (Exam) — tạo Đề mới + gán cho schoolClass ngay ở
     * đây (deliverToClass gọi sau này bên trong resolveExerciseHomework sẽ
     * cần Đề đã gán lớp mới thành công).
     */
    private GrammarFixture createGrammarOnlineExercise() {
        var exam = examService.createExam(
                new CreateExamRequest(examCode(), "Đề Ngữ pháp V55", schoolClass.curriculumId(), "VIETNAMESE", "HOMEWORK"), teacher.getId());
        examService.assignToClass(exam.id(), schoolClass.id(), teacher.getId());
        // V75 (Kho đề): mỗi Exam tự sinh 1 QuestionBank nội bộ riêng, không nhận câu hỏi qua
        // QuestionBankService#createQuestion (chỉ dành cho bank "legacy" độc lập) — phải qua
        // ExamQuestionService#createQuestion (tự resolve bank nội bộ theo examId), nếu không
        // ExerciseService#addQuestion từ chối vì câu hỏi khác Kho đề với exercise.exam.
        QuestionResponse question = examQuestionService.createQuestion(exam.id(),
                new CreateExamQuestionRequest("MULTIPLE_CHOICE", "GRAMMAR", "EASY", "She ___ to school.",
                        null, null, null, null, null, new BigDecimal("1.0"), null,
                        List.of(new QuestionChoiceRequest("A", "go", false, 1), new QuestionChoiceRequest("B", "goes", true, 2)), null, null),
                teacher.getId());
        ExerciseResponse exercise = exerciseService.createExercise(
                new CreateExerciseRequest(exerciseCode(), "Bài ngữ pháp V55", exam.id(), null,
                        "ASSIGNED", new BigDecimal("1"), null, false, 1, true), teacher.getId());
        exerciseService.addQuestion(exercise.id(), new AddExerciseQuestionRequest(question.id(), 1, new BigDecimal("1.0")), teacher.getId());
        ExerciseResponse published = exerciseService.publishExercise(exercise.id(), teacher.getId());
        // V71: writeComment/writeDailyCommentWithHomeworkNext gọi deliverToClass bên trong bằng
        // PROPAGATION_REQUIRES_NEW — phải commit Đề/Bài vừa tạo trước, nếu không giao dịch lồng
        // không thấy được → FK fail.
        commitCurrentTransactionAndStartNew();
        return new GrammarFixture(published, question);
    }

    private void answerGrammarCorrectly(GrammarFixture fixture, Long assignmentId) {
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
     * đề), nên phải gán trước khi writeComment (gọi deliverToClass bên
     * trong) mới thành công.
     */
    private VideoFixture createConnectionVideoAssignedToClass(int durationSeconds) {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video V55", "CONNECTION", schoolClass.curriculumId(), "VIETNAMESE", null, 1),
                teacher.getId());
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        ReviewVideoSetResponse published = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        ReviewVideoResponse video = reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/v55.mp4",
                        1_000_000L, durationSeconds, 1, null, null),
                teacher.getId());
        // V71: writeComment/writeDailyCommentWithHomeworkNext gọi deliverToClass bên trong bằng
        // PROPAGATION_REQUIRES_NEW — phải commit Bộ video vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        return new VideoFixture(published, video);
    }

    private StudentCommentResponse writeDailyCommentWithHomeworkNext(Student targetStudent, ClassSessionResponse session,
                                                                      Long grammarExerciseId, Long videoSetId) {
        return studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(targetStudent.getId(), session.id(),
                        session.sessionDate(), "Nội dung buổi.", null, null, false, null, null, null, null, null, null, null, null,
                        grammarExerciseId, videoSetId, null, null),
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
                        teacher.getId(), null, null, null),
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
        GrammarFixture fixture = createGrammarOnlineExercise();
        ClassSessionResponse session2 = nextSession();
        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null);
        // V127: giao bài chỉ thật sự xảy ra lúc Gửi (submitComments), không còn ngay lúc Lưu nháp — cần
        // Gửi trước thì exerciseAttemptService.startAttempt() (bên trong answerGrammarCorrectly) mới
        // thấy bản giao tồn tại.
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);
        answerGrammarCorrectly(fixture, submitted.homeworkNextExerciseAssignmentId());

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_GRAMMAR_PREVIOUS)).isEqualTo("100%");
    }

    @Test
    void buildTemplate_V55_MainFlow_showsNotYetDoneForAssignedButUnattemptedGrammar() throws IOException {
        GrammarFixture fixture = createGrammarOnlineExercise();
        ClassSessionResponse session2 = nextSession();
        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null);
        // V127: cần Gửi để bài thật sự "đã giao" — "Chưa làm bài" khác với "chưa giao gì" (buildTemplate
        // trả null nếu chỉ mới Lưu nháp, chưa Gửi — xem resolvedHomeworkOnlineGrammar/effectiveExerciseChoiceId).
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_GRAMMAR_PREVIOUS)).isEqualTo("Chưa làm bài");
    }

    @Test
    void buildTemplate_V55_MainFlow_showsVideoWatchPercentFromPreviousSessionAssignment() throws IOException {
        VideoFixture fixture = createConnectionVideoAssignedToClass(100);
        ClassSessionResponse session2 = nextSession();
        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, null, fixture.set().id());
        // V127: giao bài chỉ thật sự xảy ra lúc Gửi — cần Gửi trước thì startWatchSession() mới thấy bản giao tồn tại.
        StudentCommentResponse submittedComment = studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);
        Long sessionId = reviewVideoService.startWatchSession(fixture.video().id(), submittedComment.homeworkNextReviewVideoAssignmentId(), student.getUser().getId()).sessionId();
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

    /** Cột "buổi trước" tra theo nhận xét CỦA CHÍNH học sinh đó ở buổi trước — học sinh không được nhận xét ở buổi đó thì cột tự động để trống. */
    @Test
    void buildTemplate_V55_MainFlow_leavesAutoColumnNullForStudentNotAssigned() throws IOException {
        GrammarFixture fixture = createGrammarOnlineExercise();
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        ClassSessionResponse session2 = nextSession();
        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null);
        writeDailyCommentWithHomeworkNext(student2, classSession, null, null);
        // V127: cần Gửi để bài thật sự "đã giao" — mirror showsNotYetDoneForAssignedButUnattemptedGrammar.
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        byte[] template = studentCommentService.buildTemplate(session2.id(), teacher.getId());

        assertThat(rowForStudent(template, student.getStudentCode(), COL_HOMEWORK_GRAMMAR_PREVIOUS)).isEqualTo("Chưa làm bài");
        assertThat(rowForStudent(template, student2.getStudentCode(), COL_HOMEWORK_GRAMMAR_PREVIOUS)).isNull();
    }

    /** V65: chọn đề cho 1 học sinh tự động giao cho CẢ LỚP — học sinh khác cùng buổi cũng thấy đề đó trong dropdown/khi tự làm bài. */
    @Test
    void writeComment_V65_MainFlow_deliversExerciseToWholeClassNotJustCommentedStudent() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();

        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null);
        // V127: giao bài chỉ thật sự xảy ra lúc Gửi — chưa Gửi thì chưa có ExerciseAssignment nào để tìm.
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);

        ExerciseAssignment assignment = exerciseAssignmentRepository.findById(submitted.homeworkNextExerciseAssignmentId()).orElseThrow();
        assertThat(assignment.getSchoolClass().getId()).isEqualTo(schoolClass.id());
        assertThat(assignment.getTargetStudentIds()).isNull();
        // targetStudentIds=null (cả lớp) -- student2 (không được nhận xét) vẫn tự làm được bài này.
        var attempt = exerciseAttemptService.startAttempt(fixture.exercise().id(), assignment.getId(), student2.getUser().getId());
        assertThat(attempt.exerciseId()).isEqualTo(fixture.exercise().id());
    }

    /** V65: mirror test trên cho kênh Video Ôn tập -- xem Javadoc test đó. */
    @Test
    void writeComment_V65_MainFlow_deliversVideoToWholeClassNotJustCommentedStudent() {
        VideoFixture fixture = createConnectionVideoAssignedToClass(100);
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();

        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, null, fixture.set().id());
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);

        ReviewVideoAssignment assignment = reviewVideoAssignmentRepository.findById(submitted.homeworkNextReviewVideoAssignmentId()).orElseThrow();
        assertThat(assignment.getSchoolClass().getId()).isEqualTo(schoolClass.id());
        assertThat(assignment.getTargetStudentIds()).isNull();
        // targetStudentIds=null (cả lớp) -- student2 (không được nhận xét) vẫn xem được video này.
        Long sessionId = reviewVideoService.startWatchSession(fixture.video().id(), assignment.getId(), student2.getUser().getId()).sessionId();
        assertThat(sessionId).isNotNull();
    }

    /** Câu hỏi mở #1 (đã chốt 2026-07-30): dòng đầu tiên chọn 1 đề cho buổi -- dòng khác (học sinh khác) cùng buổi phải chọn ĐÚNG đề đó, khác đề bị chặn 409. */
    @Test
    void writeComment_V65_A1_rejectsConflictingGrammarChoiceInSameSession() {
        GrammarFixture fixture1 = createGrammarOnlineExercise();
        GrammarFixture fixture2 = createGrammarOnlineExercise();
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();
        writeDailyCommentWithHomeworkNext(student, classSession, fixture1.exercise().id(), null);

        assertThatThrownBy(() -> writeDailyCommentWithHomeworkNext(student2, classSession, fixture2.exercise().id(), null))
                .isInstanceOf(HomeworkNextConflictException.class);
    }

    /** Mirror test trên cho kênh Video -- 2 kênh chặn xung đột ĐỘC LẬP nhau (xem Javadoc StudentCommentService.requireNoHomeworkConflict). */
    @Test
    void writeComment_V65_A1_rejectsConflictingVideoChoiceInSameSession() {
        VideoFixture fixture1 = createConnectionVideoAssignedToClass(100);
        VideoFixture fixture2 = createConnectionVideoAssignedToClass(100);
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();
        writeDailyCommentWithHomeworkNext(student, classSession, null, fixture1.set().id());

        assertThatThrownBy(() -> writeDailyCommentWithHomeworkNext(student2, classSession, null, fixture2.set().id()))
                .isInstanceOf(HomeworkNextConflictException.class);
    }

    /**
     * V127 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — regression cho đúng bug mà
     * effectiveExerciseChoiceId (StudentCommentService) phải né: học sinh A Gửi TRƯỚC (bản giao thật đã
     * tạo, pendingHomeworkNextExerciseId của A đã về null) — giáo viên sau đó Lưu nháp cho học sinh B 1
     * đề KHÁC cùng buổi vẫn phải bị chặn 409, dù A không còn "pending" nào để so trực tiếp (thiết kế
     * ngây thơ "chỉ so pending với pending" sẽ bỏ sót đúng case này).
     */
    @Test
    void writeComment_V127_rejectsConflictWhenSiblingAlreadySubmittedAndPendingIsNull() {
        GrammarFixture fixture1 = createGrammarOnlineExercise();
        GrammarFixture fixture2 = createGrammarOnlineExercise();
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();
        StudentCommentResponse commentA = writeDailyCommentWithHomeworkNext(student, classSession, fixture1.exercise().id(), null);
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(commentA.id())), teacher.getId());

        assertThatThrownBy(() -> writeDailyCommentWithHomeworkNext(student2, classSession, fixture2.exercise().id(), null))
                .isInstanceOf(HomeworkNextConflictException.class);
    }

    /**
     * Bổ sung ngoài SDD gốc, xác nhận 2026-08-19 — regression cho bug OffsetDateTime.equals() ở
     * requireNoDueDateConflict (mirror đúng bug đã gặp+fix trước đó ở ExerciseService#sameDueAt —
     * xem Javadoc requireNoDueDateConflict): 2 học sinh CÙNG buổi chọn CÙNG 1 Bài (nên cùng hạn nộp
     * tính từ buổi kế tiếp) KHÔNG được báo xung đột. Bắt buộc commitCurrentTransactionAndStartNew()
     * giữa 2 lần gọi để bản ghi ĐÃ GIAO của student1 THẬT SỰ round-trip qua JDBC (mang offset UTC do
     * hibernate.jdbc.time_zone: UTC ở application.yml) trước khi student2 nạp lại làm "sibling" —
     * gọi liên tiếp trong CÙNG persistence context (như mọi test khác ở trên) sẽ không tái hiện
     * được bug vì Hibernate tái dùng thẳng object Java gốc, chưa từng qua JDBC.
     *
     * V127 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — phải Gửi (submitComments)
     * CẢ 2 học sinh, không chỉ Lưu nháp (writeComment): bug gốc chỉ xảy ra trên giá trị dueAt của bản
     * giao THẬT SỰ (OffsetDateTime, round-trip qua JDBC) — pendingHomeworkNextExerciseId (Long thô,
     * không có offset) không tái hiện được bug này dù không submit.
     */
    @Test
    void writeComment_boSung_allowsSameGrammarDueDateAcrossStudentsInSameSessionAfterDbRoundTrip() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        Student student2 = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student2.getId(), LocalDate.now()), headAcademic.getId());
        nextSession();
        StudentCommentResponse comment1 = writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null);
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment1.id())), teacher.getId());
        commitCurrentTransactionAndStartNew();

        StudentCommentResponse comment2 = writeDailyCommentWithHomeworkNext(student2, classSession, fixture.exercise().id(), null);
        StudentCommentResponse submitted2 = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment2.id())), teacher.getId()).get(0);

        assertThat(submitted2.homeworkNextExerciseAssignmentId()).isNotNull();
    }

    /** Câu hỏi mở #4 (đã chốt 2026-07-30): lớp chưa có buổi kế tiếp -- chặn hẳn, không cho chọn đề/video làm BTVN buổi sau. */
    @Test
    void writeComment_V65_A2_rejectsGrammarChoiceWhenNoUpcomingSession() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        // Không tạo buổi kế tiếp -- classSession (từ setUp) là buổi duy nhất/cuối cùng của lớp.

        assertThatThrownBy(() -> writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null))
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
    void writeComment_V117_A2_rejectsGrammarChoiceWhenUpcomingSessionIsDifferentTeacherType() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        User foreignTeacher = assignForeignTeacher();
        Room room = newRoom(siteOf(schoolClass));
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(classSession.sessionDate().plusDays(1), "MORNING", List.of(2), room.getId(), "REGULAR", "FOREIGN",
                        foreignTeacher.getId(), null, null, null),
                headAcademic.getId());
        // classSession (setUp) = VIETNAMESE; buổi kế tiếp vừa tạo = FOREIGN -- khác loại GV, không tính.

        assertThatThrownBy(() -> writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null))
                .isInstanceOf(NoUpcomingClassSessionException.class);
    }

    /**
     * Mirror test trên -- buổi kế tiếp CÙNG loại GV (VIETNAMESE) nằm SAU buổi khác loại (FOREIGN) xen
     * giữa vẫn phải được dùng làm hạn nộp mặc định (bỏ qua buổi FOREIGN ở giữa), khớp đúng nơi điểm/%
     * của bài giao này sẽ hiển thị (previousComment() cũng bỏ qua buổi FOREIGN khi tra ngược).
     */
    @Test
    void writeComment_V117_MainFlow_defaultDueDateSkipsUpcomingSessionOfDifferentTeacherType() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        User foreignTeacher = assignForeignTeacher();
        Room foreignRoom = newRoom(siteOf(schoolClass));
        classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(classSession.sessionDate().plusDays(1), "MORNING", List.of(2), foreignRoom.getId(), "REGULAR", "FOREIGN",
                        foreignTeacher.getId(), null, null, null),
                headAcademic.getId());
        Room vietnameseRoom = newRoom(siteOf(schoolClass));
        ClassSessionResponse nextVietnameseSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(classSession.sessionDate().plusDays(2), "MORNING", List.of(2), vietnameseRoom.getId(), "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null),
                headAcademic.getId());

        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null);
        // V127: homeworkNextDueAt chỉ có giá trị SAU KHI Gửi (bản giao thật) — lúc còn DRAFT chỉ có
        // pendingHomeworkNextDueDate (không expose qua response). Việc resolve hạn nộp (bỏ qua buổi khác
        // loại GV) đã chạy VALIDATE ngay ở writeComment (không throw ở đây là bằng chứng đã đúng); Gửi
        // để đọc lại đúng giá trị đã resolve.
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);

        // Khớp APP_ZONE (Asia/Ho_Chi_Minh) cố định trong StudentCommentService — không dùng ZoneId.systemDefault()
        // vì múi giờ JVM chạy test (CI/local) có thể khác múi giờ nghiệp vụ, gây lệch giả (xem StudentCommentService).
        OffsetDateTime expectedDueAt = nextVietnameseSession.sessionDate().atTime(nextVietnameseSession.startTime())
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        assertThat(submitted.homeworkNextDueAt()).isEqualTo(expectedDueAt);
    }

    /**
     * Câu hỏi mở #2 (đã chốt 2026-07-30), SỬA LẠI V127 (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng 2026-08-19) — trước V127, đổi lựa chọn khi còn DRAFT huỷ bản giao cũ + tạo bản mới NGAY (vì
     * giao bài xảy ra ngay lúc Lưu nháp). Giờ giao bài chỉ xảy ra lúc Gửi — đổi lựa chọn lúc còn Nháp
     * CHỈ ghi đè pendingHomeworkNextExerciseId, KHÔNG đụng ExerciseAssignment nào (chưa có gì để huỷ,
     * vì chưa từng giao). Case "huỷ bản CŨ + giao bản MỚI" (rule cũ) giờ chỉ xảy ra ở luồng resubmit
     * sau REJECTED — xem updateComment_V127_resubmitAfterRejected_cancelsOldAssignmentAndDeliversNew.
     */
    @Test
    void updateComment_V127_MainFlow_changingChoiceWhileDraftOnlyUpdatesPendingChoiceNoAssignmentTouched() {
        GrammarFixture fixture1 = createGrammarOnlineExercise();
        GrammarFixture fixture2 = createGrammarOnlineExercise();
        nextSession();
        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture1.exercise().id(), null);
        assertThat(comment.pendingHomeworkNextExerciseId()).isEqualTo(fixture1.exercise().id());
        assertThat(comment.homeworkNextExerciseAssignmentId()).isNull();

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung buổi.", null, null, false, null, null, null, null, null, null, null, null,
                        fixture2.exercise().id(), null, null, null),
                teacher.getId());

        assertThat(edited.pendingHomeworkNextExerciseId()).isEqualTo(fixture2.exercise().id());
        assertThat(edited.homeworkNextExerciseAssignmentId()).isNull();
        assertThat(exerciseAssignmentRepository.count()).isZero();
    }

    /** Bỏ chọn hẳn (về null) khi còn DRAFT -- SỬA LẠI V127, mirror test trên: chỉ xoá pending, không đụng gì bản giao (chưa từng giao). */
    @Test
    void updateComment_V127_A_clearingChoiceWhileDraftOnlyClearsPendingChoiceNoAssignmentTouched() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        nextSession();
        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null);
        assertThat(comment.pendingHomeworkNextExerciseId()).isEqualTo(fixture.exercise().id());

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung buổi.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
                teacher.getId());

        assertThat(edited.pendingHomeworkNextExerciseId()).isNull();
        assertThat(edited.homeworkNextExerciseAssignmentId()).isNull();
        assertThat(exerciseAssignmentRepository.count()).isZero();
    }

    /**
     * V127 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — luồng MỚI thay thế đúng ý
     * "đổi lựa chọn huỷ bản cũ + giao bản mới" của rule cũ (nay chỉ áp dụng cho case REJECTED, không
     * còn áp dụng lúc còn DRAFT lần đầu — xem 2 test trên): Gửi lần 1 (giao bài fixture1 thật), bị Từ
     * chối, sửa sang fixture2 (chưa đụng gì bản giao fixture1 — vẫn ACTIVE), Gửi lại lần 2 mới huỷ
     * fixture1 (CANCELLED) + giao fixture2 (ACTIVE) — đúng cơ chế "previous" có sẵn trong
     * resolveExerciseHomework, không cần logic mới.
     */
    @Test
    void updateComment_V127_resubmitAfterRejected_cancelsOldAssignmentAndDeliversNew() {
        GrammarFixture fixture1 = createGrammarOnlineExercise();
        GrammarFixture fixture2 = createGrammarOnlineExercise();
        nextSession();
        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture1.exercise().id(), null);
        StudentCommentResponse submitted1 = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);
        Long firstAssignmentId = submitted1.homeworkNextExerciseAssignmentId();
        studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "REJECTED", "Chưa đạt"), siteManagerUser.getId());

        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung sửa lại.", null, null, false, null, null, null, null, null, null, null, null,
                        fixture2.exercise().id(), null, null, null),
                teacher.getId());
        assertThat(edited.pendingHomeworkNextExerciseId()).isEqualTo(fixture2.exercise().id());
        assertThat(edited.homeworkNextExerciseAssignmentId()).isEqualTo(firstAssignmentId);
        assertThat(exerciseAssignmentRepository.findById(firstAssignmentId).orElseThrow().getStatus())
                .isEqualTo(ExerciseAssignment.Status.ACTIVE);

        StudentCommentResponse resubmitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);

        assertThat(exerciseAssignmentRepository.findById(firstAssignmentId).orElseThrow().getStatus())
                .isEqualTo(ExerciseAssignment.Status.CANCELLED);
        ExerciseAssignment newAssignment = exerciseAssignmentRepository.findById(resubmitted.homeworkNextExerciseAssignmentId()).orElseThrow();
        assertThat(newAssignment.getExercise().getId()).isEqualTo(fixture2.exercise().id());
        assertThat(newAssignment.getStatus()).isEqualTo(ExerciseAssignment.Status.ACTIVE);
    }

    /** Câu hỏi mở #3 (đã chốt 2026-07-30): duyệt/từ chối nhận xét (UC-22) không liên quan tới bài đã giao -- REJECTED vẫn giữ nguyên assignment ACTIVE. */
    @Test
    void decideComments_V65_regression_rejectedDoesNotCancelAlreadyDeliveredAssignment() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        nextSession();
        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null);
        // V127: homeworkNextExerciseAssignmentId chỉ có giá trị SAU submit — đọc từ response của
        // submitComments (không phải response của writeComment ở trên, giờ luôn null).
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);

        studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "REJECTED", "Chưa đạt"), siteManagerUser.getId());

        ExerciseAssignment assignment = exerciseAssignmentRepository.findById(submitted.homeworkNextExerciseAssignmentId()).orElseThrow();
        assertThat(assignment.getStatus()).isEqualTo(ExerciseAssignment.Status.ACTIVE);
    }

    @Test
    void buildTemplate_V55_MainFlow_dropdownOnlyListsAssignmentsForThisClass() throws IOException {
        GrammarFixture fixture = createGrammarOnlineExercise();
        VideoFixture video = createConnectionVideoAssignedToClass(100);

        byte[] template = studentCommentService.buildTemplate(classSession.id(), teacher.getId());

        assertThat(dropdownValues(template, COL_HOMEWORK_GRAMMAR_NEXT))
                .containsExactly(fixture.exercise().examCode() + " - " + fixture.exercise().title());
        assertThat(dropdownValues(template, COL_HOMEWORK_VIDEO_NEXT))
                .containsExactly(video.set().title() + " (" + video.set().code() + ")");
    }

    @Test
    void importComments_V55_MainFlow_resolvesGrammarAssignmentByUuid() throws IOException {
        GrammarFixture fixture = createGrammarOnlineExercise();
        nextSession();
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Nội dung.", "", fixture.exercise().uuid().toString(), "", "", "", "")
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.pendingHomeworkNextExerciseId()).isEqualTo(fixture.exercise().id());
        assertThat(saved.homeworkNext()).isNull();
        // V127: giao bài chỉ thật sự xảy ra lúc Gửi — importComments() (như writeComment) giờ chỉ lưu tạm.
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(saved.id())), teacher.getId()).get(0);
        assertThat(submitted.homeworkNextExerciseAssignmentId()).isNotNull();
        ExerciseAssignment created = exerciseAssignmentRepository.findById(submitted.homeworkNextExerciseAssignmentId()).orElseThrow();
        assertThat(created.getExercise().getId()).isEqualTo(fixture.exercise().id());
    }

    @Test
    void importComments_V55_MainFlow_resolvesGrammarAssignmentByDropdownLabel() throws IOException {
        GrammarFixture fixture = createGrammarOnlineExercise();
        nextSession();
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        String label = fixture.exercise().examCode() + " - " + fixture.exercise().title();
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Nội dung.", "", label, "", "", "", "")
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.pendingHomeworkNextExerciseId()).isEqualTo(fixture.exercise().id());
        assertThat(saved.homeworkNext()).isNull();
        // V127: giao bài chỉ thật sự xảy ra lúc Gửi.
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(saved.id())), teacher.getId()).get(0);
        assertThat(submitted.homeworkNextExerciseAssignmentId()).isNotNull();
        ExerciseAssignment created = exerciseAssignmentRepository.findById(submitted.homeworkNextExerciseAssignmentId()).orElseThrow();
        assertThat(created.getExercise().getId()).isEqualTo(fixture.exercise().id());
    }

    /**
     * BTVN offline (cột riêng "BTVN offline", tách khỏi cột "BTVN online" — bổ sung ngoài SDD gốc,
     * đã xác nhận với người dùng 2026-08-06): text tự do, lưu thẳng làm homeworkNext, không qua
     * resolveByUuidOrLabel nên không cần khớp đề nào trong kho (khác cột online — nay CHẶT, không
     * khớp thì báo lỗi, xem importComments_V55_A_rejectsUnmatchedVideoSelectionWithRowError).
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
        assertThat(saved.homeworkNext()).isEqualTo("Ôn lại Unit 3 ở nhà");
        assertThat(saved.homeworkNextExerciseAssignmentId()).isNull();
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — bỏ ràng buộc loại trừ lẫn nhau
     * giữa cột "BTVN offline" và "BTVN online": trước đây import báo lỗi "Chỉ điền 1 trong 2 cột" nếu
     * điền cả hai, nay 1 dòng Excel giao được ĐỒNG THỜI cả 2 cho kênh Ngữ pháp.
     */
    @Test
    void importComments_boSung_MainFlow_allowsBothOfflineAndOnlineGrammarHomeworkTogether() throws IOException {
        GrammarFixture fixture = createGrammarOnlineExercise();
        nextSession();
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Nội dung.", "Ôn lại Unit 3 ở nhà",
                        fixture.exercise().uuid().toString(), "", "", "", "")
        });

        DailyCommentImportResponse result = studentCommentService.importComments(classSession.id(),
                new MockMultipartFile("file", "nhanxet.xlsx", "application/vnd.openxmlformats", file), teacher.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        StudentCommentResponse saved = studentCommentService.listComments(schoolClass.id(), student.getId()).get(0);
        assertThat(saved.homeworkNext()).isEqualTo("Ôn lại Unit 3 ở nhà");
        assertThat(saved.pendingHomeworkNextExerciseId()).isEqualTo(fixture.exercise().id());
        // V127: giao bài chỉ thật sự xảy ra lúc Gửi.
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(saved.id())), teacher.getId()).get(0);
        assertThat(submitted.homeworkNextExerciseAssignmentId()).isNotNull();
        ExerciseAssignment created = exerciseAssignmentRepository.findById(submitted.homeworkNextExerciseAssignmentId()).orElseThrow();
        assertThat(created.getExercise().getId()).isEqualTo(fixture.exercise().id());
    }

    /** Cùng quy tắc 2026-08-18 ở trên nhưng qua API JSON trực tiếp (writeComment) — luồng chưa từng bị chặn, chốt lại hành vi bằng test riêng. */
    @Test
    void writeComment_boSung_MainFlow_allowsBothOfflineAndOnlineGrammarHomeworkTogether() {
        GrammarFixture fixture = createGrammarOnlineExercise();
        nextSession();

        StudentCommentResponse saved = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), classSession.id(), classSession.sessionDate(),
                        "Nội dung.", null, null, false, null, null, null, null, null,
                        "Ôn lại Unit 3 ở nhà", null, null, fixture.exercise().id(), null, null, null),
                teacher.getId());

        assertThat(saved.homeworkNext()).isEqualTo("Ôn lại Unit 3 ở nhà");
        assertThat(saved.pendingHomeworkNextExerciseId()).isEqualTo(fixture.exercise().id());
        // V127: giao bài chỉ thật sự xảy ra lúc Gửi.
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(saved.id())), teacher.getId()).get(0);
        assertThat(submitted.homeworkNextExerciseAssignmentId()).isNotNull();
    }

    /** Cột "BTVN Nghe-nói buổi sau" (chỉ đổi tên, vẫn thuần online) — vẫn báo lỗi khi không khớp, không fallback text như cột Ngữ pháp. */
    @Test
    void importComments_V55_A_rejectsUnmatchedVideoSelectionWithRowError() throws IOException {
        studentAttendanceService.markAttendance(classSession.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        byte[] file = buildCommentWorkbook(new String[][]{
                commentRow(classSession.sessionDate().toString(), student.getStudentCode(), "", "",
                        "Có mặt", "", "", "", "Nội dung.", "", "", "Bộ video không tồn tại (XX)", "", "", "")
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
                new CreateStudentCommentRequest(student.getId(), classSession.id(),
                        classSession.sessionDate(), "Nội dung.", null, null, false, null, "80%", "60%", null, null, null, null, null, null, null, null, null),
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
        StudentCommentResponse comment = writeDailyCommentWithHomeworkNext(student, classSession, fixture.exercise().id(), null);
        // V127: giao bài chỉ thật sự xảy ra lúc Gửi — cần Gửi trước thì startAttempt() (trong answerGrammarCorrectly) mới thấy bản giao tồn tại.
        StudentCommentResponse submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()).get(0);
        answerGrammarCorrectly(fixture, submitted.homeworkNextExerciseAssignmentId());
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
                new CreateStudentCommentRequest(student.getId(), classSession.id(),
                        classSession.sessionDate(), "Nội dung đã duyệt.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
                teacher.getId());
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(toApprove.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(toApprove.id()), "APPROVED", null), siteManagerUser.getId());
        ClassSessionResponse session2 = nextSession();
        // siteManagerUser thay vì teacher -- bổ sung 2026-08-14: session2 (nextSession(), cố tình ở
        // TƯƠNG LAI) chưa "kết thúc" nên GV thường bị requireSessionEndedAndAttendanceTaken chặn; actor
        // có quyền duyệt bỏ qua rào này, không ảnh hưởng gì tới điều đang test (danh sách chỉ APPROVED).
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), session2.id(),
                        session2.sessionDate(), "Nội dung chờ duyệt.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
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
                new CreateStudentCommentRequest(student.getId(), classSession.id(),
                        classSession.sessionDate(), "Nội dung đã duyệt.", null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
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
                new CreateStudentCommentRequest(targetStudent.getId(), session.id(),
                        session.sessionDate(), "Nội dung buổi.", null, null, false, null, homeworkPreviousScore, null, null, null, null, null, null, null, null, null, null),
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
     * khớp đúng 17 cột COL_* (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06). Cột
     * "Họ và tên"/"Ngày sinh" (2,3) chỉ hiển thị đối chiếu, KHÔNG đọc lại khi import (mirror
     * production) nên không có tham số riêng, luôn để trống.
     */
    private String[] commentRow(String date, String studentCode, String lessonContent, String teacherName,
                                 String attendance, String offlinePrevious, String grammarPrevious, String speakingPrevious,
                                 String content, String homeworkOffline, String grammarNext, String videoNext,
                                 String dueDate, String attitude, String note) {
        String[] row = new String[17];
        row[COL_DATE] = date;
        row[COL_STUDENT_CODE] = studentCode;
        row[COL_LESSON_CONTENT] = lessonContent;
        row[COL_TEACHER_NAME] = teacherName;
        row[COL_ATTENDANCE] = attendance;
        row[COL_HOMEWORK_OFFLINE_PREVIOUS] = offlinePrevious;
        row[COL_HOMEWORK_GRAMMAR_PREVIOUS] = grammarPrevious;
        row[COL_HOMEWORK_SPEAKING_PREVIOUS] = speakingPrevious;
        row[COL_CONTENT] = content;
        row[COL_HOMEWORK_OFFLINE] = homeworkOffline;
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
     * importComments() đọc dữ liệu từ dòng index 2 trở đi (khớp buildTemplate() sinh header 2 dòng —
     * dòng nhóm + dòng cột con, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) — dựng
     * đủ 2 dòng header giả (nội dung không quan trọng, import không đọc lại header) để dữ liệu rơi
     * đúng từ dòng index 2 như production mong đợi.
     */
    private byte[] buildCommentWorkbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("NhanXet");
            Row groupHeader = sheet.createRow(0);
            Row subHeader = sheet.createRow(1);
            String[] headers = {"Ngày", "Mã học viên", "Họ và tên", "Ngày sinh", "Tên bài học", "Tên giáo viên giảng dạy",
                    "Điểm danh", "Offline", "Bài", "Video", "BTVN offline", "Bài", "Video", "Hạn nộp bài",
                    "Thái độ học tập", "Nhận xét học sinh", "Ghi chú"};
            for (int i = 0; i < headers.length; i++) {
                groupHeader.createCell(i).setCellValue(headers[i]);
                subHeader.createCell(i);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 2);
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
                new CreateStudentCommentRequest(student.getId(), classSession.id(),
                        LocalDate.now(), content, null, null, false, null, null, null, null, null, null, null, null, null, null, null, null),
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
