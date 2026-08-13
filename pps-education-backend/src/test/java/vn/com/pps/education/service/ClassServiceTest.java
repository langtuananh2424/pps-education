package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateAcademicYearRequest;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateCustomCurriculumRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DecideCurriculumApprovalRequest;
import vn.com.pps.education.dto.EndTeacherAssignmentRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.PromoteClassRequest;
import vn.com.pps.education.dto.PromoteClassResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.WithdrawEnrollmentRequest;
import vn.com.pps.education.exception.ClassEnrollmentAlreadyActiveException;
import vn.com.pps.education.exception.CurriculumNotActiveException;
import vn.com.pps.education.exception.CurriculumNotAvailableForSiteException;
import vn.com.pps.education.exception.DuplicateClassCodeException;
import vn.com.pps.education.exception.LinkedClassRequiresPartnerSiteException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.SiteTeacherRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-18: Xếp lớp & gán khóa học — Main Flow (bước 1-6), A2 (thiếu Điểm
 * trường cho Lớp liên kết). Xem docs/uc/phan-he-06-hoc-thuat.md.
 * A1 (trùng phòng học) không áp dụng — xem Javadoc ClassService.
 */
@Transactional
class ClassServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private AcademicYearService academicYearService;

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
    private SiteManagerRepository siteManagerRepository;

    @Autowired
    private SiteTeacherRepository siteTeacherRepository;

    @Autowired
    private ClassEnrollmentRepository classEnrollmentRepository;

    private User headAcademic;
    private CurriculumResponse activeCurriculum;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());
    }

    @Test
    void create_UC18_MainFlow_createsOpenClassAndWritesHistory() {
        Site site = newSite(Site.SiteType.OWNED);

        ClassResponse response = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN",
                        25, 10, LocalDate.now(), null, null),
                headAcademic.getId());

        assertThat(response.id()).isNotNull();
        // e185328: startDate = hôm nay -> lớp tự động IN_PROGRESS khi tạo (không còn PLANNED).
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.classCategory()).isEqualTo(activeCurriculum.classCategory());
    }

    @Test
    void create_rejectsWhenCurriculumNotActive() {
        CurriculumResponse draft = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Draft", "MAIN", null, null, null), headAcademic.getId());
        Site site = newSite(Site.SiteType.OWNED);

        assertThatThrownBy(() -> classService.create(
                new CreateClassRequest(classCode(), "X", site.getId(), draft.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId()))
                .isInstanceOf(CurriculumNotActiveException.class);
    }

    @Test
    void create_UC18_A2_rejectsLinkedClassAtNonPartnerSite() {
        Site ownedSite = newSite(Site.SiteType.OWNED);

        assertThatThrownBy(() -> classService.create(
                new CreateClassRequest(classCode(), "Lớp liên kết", ownedSite.getId(), activeCurriculum.id(), "LINKED",
                        20, null, LocalDate.now(), null, null), headAcademic.getId()))
                .isInstanceOf(LinkedClassRequiresPartnerSiteException.class);
    }

    @Test
    void create_UC18_A2_allowsLinkedClassAtPartnerSite() {
        Site partnerSite = newSite(Site.SiteType.PARTNER);

        ClassResponse response = classService.create(
                new CreateClassRequest(classCode(), "Lớp liên kết hợp lệ", partnerSite.getId(), activeCurriculum.id(),
                        "LINKED", 20, null, LocalDate.now(), null, null),
                headAcademic.getId());

        assertThat(response.classType()).isEqualTo("LINKED");
    }

    @Test
    void create_rejectsCustomCurriculumUsedAtWrongSite() {
        Site customSite = newSite(Site.SiteType.OWNED);
        User siteManagerUser = newUser("site.manager.forclass");
        assignRole(siteManagerUser, "SITE_MANAGER");
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(customSite);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);
        CurriculumResponse customCurriculum = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), activeCurriculum.id(), customSite.getId(), null),
                siteManagerUser.getId());
        var submitted = curriculumService.submitForApproval(customCurriculum.id(), siteManagerUser.getId());
        curriculumService.decideApproval(submitted.id(),
                new DecideCurriculumApprovalRequest("APPROVED", null), headAcademic.getId());

        Site otherSite = newSite(Site.SiteType.OWNED);

        assertThatThrownBy(() -> classService.create(
                new CreateClassRequest(classCode(), "Sai site", otherSite.getId(), customCurriculum.id(), "OPEN",
                        20, null, LocalDate.now(), null, null), headAcademic.getId()))
                .isInstanceOf(CurriculumNotAvailableForSiteException.class);
    }

    @Test
    void assignTeacher_UC18_MainFlow_persistsAssignment() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Có GV", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User teacher = newUser("teacher.assigned");
        assignRole(teacher, "TEACHER");

        var assignment = classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        assertThat(classService.listTeachers(schoolClass.id())).containsExactly(assignment);
    }

    @Test
    void assignTeacher_UC18_A3_autoCreatesSiteTeacherLinkWhenMissing() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Tự động gán site", site.getId(), activeCurriculum.id(), "OPEN", 20,
                        null, LocalDate.now(), null, null), headAcademic.getId());
        User teacher = newUser("teacher.newsite");
        assignRole(teacher, "TEACHER");
        assertThat(siteTeacherRepository.existsBySiteIdAndTeacherIdAndAssignedToIsNull(site.getId(), teacher.getId())).isFalse();

        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        assertThat(siteTeacherRepository.existsBySiteIdAndTeacherIdAndAssignedToIsNull(site.getId(), teacher.getId())).isTrue();
    }

    @Test
    void assignTeacher_UC18_A3_skipsWhenTeacherAlreadyAssignedToSite() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse class1 = classService.create(
                new CreateClassRequest(classCode(), "Lớp 1", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        ClassResponse class2 = classService.create(
                new CreateClassRequest(classCode(), "Lớp 2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User teacher = newUser("teacher.samesite");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(class1.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        classService.assignTeacher(class2.id(),
                new AssignTeacherRequest(teacher.getId(), "ASSISTANT", null, LocalDate.now(), null), headAcademic.getId());

        assertThat(siteTeacherRepository.findByTeacherIdAndAssignedToIsNull(teacher.getId()))
                .hasSize(1); // khong tao ban ghi trung cho cung 1 site
    }

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-13 (UC-18): 1 lớp cho phép đồng thời 1 PRIMARY VIETNAMESE + 1 PRIMARY FOREIGN. */
    @Test
    void assignTeacher_UC18_AllowsConcurrentPrimaryVietnameseAndForeign() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "2 GV chính", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User vnTeacher = newUser("teacher.vn");
        assignRole(vnTeacher, "TEACHER");
        User foreignTeacher = newUser("teacher.foreign");
        assignRole(foreignTeacher, "TEACHER");

        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(vnTeacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());
        var foreignAssignment = classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(foreignTeacher.getId(), "PRIMARY", null, LocalDate.now(), "FOREIGN"), headAcademic.getId());

        assertThat(classService.listTeachers(schoolClass.id())).hasSize(2);
        assertThat(foreignAssignment.teacherType()).isEqualTo("FOREIGN");
    }

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-13 (UC-18): không cho 2 PRIMARY active cùng loại giáo viên trong 1 lớp. */
    @Test
    void assignTeacher_UC18_RejectsDuplicatePrimarySameTeacherType() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Trùng loại GV chính", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User teacher1 = newUser("teacher.dup1");
        assignRole(teacher1, "TEACHER");
        User teacher2 = newUser("teacher.dup2");
        assignRole(teacher2, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher1.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        assertThatThrownBy(() -> classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher2.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId()))
                .isInstanceOf(RuntimeException.class);
    }

    /** UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): đổi GV chính gộp end+assign trong 1 transaction. */
    @Test
    void changeTeacher_UC18_MainFlow_endsOldAssignsNewInSingleTransaction() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Đổi GV chính", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User oldTeacher = newUser("teacher.change.old");
        assignRole(oldTeacher, "TEACHER");
        User newTeacher = newUser("teacher.change.new");
        assignRole(newTeacher, "TEACHER");
        var oldAssignment = classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(oldTeacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        var newAssignment = classService.changeTeacher(schoolClass.id(), oldAssignment.id(),
                new vn.com.pps.education.dto.ChangeTeacherRequest(newTeacher.getId(), LocalDate.now()), headAcademic.getId());

        assertThat(newAssignment.teacherUserId()).isEqualTo(newTeacher.getId());
        assertThat(newAssignment.teacherType()).isEqualTo("VIETNAMESE");
        var reloadedOld = classService.listTeachers(schoolClass.id()).stream()
                .filter(t -> t.id().equals(oldAssignment.id())).findFirst().orElseThrow();
        assertThat(reloadedOld.assignedTo()).isEqualTo(LocalDate.now());
    }

    /** UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): lịch sử thay đổi GV phụ trách gộp mọi phân công của cả lớp, mới nhất trước. */
    @Test
    void listTeacherHistory_UC18_returnsHistoryAcrossAssignmentsNewestFirst() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Lịch sử GV", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User oldTeacher = newUser("teacher.history.old");
        assignRole(oldTeacher, "TEACHER");
        User newTeacher = newUser("teacher.history.new");
        assignRole(newTeacher, "TEACHER");
        var oldAssignment = classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(oldTeacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());
        var newAssignment = classService.changeTeacher(schoolClass.id(), oldAssignment.id(),
                new vn.com.pps.education.dto.ChangeTeacherRequest(newTeacher.getId(), LocalDate.now()), headAcademic.getId());

        var history = classService.listTeacherHistory(schoolClass.id());

        assertThat(history).hasSize(3); // CREATED (oldTeacher) -> UPDATED (kết thúc oldTeacher) -> CREATED (newTeacher)
        assertThat(history.get(0).classTeacherId()).isEqualTo(newAssignment.id());
        assertThat(history.get(0).action()).isEqualTo("CREATED");
        assertThat(history.get(0).details().get("teacherUserId")).isEqualTo(newTeacher.getId());
        assertThat(history.get(1).classTeacherId()).isEqualTo(oldAssignment.id());
        assertThat(history.get(1).action()).isEqualTo("UPDATED");
        assertThat(history.get(2).classTeacherId()).isEqualTo(oldAssignment.id());
        assertThat(history.get(2).action()).isEqualTo("CREATED");
    }

    /** UC-18 changeTeacher: chỉ áp dụng cho phân công PRIMARY, từ chối CM/ASSISTANT/SUBSTITUTE. */
    @Test
    void changeTeacher_UC18_rejectsWhenAssignmentIsNotPrimaryRole() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Đổi CM", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User cm = newUser("teacher.cm");
        assignRole(cm, "TEACHER");
        User newCm = newUser("teacher.cm.new");
        assignRole(newCm, "TEACHER");
        var cmAssignment = classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(cm.getId(), "CM", null, LocalDate.now(), null), headAcademic.getId());

        assertThatThrownBy(() -> classService.changeTeacher(schoolClass.id(), cmAssignment.id(),
                new vn.com.pps.education.dto.ChangeTeacherRequest(newCm.getId(), LocalDate.now()), headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endTeacherAssignment_boSung_MainFlow_setsAssignedToAndWritesHistory() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Đổi giáo viên theo kỳ", site.getId(), activeCurriculum.id(), "OPEN", 20,
                        null, LocalDate.now(), null, null), headAcademic.getId());
        User teacher = newUser("teacher.ended");
        assignRole(teacher, "TEACHER");
        var assignment = classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now().minusMonths(1), "VIETNAMESE"), headAcademic.getId());
        LocalDate endDate = LocalDate.now();

        var ended = classService.endTeacherAssignment(schoolClass.id(), assignment.id(),
                new EndTeacherAssignmentRequest(endDate), headAcademic.getId());

        assertThat(ended.assignedTo()).isEqualTo(endDate);
        assertThat(classService.listTeachers(schoolClass.id())).containsExactly(ended);
    }

    @Test
    void endTeacherAssignment_boSung_A1_rejectsWhenAlreadyEnded() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Kết thúc 2 lần", site.getId(), activeCurriculum.id(), "OPEN", 20,
                        null, LocalDate.now(), null, null), headAcademic.getId());
        User teacher = newUser("teacher.doubleend");
        assignRole(teacher, "TEACHER");
        var assignment = classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now().minusMonths(1), "VIETNAMESE"), headAcademic.getId());
        classService.endTeacherAssignment(schoolClass.id(), assignment.id(),
                new EndTeacherAssignmentRequest(LocalDate.now()), headAcademic.getId());

        assertThatThrownBy(() -> classService.endTeacherAssignment(schoolClass.id(), assignment.id(),
                new EndTeacherAssignmentRequest(LocalDate.now()), headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enroll_MainFlow_persistsEnrollment() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Có HS", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        Student student = newStudent();

        var enrollment = classService.enroll(schoolClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        assertThat(enrollment.status()).isEqualTo("ACTIVE");
        assertThat(classService.listEnrollments(schoolClass.id())).containsExactly(enrollment);
    }

    @Test
    void enroll_rejectsDuplicateActiveEnrollment() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Trùng ghi danh", site.getId(), activeCurriculum.id(), "OPEN",
                        20, null, LocalDate.now(), null, null), headAcademic.getId());
        Student student = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        assertThatThrownBy(() -> classService.enroll(schoolClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId()))
                .isInstanceOf(ClassEnrollmentAlreadyActiveException.class);
    }

    @Test
    void withdraw_MainFlow_setsWithdrawnStatus() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Rút lớp", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        Student student = newStudent();
        var enrollment = classService.enroll(schoolClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        var withdrawn = classService.withdraw(schoolClass.id(), enrollment.id(),
                new WithdrawEnrollmentRequest(LocalDate.now(), "Chuyển trường"), headAcademic.getId());

        assertThat(withdrawn.status()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.withdrawReason()).isEqualTo("Chuyển trường");
    }

    @Test
    void search_filtersBySiteId_returnsOnlyClassesAtThatSite() {
        Site siteA = newSite(Site.SiteType.OWNED);
        Site siteB = newSite(Site.SiteType.OWNED);
        ClassResponse classAtA = classService.create(
                new CreateClassRequest(classCode(), "Lớp A", siteA.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        classService.create(
                new CreateClassRequest(classCode(), "Lớp B", siteB.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        var result = classService.search(null, siteA.getId(), null, null, null, headAcademic.getId());

        assertThat(result).extracting(ClassResponse::id).containsExactly(classAtA.id());
    }

    @Test
    void search_filtersByCurriculumId_returnsOnlyClassesOfThatCurriculum() {
        Site site = newSite(Site.SiteType.OWNED);
        CurriculumResponse otherCurriculum = activeCurriculumWithCategory("MAIN");
        ClassResponse classOfActive = classService.create(
                new CreateClassRequest(classCode(), "Lớp chuẩn", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        classService.create(
                new CreateClassRequest(classCode(), "Lớp khác", site.getId(), otherCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        var result = classService.search(null, null, activeCurriculum.id(), null, null, headAcademic.getId());

        assertThat(result).extracting(ClassResponse::id).containsExactly(classOfActive.id());
    }

    @Test
    void search_filtersByClassCategory_returnsOnlyMatchingCategory() {
        Site site = newSite(Site.SiteType.OWNED);
        CurriculumResponse supplementaryCurriculum = activeCurriculumWithCategory("SUPPLEMENTARY");
        ClassResponse mainClass = classService.create(
                new CreateClassRequest(classCode(), "Lớp chuẩn", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        classService.create(
                new CreateClassRequest(classCode(), "Lớp bổ trợ", site.getId(), supplementaryCurriculum.id(), "OPEN",
                        20, null, LocalDate.now(), null, null), headAcademic.getId());

        var result = classService.search(null, null, null, "MAIN", null, headAcademic.getId());

        assertThat(result).extracting(ClassResponse::id).containsExactly(mainClass.id());
    }

    @Test
    void search_teacherWithoutSiteAssignment_seesNoClasses() {
        Site site = newSite(Site.SiteType.OWNED);
        classService.create(
                new CreateClassRequest(classCode(), "Lớp bất kỳ", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User teacher = newUser("teacher.nosite");
        assignRole(teacher, "TEACHER");

        assertThat(classService.search(null, null, null, null, null, teacher.getId())).isEmpty();
    }

    @Test
    void search_teacherWithSiteAssignment_seesOnlyOwnSiteClasses() {
        Site siteA = newSite(Site.SiteType.OWNED);
        Site siteB = newSite(Site.SiteType.OWNED);
        ClassResponse classAtA = classService.create(
                new CreateClassRequest(classCode(), "Lớp A", siteA.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        classService.create(
                new CreateClassRequest(classCode(), "Lớp B", siteB.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User teacher = newUser("teacher.ownsite");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(classAtA.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        var result = classService.search(null, null, null, null, null, teacher.getId());

        assertThat(result).extracting(ClassResponse::id).containsExactly(classAtA.id());
    }

    /**
     * Bổ sung (audit FE 2026-07-20): resolveAllowedSiteIds trước đây chỉ
     * cộng site theo site_teachers, bỏ sót site_managers -- Quản lý điểm
     * trường không kiêm giáo viên gọi GET /api/classes luôn ra rỗng, dù
     * Precondition UC-19 xác nhận rõ họ được thao tác trên lớp thuộc site
     * mình phụ trách.
     */
    @Test
    void search_siteManagerForSite_seesOwnSiteClasses() {
        Site siteA = newSite(Site.SiteType.OWNED);
        Site siteB = newSite(Site.SiteType.OWNED);
        ClassResponse classAtA = classService.create(
                new CreateClassRequest(classCode(), "Lớp A", siteA.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        classService.create(
                new CreateClassRequest(classCode(), "Lớp B", siteB.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User siteManagerUser = newUser("site.manager.search");
        assignRole(siteManagerUser, "SITE_MANAGER");
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(siteA);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);

        var result = classService.search(null, null, null, null, null, siteManagerUser.getId());

        assertThat(result).extracting(ClassResponse::id).containsExactly(classAtA.id());
    }

    /**
     * V64 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * academic.class.view-all cho Trưởng phòng đào tạo/Quản trị viên xem
     * mọi lớp không cần đứng tên class_teachers/site_managers — tách riêng
     * khỏi academic.class.manage (UC-18 "xếp lớp"). SYS_ADMIN có quyền này
     * (V64) nhưng KHÔNG có academic.class.manage (loại trừ cố ý từ V28) nên
     * test này cô lập đúng permission mới, không lẫn với academic.class.manage.
     */
    @Test
    void search_userWithClassViewAllPermission_seesClassesAcrossAllSites() {
        Site siteA = newSite(Site.SiteType.OWNED);
        Site siteB = newSite(Site.SiteType.OWNED);
        ClassResponse classAtA = classService.create(
                new CreateClassRequest(classCode(), "Lớp A", siteA.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        ClassResponse classAtB = classService.create(
                new CreateClassRequest(classCode(), "Lớp B", siteB.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        User sysAdminUser = newUser("sysadmin.viewall");
        assignRole(sysAdminUser, "SYS_ADMIN");

        var result = classService.search(null, null, null, null, null, sysAdminUser.getId());

        assertThat(result).extracting(ClassResponse::id).containsExactlyInAnyOrder(classAtA.id(), classAtB.id());
    }

    @Test
    void promoteClass_UC18b_MainFlow_movesActiveStudentsToNewClassKeepingSiteAndClassType() {
        Site partnerSite = newSite(Site.SiteType.PARTNER);
        ClassResponse oldClass = classService.create(
                new CreateClassRequest(classCode(), "6A", partnerSite.getId(), activeCurriculum.id(), "LINKED", 20, null,
                        LocalDate.now().minusYears(1), null, null), headAcademic.getId());
        Student student = newStudent();
        var oldEnrollment = classService.enroll(oldClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now().minusYears(1)), headAcademic.getId());
        CurriculumResponse newCurriculum = activeCurriculumWithCategory("MAIN");
        String newCode = classCode();
        LocalDate newStartDate = LocalDate.now();
        Long newAcademicYearId = newAcademicYear("2026-2027");

        PromoteClassResponse result = classService.promoteClass(oldClass.id(),
                new PromoteClassRequest(newCode, "7A", newCurriculum.id(), newAcademicYearId, newStartDate, null, 20, null),
                headAcademic.getId());

        assertThat(result.newClass().classCode()).isEqualTo(newCode);
        assertThat(result.newClass().siteId()).isEqualTo(partnerSite.getId());
        assertThat(result.newClass().classType()).isEqualTo("LINKED");
        assertThat(result.newClass().academicYear()).isEqualTo("2026-2027");
        assertThat(result.movedStudentCount()).isEqualTo(1);
        assertThat(result.skippedStudentCount()).isZero();

        var newEnrollments = classService.listEnrollments(result.newClass().id());
        assertThat(newEnrollments).hasSize(1);
        assertThat(newEnrollments.get(0).studentId()).isEqualTo(student.getId());
        assertThat(newEnrollments.get(0).status()).isEqualTo("ACTIVE");
        assertThat(newEnrollments.get(0).academicYear()).isEqualTo("2026-2027");

        ClassEnrollment reloadedOldEnrollment = classEnrollmentRepository.findById(oldEnrollment.id()).orElseThrow();
        assertThat(reloadedOldEnrollment.getStatus()).isEqualTo(ClassEnrollment.Status.TRANSFERRED);
        assertThat(reloadedOldEnrollment.getWithdrawnDate()).isEqualTo(newStartDate);
    }

    @Test
    void promoteClass_UC18b_A1_skipsStudentNotActive() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse oldClass = classService.create(
                new CreateClassRequest(classCode(), "6B", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now().minusYears(1), null, null), headAcademic.getId());
        Student suspendedStudent = newStudent();
        suspendedStudent.setStatus(Student.Status.SUSPENDED);
        studentRepository.save(suspendedStudent);
        var oldEnrollment = classService.enroll(oldClass.id(),
                new EnrollStudentRequest(suspendedStudent.getId(), LocalDate.now().minusYears(1)), headAcademic.getId());

        PromoteClassResponse result = classService.promoteClass(oldClass.id(),
                new PromoteClassRequest(classCode(), "7B", activeCurriculum.id(), newAcademicYear("2026-2027"), LocalDate.now(), null, 20, null),
                headAcademic.getId());

        assertThat(result.movedStudentCount()).isZero();
        assertThat(result.skippedStudentCount()).isEqualTo(1);
        assertThat(result.skippedStudents().get(0).studentId()).isEqualTo(suspendedStudent.getId());
        assertThat(classService.listEnrollments(result.newClass().id())).isEmpty();

        ClassEnrollment reloadedOldEnrollment = classEnrollmentRepository.findById(oldEnrollment.id()).orElseThrow();
        assertThat(reloadedOldEnrollment.getStatus()).isEqualTo(ClassEnrollment.Status.ACTIVE);
    }

    @Test
    void promoteClass_UC18b_A2_rejectsDuplicateClassCode() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse oldClass = classService.create(
                new CreateClassRequest(classCode(), "6C", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now().minusYears(1), null, null), headAcademic.getId());
        String existingCode = classCode();
        classService.create(
                new CreateClassRequest(existingCode, "Lớp đã có mã", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        assertThatThrownBy(() -> classService.promoteClass(oldClass.id(),
                new PromoteClassRequest(existingCode, "7C", activeCurriculum.id(), newAcademicYear("2026-2027"), LocalDate.now(), null, 20, null),
                headAcademic.getId()))
                .isInstanceOf(DuplicateClassCodeException.class);
    }

    @Test
    void promoteClass_UC18b_A3_rejectsWhenNewCurriculumNotActive() {
        Site site = newSite(Site.SiteType.OWNED);
        ClassResponse oldClass = classService.create(
                new CreateClassRequest(classCode(), "6D", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now().minusYears(1), null, null), headAcademic.getId());
        CurriculumResponse draftCurriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chưa duyệt", "MAIN", null, null, null), headAcademic.getId());

        assertThatThrownBy(() -> classService.promoteClass(oldClass.id(),
                new PromoteClassRequest(classCode(), "7D", draftCurriculum.id(), newAcademicYear("2026-2027"), LocalDate.now(), null, 20, null),
                headAcademic.getId()))
                .isInstanceOf(CurriculumNotActiveException.class);
    }

    private CurriculumResponse activeCurriculumWithCategory(String classCategory) {
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), classCategory, classCategory, null, null, null),
                headAcademic.getId());
        return curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest(classCategory, null, null, null, "ACTIVE", false), headAcademic.getId());
    }

    private Long newAcademicYear(String code) {
        return academicYearService.create(new CreateAcademicYearRequest(code, code, null, null),
                headAcademic.getId()).id();
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

    private Site newSite(Site.SiteType type) {
        Site site = new Site();
        site.setCode("SITE-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(type);
        return siteRepository.save(site);
    }

    private Student newStudent() {
        User user = newUser("student.forclass");
        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
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
