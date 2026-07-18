package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateGradeComponentRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DecideGradesRequest;
import vn.com.pps.education.dto.EnterGradePeriodResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.PeriodAverageResponse;
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateGradeComponentRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.GradeComponentLockedException;
import vn.com.pps.education.exception.GradeComponentWeightExceededException;
import vn.com.pps.education.exception.GradeEntryNotEditableException;
import vn.com.pps.education.exception.GradePeriodWeightExceededException;
import vn.com.pps.education.exception.InvalidGradeScoreException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-19: Nhập điểm — Main Flow (bước 1-6), A1 (điểm không hợp lệ), A2
 * (điểm bị từ chối) + UC-20: Duyệt điểm — Main Flow (bước 1-5), A1 (duyệt
 * tách lẻ). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class GradeServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private GradeService gradeService;

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
    private SiteManagerRepository siteManagerRepository;

    @Autowired
    private StudentRepository studentRepository;

    private User headAcademic;
    private User teacher;
    private User siteManagerUser;
    private ClassResponse schoolClass;
    private GradePeriodResponse gradePeriod;
    private GradeComponentResponse gradeComponent;
    private Student student;

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

        gradePeriod = gradeService.createGradePeriod(activeCurriculum.id(),
                new CreateGradePeriodRequest("MID_1", "Giữa kỳ 1", 1, new BigDecimal("50"), null, null), headAcademic.getId());
        gradeComponent = gradeService.addGradeComponent(gradePeriod.id(),
                new CreateGradeComponentRequest(null, null, "SPEAKING", "Nói", new BigDecimal("100"), new BigDecimal("10.00"), null, null, 1),
                headAcademic.getId());

        student = newStudent();
    }

    @Test
    void createGradePeriod_rejectsWhenWeightExceeds100() {
        assertThatThrownBy(() -> gradeService.createGradePeriod(gradePeriod.curriculumId(),
                new CreateGradePeriodRequest("END_1", "Cuối kỳ 1", 2, new BigDecimal("60"), null, null), headAcademic.getId()))
                .isInstanceOf(GradePeriodWeightExceededException.class);
    }

    @Test
    void updateGradeComponent_rejectsWeightChangeWhenEntriesExist() {
        gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.0"), false, null), teacher.getId());

        assertThatThrownBy(() -> gradeService.updateGradeComponent(gradeComponent.id(),
                new UpdateGradeComponentRequest("Nói", new BigDecimal("50"), null, null, 1), headAcademic.getId()))
                .isInstanceOf(GradeComponentLockedException.class);
    }

    @Test
    void addGradeComponent_UC16_A2_vuotTongTrongSo_bloc() {
        // setUp() đã tạo "Nói" weight=100 trong gradePeriod -> thêm bất kỳ weight > 0 nữa sẽ vượt 100.
        assertThatThrownBy(() -> gradeService.addGradeComponent(gradePeriod.id(),
                new CreateGradeComponentRequest(null, null, "WRITING", "Viết", new BigDecimal("1"), null, null, null, 2),
                headAcademic.getId()))
                .isInstanceOf(GradeComponentWeightExceededException.class);
    }

    @Test
    void addGradeComponent_UC16_A2_themThanhCongKhongCanDuyetKhung_thanhCong() {
        // Khung đang ACTIVE, đã có lớp dùng (schoolClass) -- vẫn thêm được, không cần qua UC-16b/17.
        GradePeriodResponse newPeriod = gradeService.createGradePeriod(gradePeriod.curriculumId(),
                new CreateGradePeriodRequest("END_1", "Cuối kỳ 1", 2, new BigDecimal("50"), null, null), headAcademic.getId());

        GradeComponentResponse component = gradeService.addGradeComponent(newPeriod.id(),
                new CreateGradeComponentRequest(null, null, "GRAMMAR", "Ngữ pháp", new BigDecimal("100"),
                        new BigDecimal("9.00"), null, "BAND", 1),
                headAcademic.getId());

        assertThat(component.code()).isEqualTo("GRAMMAR");
        assertThat(component.scaleType()).isEqualTo("BAND");
        assertThat(gradeService.listGradeComponents(newPeriod.id())).extracting(GradeComponentResponse::id)
                .contains(component.id());
    }

    @Test
    void enterPeriodResult_UC53_luuOverallLevel_thanhCong() {
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("6.5"), "BAND", "B2"), teacher.getId());

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.overallScore()).isEqualByComparingTo("6.5");
        assertThat(result.scaleType()).isEqualTo("BAND");
        assertThat(result.level()).isEqualTo("B2");
        assertThat(result.source()).isEqualTo("MANUAL");
    }

    @Test
    void enterPeriodResult_UC53_suaKhiRejected_thanhCong() {
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("5.0"), "BAND", "B1"), teacher.getId());
        gradeService.submitGrades(schoolClass.id(),
                new SubmitGradesRequest(null, List.of(result.id())), teacher.getId());
        gradeService.decideGrades(new DecideGradesRequest(null, List.of(result.id()), "REJECTED", "Sai band"),
                siteManagerUser.getId());

        GradePeriodResultResponse reentered = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("6.0"), "BAND", "B2"), teacher.getId());

        assertThat(reentered.status()).isEqualTo("DRAFT");
        assertThat(reentered.overallScore()).isEqualByComparingTo("6.0");
    }

    @Test
    void enterGrade_UC19_MainFlow_savesDraftEntry() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.5"), false, "Tốt"), teacher.getId());

        assertThat(entry.status()).isEqualTo("DRAFT");
        assertThat(entry.score()).isEqualByComparingTo("8.5");
    }

    @Test
    void enterGrade_UC19_A1_rejectsScoreOutOfRange() {
        assertThatThrownBy(() -> gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("10.5"), false, null), teacher.getId()))
                .isInstanceOf(InvalidGradeScoreException.class);

        assertThatThrownBy(() -> gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("-1"), false, null), teacher.getId()))
                .isInstanceOf(InvalidGradeScoreException.class);
    }

    @Test
    void enterGrade_UC19_Precondition_supportsHeadAcademicEnteringOnBehalfOfTeacher() {
        // Mở rộng ngoài SDD gốc, đã xác nhận với người dùng: HEAD_ACADEMIC (quyền academic.grade.manage) nhập thay được.
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), headAcademic.getId());

        assertThat(entry.status()).isEqualTo("DRAFT");
    }

    @Test
    void enterGrade_UC19_Precondition_supportsSiteManagerOfClassSiteEnteringOnBehalfOfTeacher() {
        // Mở rộng ngoài SDD gốc, đã xác nhận với người dùng: Quản lý điểm trường phụ trách đúng site của lớp nhập thay được.
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("7"), false, null), siteManagerUser.getId());

        assertThat(entry.status()).isEqualTo("DRAFT");
    }

    @Test
    void enterGrade_rejectsWhenActorNotAssignedTeacher() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void submitGrades_UC19_MainFlow_transitionsToPending() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());

        List<GradeEntryResponse> submitted = gradeService.submitGrades(schoolClass.id(),
                new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());

        assertThat(submitted).hasSize(1);
        assertThat(submitted.get(0).status()).isEqualTo("PENDING");
        assertThat(gradeService.listPendingForSite(siteManagerUser.getId()))
                .extracting(GradeEntryResponse::id).contains(entry.id());
    }

    @Test
    void decideGrades_UC20_MainFlow_approvedPublishesGrade() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId());
        gradeService.submitGrades(schoolClass.id(), new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());

        List<GradeEntryResponse> decided = gradeService.decideGrades(
                new DecideGradesRequest(List.of(entry.id()), null, "APPROVED", "Tốt"), siteManagerUser.getId());

        assertThat(decided.get(0).status()).isEqualTo("APPROVED");
    }

    @Test
    void decideGrades_UC20_MainFlow_rejectedAndUC19_A2_reenterLoopsBackToDraft() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("4"), false, null), teacher.getId());
        gradeService.submitGrades(schoolClass.id(), new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());

        List<GradeEntryResponse> decided = gradeService.decideGrades(
                new DecideGradesRequest(List.of(entry.id()), null, "REJECTED", "Điểm không hợp lý"), siteManagerUser.getId());
        assertThat(decided.get(0).status()).isEqualTo("REJECTED");

        // UC-19 A2 -- Giáo viên sửa lại, quay về DRAFT, submit lại được.
        GradeEntryResponse reentered = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("7"), false, "Đã chấm lại"), teacher.getId());
        assertThat(reentered.status()).isEqualTo("DRAFT");
        List<GradeEntryResponse> resubmitted = gradeService.submitGrades(schoolClass.id(),
                new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());
        assertThat(resubmitted.get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void decideGrades_UC20_A1_approvesOneEntryIndependentlyInBatch() {
        Student student2 = newStudent();
        GradeEntryResponse entry1 = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        GradeEntryResponse entry2 = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student2.getId(), new BigDecimal("6"), false, null), teacher.getId());
        gradeService.submitGrades(schoolClass.id(), new SubmitGradesRequest(List.of(entry1.id(), entry2.id()), null), teacher.getId());

        // A1 -- duyệt tách lẻ chỉ entry1, entry2 vẫn PENDING.
        gradeService.decideGrades(new DecideGradesRequest(List.of(entry1.id()), null, "APPROVED", null), siteManagerUser.getId());

        assertThat(gradeService.listPendingForSite(siteManagerUser.getId()))
                .extracting(GradeEntryResponse::id).containsExactly(entry2.id());
    }

    @Test
    void decideGrades_rejectsWhenActorNotSiteManagerForSite() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.submitGrades(schoolClass.id(), new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());
        User outsiderManager = newUser("outsider.sitemanager");
        assignRole(outsiderManager, "SITE_MANAGER");

        assertThatThrownBy(() -> gradeService.decideGrades(
                new DecideGradesRequest(List.of(entry.id()), null, "APPROVED", null), outsiderManager.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void decideGrades_rejectsWhenAlreadyDecided() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.submitGrades(schoolClass.id(), new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());
        gradeService.decideGrades(new DecideGradesRequest(List.of(entry.id()), null, "APPROVED", null), siteManagerUser.getId());

        assertThatThrownBy(() -> gradeService.decideGrades(
                new DecideGradesRequest(List.of(entry.id()), null, "APPROVED", null), siteManagerUser.getId()))
                .isInstanceOf(ApprovalAlreadyDecidedException.class);
    }

    @Test
    void enterGrade_rejectsEditingPendingEntry() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.submitGrades(schoolClass.id(), new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());

        assertThatThrownBy(() -> gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId()))
                .isInstanceOf(GradeEntryNotEditableException.class);
    }

    @Test
    void getPeriodAverage_UC19_Postcondition_computesWeightedAverage() {
        // Tạo kỳ riêng với 2 thành phần 60/40 (tổng đúng 100 — UC-16 A2 giờ validate trần này).
        GradePeriodResponse endPeriod = gradeService.createGradePeriod(gradePeriod.curriculumId(),
                new CreateGradePeriodRequest("END_1", "Cuối kỳ 1", 2, new BigDecimal("50"), null, null), headAcademic.getId());
        GradeComponentResponse speaking = gradeService.addGradeComponent(endPeriod.id(),
                new CreateGradeComponentRequest(null, null, "SPEAKING", "Nói", new BigDecimal("60"), new BigDecimal("10.00"), null, null, 1),
                headAcademic.getId());
        GradeComponentResponse writing = gradeService.addGradeComponent(endPeriod.id(),
                new CreateGradeComponentRequest(null, null, "WRITING", "Viết", new BigDecimal("40"), new BigDecimal("10.00"), null, null, 2),
                headAcademic.getId());
        gradeService.enterGrade(schoolClass.id(), speaking.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.enterGrade(schoolClass.id(), writing.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("6"), false, null), teacher.getId());

        PeriodAverageResponse average = gradeService.getPeriodAverage(schoolClass.id(), student.getId(), endPeriod.id());

        // (8*60 + 6*40) / (60+40) = 720/100 = 7.20
        assertThat(average.componentsEntered()).isEqualTo(2);
        assertThat(average.componentsTotal()).isEqualTo(2);
        assertThat(average.average()).isEqualByComparingTo("7.20");
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
