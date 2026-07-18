package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.GradePeriodResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-21: Viết nhận xét học sinh — Main Flow (bước 1-5), A1 (bị từ chối,
 * sửa lại và submit lại) + UC-22: Duyệt nhận xét — Main Flow (bước 1-5),
 * A1 (duyệt lô nhanh). Xem docs/uc/phan-he-06-hoc-thuat.md.
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
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 40),
                        room.getId(), teacher.getId(), "REGULAR"),
                headAcademic.getId());

        gradePeriod = gradeService.createGradePeriod(activeCurriculum.id(),
                new CreateGradePeriodRequest("MID_1", "Giữa kỳ 1", 1, new BigDecimal("50"), null, null), headAcademic.getId());
    }

    @Test
    void writeComment_UC21_MainFlow_savesDraftDailyComment() {
        StudentCommentResponse comment = writeDailyComment("Chăm chỉ, tích cực phát biểu.", false);

        assertThat(comment.status()).isEqualTo("DRAFT");
        assertThat(comment.commentType()).isEqualTo("DAILY");
        assertThat(comment.classSessionId()).isEqualTo(classSession.id());
        assertThat(comment.severity()).isEqualTo("NORMAL");
    }

    @Test
    void writeComment_UC21_MainFlow_savesMidTermCommentWithWarningFlag() {
        StudentCommentResponse comment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "MID_TERM", null, gradePeriod.id(),
                        LocalDate.now(), "Cần cải thiện kỹ năng nghe.", null, "CONCERN", true),
                teacher.getId());

        assertThat(comment.commentType()).isEqualTo("MID_TERM");
        assertThat(comment.gradePeriodId()).isEqualTo(gradePeriod.id());
        assertThat(comment.severity()).isEqualTo("CONCERN");
        assertThat(comment.isWarning()).isTrue();
    }

    @Test
    void writeComment_rejectsInvalidContextForDailyWithoutSession() {
        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", null, null,
                        LocalDate.now(), "Nội dung", null, null, false),
                teacher.getId()))
                .isInstanceOf(InvalidCommentContextException.class);
    }

    @Test
    void writeComment_rejectsInvalidContextForMidTermWithSessionInsteadOfPeriod() {
        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "MID_TERM", classSession.id(), null,
                        LocalDate.now(), "Nội dung", null, null, false),
                teacher.getId()))
                .isInstanceOf(InvalidCommentContextException.class);
    }

    @Test
    void writeComment_rejectsWhenActorNotAssignedTeacher() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", classSession.id(), null,
                        LocalDate.now(), "Nội dung", null, null, false),
                outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void submitComments_UC21_MainFlow_transitionsToPendingAndNotifiesSiteManager() {
        StudentCommentResponse comment = writeDailyComment("Nội dung nhận xét.", false);

        List<StudentCommentResponse> submitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        assertThat(submitted).hasSize(1);
        assertThat(submitted.get(0).status()).isEqualTo("PENDING");
        assertThat(studentCommentService.listPendingForSite(siteManagerUser.getId()))
                .extracting(StudentCommentResponse::id).contains(comment.id());
    }

    @Test
    void submitComments_rejectsWhenNotDraft() {
        StudentCommentResponse comment = writeDailyComment("Nội dung nhận xét.", false);
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        assertThatThrownBy(() -> studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void decideComments_UC22_MainFlow_approvedMakesVisibleToParent() {
        StudentCommentResponse comment = writeDailyComment("Nội dung nhận xét.", false);
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        List<StudentCommentResponse> decided = studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "APPROVED", "Đạt"), siteManagerUser.getId());

        assertThat(decided.get(0).status()).isEqualTo("APPROVED");
        assertThat(decided.get(0).visibleToParentAt()).isNotNull();
    }

    @Test
    void decideComments_UC22_A1_batchApprovalForMultipleComments() {
        Student student2 = newStudent();
        StudentCommentResponse comment1 = writeDailyComment("Nhận xét HS1.", false);
        StudentCommentResponse comment2 = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student2.getId(), "DAILY", classSession.id(), null,
                        LocalDate.now(), "Nhận xét HS2.", null, null, false),
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
        StudentCommentResponse comment = writeDailyComment("Nội dung ban đầu.", false);
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        List<StudentCommentResponse> decided = studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "REJECTED", "Nội dung chưa rõ ràng"), siteManagerUser.getId());
        assertThat(decided.get(0).status()).isEqualTo("REJECTED");
        assertThat(decided.get(0).visibleToParentAt()).isNull();

        // UC-21 A1 -- Giáo viên sửa lại sau khi bị từ chối, quay về DRAFT, submit lại được.
        StudentCommentResponse edited = studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Nội dung đã sửa lại.", null, null, false), teacher.getId());
        assertThat(edited.status()).isEqualTo("DRAFT");

        List<StudentCommentResponse> resubmitted = studentCommentService.submitComments(schoolClass.id(),
                new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());
        assertThat(resubmitted.get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void updateComment_rejectsWhenPending() {
        StudentCommentResponse comment = writeDailyComment("Nội dung.", false);
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());

        assertThatThrownBy(() -> studentCommentService.updateComment(comment.id(),
                new UpdateStudentCommentRequest("Sửa khi đang chờ duyệt.", null, null, false), teacher.getId()))
                .isInstanceOf(StudentCommentNotEditableException.class);
    }

    @Test
    void decideComments_rejectsWhenActorNotSiteManagerForSite() {
        StudentCommentResponse comment = writeDailyComment("Nội dung.", false);
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());
        User outsiderManager = newUser("outsider.sitemanager");
        assignRole(outsiderManager, "SITE_MANAGER");

        assertThatThrownBy(() -> studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "APPROVED", null), outsiderManager.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void decideComments_rejectsWhenAlreadyDecided() {
        StudentCommentResponse comment = writeDailyComment("Nội dung.", false);
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(comment.id())), teacher.getId());
        studentCommentService.decideComments(new DecideCommentsRequest(List.of(comment.id()), "APPROVED", null), siteManagerUser.getId());

        assertThatThrownBy(() -> studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(comment.id()), "APPROVED", null), siteManagerUser.getId()))
                .isInstanceOf(ApprovalAlreadyDecidedException.class);
    }

    private StudentCommentResponse writeDailyComment(String content, boolean isWarning) {
        return studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), "DAILY", classSession.id(), null,
                        LocalDate.now(), content, null, null, isWarning),
                teacher.getId());
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
