package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.GradePeriodEditWindow;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
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
import vn.com.pps.education.dto.CreateGradeComponentSetupRequest;
import vn.com.pps.education.dto.CreateGradeEvaluationComponentRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterGradeEvaluationResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentSetupResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradeEvaluationComponentResponse;
import vn.com.pps.education.dto.GradeEvaluationResultResponse;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateGradeEvaluationComponentRequest;
import vn.com.pps.education.exception.GradeAlreadyPublishedException;
import vn.com.pps.education.exception.GradeComponentLockedException;
import vn.com.pps.education.exception.GradeComponentNotDeletableException;
import vn.com.pps.education.exception.GradeComponentSetupNotDeletableException;
import vn.com.pps.education.exception.GradeComponentSetupScaleMismatchException;
import vn.com.pps.education.exception.GradeNotEditableException;
import vn.com.pps.education.exception.InvalidGradeScoreException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.GradeEntryHistoryRepository;
import vn.com.pps.education.repository.GradeEntryRepository;
import vn.com.pps.education.repository.GradePeriodEditWindowRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-19: Nhập điểm — Main Flow (bước 1-4), A1 (điểm không hợp lệ), A2 (sửa/xoá
 * bản ghi không ở trạng thái cho phép), A3 (gửi duyệt thất bại) + UC-20:
 * Duyệt/Từ chối điểm — Main Flow (bước 1-5), A1 (từ chối), A2 (Quản lý sửa
 * bản ghi REJECTED rồi duyệt thẳng), A3 (duyệt/từ chối bản ghi không còn ở
 * trạng thái phù hợp). V44 (bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng — thay hẳn luồng "công bố dự kiến + phúc khảo" cũ): luồng 4 trạng
 * thái DRAFT → SUBMITTED → OFFICIAL / REJECTED. V95 (bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng — consolidate vào academic_terms): cấu hình
 * sổ điểm gắn (lớp, kỳ học, Giữa/Cuối kỳ) qua GradeComponentSetup thay
 * GradePeriod/GradeComponent theo curriculum. Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class GradeServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private GradeService gradeService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private AcademicSettingsService academicSettingsService;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private GradePeriodEditWindowRepository gradePeriodEditWindowRepository;

    @Autowired
    private GradeEntryRepository gradeEntryRepository;

    @Autowired
    private GradeEntryHistoryRepository gradeEntryHistoryRepository;

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
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentRepository parentStudentRepository;

    @Autowired
    private NotificationService notificationService;

    private User headAcademic;
    private User teacher;
    private User siteManagerUser;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;
    private AcademicTerm academicTerm;
    private GradeComponentSetupResponse gradeSetup;
    private GradeEvaluationComponentResponse gradeComponent;
    private Student student;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
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
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(siteManagerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(siteManager);

        gradeSetup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "MID_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        gradeComponent = gradeService.addGradeEvaluationComponent(gradeSetup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "SPEAKING", "Nói", new BigDecimal("10.00"), null, null, 1),
                headAcademic.getId());

        student = newStudent();
    }

    @Test
    void addGradeEvaluationComponent_UC16_rejectsWhenMaxScoreDoesNotMatchSetupScale() {
        // gradeSetup dùng scaleType POINT_10 (cận trên = 10) -- thêm component maxScore=100 (thang PERCENT) phải bị từ chối.
        assertThatThrownBy(() -> gradeService.addGradeEvaluationComponent(gradeSetup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "WRITING", "Viết", new BigDecimal("100.00"), null, null, 2),
                headAcademic.getId()))
                .isInstanceOf(GradeComponentSetupScaleMismatchException.class);
    }

    @Test
    void updateGradeEvaluationComponent_rejectsWhenNewMaxScoreDoesNotMatchSetupScale() {
        assertThatThrownBy(() -> gradeService.updateGradeEvaluationComponent(gradeComponent.id(),
                new UpdateGradeEvaluationComponentRequest("Nói", new BigDecimal("9.00"), null, 1), headAcademic.getId()))
                .isInstanceOf(GradeComponentSetupScaleMismatchException.class);
    }

    @Test
    void updateGradeEvaluationComponent_rejectsMaxScoreChangeWhenEntriesExist() {
        gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.0"), false, null), teacher.getId());

        assertThatThrownBy(() -> gradeService.updateGradeEvaluationComponent(gradeComponent.id(),
                new UpdateGradeEvaluationComponentRequest("Nói", new BigDecimal("50"), null, 1), headAcademic.getId()))
                .isInstanceOf(GradeComponentLockedException.class);
    }

    // ===================== UC-19 (bổ sung): xoá thành phần điểm / setup sổ điểm =====================

    @Test
    void deleteGradeEvaluationComponent_UC19_success_whenNoEntries() {
        gradeService.deleteGradeEvaluationComponent(gradeComponent.id(), headAcademic.getId());

        assertThat(gradeService.listGradeEvaluationComponents(gradeSetup.id()))
                .extracting(GradeEvaluationComponentResponse::id)
                .doesNotContain(gradeComponent.id());
    }

    @Test
    void deleteGradeEvaluationComponent_UC19_blockedWhenEntriesExist() {
        gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.0"), false, null), teacher.getId());

        assertThatThrownBy(() -> gradeService.deleteGradeEvaluationComponent(gradeComponent.id(), headAcademic.getId()))
                .isInstanceOf(GradeComponentNotDeletableException.class);
    }

    @Test
    void deleteGradeComponentSetup_UC19_blockedWhenHasComponent() {
        // gradeSetup (setUp) đang còn gradeComponent -> phải xoá thành phần trước.
        assertThatThrownBy(() -> gradeService.deleteGradeComponentSetup(gradeSetup.id(), headAcademic.getId()))
                .isInstanceOf(GradeComponentSetupNotDeletableException.class);
    }

    @Test
    void deleteGradeComponentSetup_UC19_blockedWhenHasResult() {
        GradeComponentSetupResponse emptySetup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "END_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        // Điểm tổng kết (Overall/Level) không cần thành phần điểm -> setup có result nhưng không có component.
        gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), emptySetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.0"), "NUMERIC", null, null, null, null), teacher.getId());

        assertThatThrownBy(() -> gradeService.deleteGradeComponentSetup(emptySetup.id(), headAcademic.getId()))
                .isInstanceOf(GradeComponentSetupNotDeletableException.class);
    }

    @Test
    void deleteGradeComponentSetup_UC19_success_afterDeletingItsComponent() {
        // Kịch bản thực tế: tạo Speaking nhầm -> xoá thành phần (chưa nhập điểm) -> xoá luôn setup rỗng.
        gradeService.deleteGradeEvaluationComponent(gradeComponent.id(), headAcademic.getId());

        gradeService.deleteGradeComponentSetup(gradeSetup.id(), headAcademic.getId());

        assertThat(gradeService.listGradeComponentSetups(schoolClass.id(), academicTerm.getId()))
                .extracting(GradeComponentSetupResponse::id)
                .doesNotContain(gradeSetup.id());
    }

    @Test
    void addGradeEvaluationComponent_UC16_A2_themThanhCongKhongCanDuyetKhung_thanhCong() {
        // Lớp đang có setup dùng rồi -- vẫn thêm được setup/thành phần mới, không cần qua UC-16b/17.
        GradeComponentSetupResponse newSetup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "END_TERM", "IELTS", LocalDate.now(), false),
                headAcademic.getId());

        GradeEvaluationComponentResponse component = gradeService.addGradeEvaluationComponent(newSetup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "GRAMMAR", "Ngữ pháp", new BigDecimal("9.00"),
                        null, "BAND", 1),
                headAcademic.getId());

        assertThat(component.code()).isEqualTo("GRAMMAR");
        assertThat(component.scaleType()).isEqualTo("BAND");
        assertThat(gradeService.listGradeEvaluationComponents(newSetup.id())).extracting(GradeEvaluationComponentResponse::id)
                .contains(component.id());
    }

    @Test
    void enterEvaluationResult_UC53_luuOverallLevel_thanhCong() {
        GradeEvaluationResultResponse result = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("6.5"), "BAND", "B2", "Tiến bộ tốt", null, null), teacher.getId());

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.overallScore()).isEqualByComparingTo("6.5");
        assertThat(result.scaleType()).isEqualTo("BAND");
        assertThat(result.level()).isEqualTo("B2");
        assertThat(result.comment()).isEqualTo("Tiến bộ tốt");
        assertThat(result.source()).isEqualTo("MANUAL");
    }

    @Test
    void enterEvaluationResult_suaLaiTruocKhiCongBo_thanhCong() {
        gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("5.0"), "BAND", "B1", null, null, null), teacher.getId());

        GradeEvaluationResultResponse reentered = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("6.0"), "BAND", "B2", null, null, null), teacher.getId());

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
    void enterGrade_UC19_MainFlow_draftEditableWithNoTimeLimit() {
        // V43+: bỏ hẳn hạn X ngày làm giới hạn sửa -- DRAFT sửa được vô thời hạn, kể cả sau mốc X ngày cũ.
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        expireEditWindow(schoolClass.id(), gradeSetup.id());

        GradeEntryResponse edited = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId());

        assertThat(edited.status()).isEqualTo("DRAFT");
        assertThat(edited.score()).isEqualByComparingTo("9");
        assertThat(entry.id()).isEqualTo(edited.id());
    }

    @Test
    void enterGrade_UC19_A2_rejectsEditWhenSubmittedWithoutOverride() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());

        assertThatThrownBy(() -> gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId()))
                .isInstanceOf(GradeNotEditableException.class);
    }

    @Test
    void enterGrade_allowsEditingOfficialWithOverridePermission() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        approveEntries(siteManagerUser.getId(), entry.id());

        // siteManagerUser có quyền academic.grade.edit.override mặc định (V39) -- bỏ qua mọi ràng buộc trạng thái (V44).
        GradeEntryResponse edited = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9.5"), false, "Sửa bởi HEAD"), siteManagerUser.getId());

        assertThat(edited.score()).isEqualByComparingTo("9.5");
        assertThat(edited.status()).isEqualTo("OFFICIAL");
    }

    @Test
    void enterGrade_UC20_A2_editableWhenRejectedByTeacherAndStaysRejected() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        rejectEntries(siteManagerUser.getId(), "Điểm chưa hợp lý", entry.id());

        // Sửa lúc REJECTED KHÔNG tự đổi trạng thái (UC-20 A2) -- vẫn REJECTED cho tới khi gửi duyệt lại hoặc Quản lý duyệt thẳng.
        GradeEntryResponse edited = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, "Sửa lại sau khi bị từ chối"), teacher.getId());

        assertThat(edited.status()).isEqualTo("REJECTED");
        assertThat(edited.score()).isEqualByComparingTo("9");
    }

    @Test
    void deleteGradeEntry_UC19_MainFlow_deletesDraftEntry() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());

        gradeService.deleteGradeEntry(schoolClass.id(), gradeComponent.id(), student.getId(), teacher.getId());

        assertThat(gradeEntryRepository.findById(entry.id())).isEmpty();
        assertThat(gradeEntryHistoryRepository.findAll()).noneMatch(h -> h.getGradeEntry().getId().equals(entry.id()));
    }

    @Test
    void deleteGradeEntry_rejectsWhenNotDraft() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());

        assertThatThrownBy(() -> gradeService.deleteGradeEntry(schoolClass.id(), gradeComponent.id(), student.getId(), teacher.getId()))
                .isInstanceOf(GradeNotEditableException.class);
        assertThat(gradeEntryRepository.findById(entry.id())).isPresent();
    }

    @Test
    void deleteEvaluationResult_UC53_MainFlow_deletesDraftResult() {
        GradeEvaluationResultResponse result = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("6.5"), "BAND", "B2", null, null, null), teacher.getId());

        gradeService.deleteEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(), teacher.getId());

        assertThat(gradeService.listEvaluationResults(schoolClass.id(), gradeSetup.id()))
                .extracting(GradeEvaluationResultResponse::id).doesNotContain(result.id());
    }

    @Test
    void deleteEvaluationResult_rejectsWhenNotDraft() {
        GradeEvaluationResultResponse result = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("6.5"), "BAND", "B2", null, null, null), teacher.getId());
        submitResults(result.id());

        assertThatThrownBy(() -> gradeService.deleteEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(), teacher.getId()))
                .isInstanceOf(GradeNotEditableException.class);
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

    // ===================== UC-19 Main Flow bước 4: Gửi duyệt =====================

    @Test
    void submitGradesForApproval_UC19_MainFlow_movesDraftEntryToSubmitted() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());

        List<GradeEntryResponse> submitted = gradeService.submitGradesForApproval(
                new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());

        assertThat(submitted.get(0).status()).isEqualTo("SUBMITTED");
    }

    @Test
    void submitGradesForApproval_UC19_A3_rejectsWhenAlreadySubmitted() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());

        assertThatThrownBy(() -> submitEntries(entry.id()))
                .isInstanceOf(GradeAlreadyPublishedException.class);
    }

    @Test
    void submitGradesForApproval_UC19_MainFlow_resubmitsRejectedEntry() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        rejectEntries(siteManagerUser.getId(), null, entry.id());

        List<GradeEntryResponse> resubmitted = gradeService.submitGradesForApproval(
                new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());

        assertThat(resubmitted.get(0).status()).isEqualTo("SUBMITTED");
    }

    // ===================== UC-20: Duyệt/Từ chối điểm =====================

    @Test
    void publishGrades_UC20_MainFlow_approvesSubmittedEntryToOfficial() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId());
        submitEntries(entry.id());

        List<GradeEntryResponse> approved = approveEntries(siteManagerUser.getId(), entry.id());

        assertThat(approved.get(0).status()).isEqualTo("OFFICIAL");
        assertThat(approved.get(0).publishedBy()).isEqualTo(siteManagerUser.getId());
        assertThat(approved.get(0).publishedAt()).isNotNull();
    }

    @Test
    void publishGrades_UC53_approvesEvaluationResultToOfficial() {
        GradeEvaluationResultResponse result = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("6.5"), "BAND", "B2", null, null, null), teacher.getId());
        submitResults(result.id());

        approveResults(siteManagerUser.getId(), result.id());

        assertThat(gradeService.listEvaluationResults(schoolClass.id(), gradeSetup.id()))
                .filteredOn(r -> r.id().equals(result.id()))
                .extracting(GradeEvaluationResultResponse::status)
                .containsExactly("OFFICIAL");
    }

    @Test
    void publishGrades_UC94_managerEditsCommentBeforeApproving() {
        GradeEvaluationResultResponse result = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("6.5"), "BAND", "B2", "Nhận xét gốc của GV", null, null), teacher.getId());
        submitResults(result.id());

        gradeService.publishGrades(new PublishGradesRequest("APPROVE", null, List.of(result.id()), null,
                java.util.Map.of(result.id(), "Nhận xét đã chỉnh sửa bởi Quản lý"), null), siteManagerUser.getId());

        GradeEvaluationResultResponse published = gradeService.listEvaluationResults(schoolClass.id(), gradeSetup.id()).stream()
                .filter(r -> r.id().equals(result.id())).findFirst().orElseThrow();
        assertThat(published.status()).isEqualTo("OFFICIAL");
        assertThat(published.comment()).isEqualTo("Nhận xét đã chỉnh sửa bởi Quản lý");
    }

    @Test
    void publishGrades_UC20_A1_rejectsSubmittedEntryAndNotifiesTeacher() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());

        List<GradeEntryResponse> rejected = rejectEntries(siteManagerUser.getId(), "Điểm chưa đúng", entry.id());

        assertThat(rejected.get(0).status()).isEqualTo("REJECTED");
        var notifications = notificationService.listMine(teacher.getId(), PageRequest.of(0, 10));
        assertThat(notifications.getContent())
                .anySatisfy(n -> {
                    assertThat(n.notificationType()).isEqualTo("GRADE_REJECTED");
                    assertThat(n.entityType()).isEqualTo("GRADE_ENTRY");
                    assertThat(n.entityId()).isEqualTo(entry.id());
                });
    }

    @Test
    void publishGrades_UC20_A2_managerEditsRejectedEntryThenApprovesDirectly() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        rejectEntries(siteManagerUser.getId(), "Sai điểm", entry.id());

        // Quản lý có quyền academic.grade.edit.override -- tự sửa bản ghi REJECTED (vẫn REJECTED sau khi sửa).
        GradeEntryResponse edited = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, "Quản lý tự sửa"), siteManagerUser.getId());
        assertThat(edited.status()).isEqualTo("REJECTED");

        // Duyệt thẳng REJECTED -> OFFICIAL, không cần Giáo viên gửi duyệt lại.
        List<GradeEntryResponse> approved = approveEntries(siteManagerUser.getId(), entry.id());

        assertThat(approved.get(0).status()).isEqualTo("OFFICIAL");
        assertThat(approved.get(0).score()).isEqualByComparingTo("9");
    }

    @Test
    void publishGrades_UC20_A3_rejectsApprovingAlreadyOfficialEntry() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        approveEntries(siteManagerUser.getId(), entry.id());

        assertThatThrownBy(() -> approveEntries(siteManagerUser.getId(), entry.id()))
                .isInstanceOf(GradeAlreadyPublishedException.class);
    }

    @Test
    void publishGrades_UC20_A3_rejectsRejectingEntryNotSubmitted() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        // Vẫn còn DRAFT -- chưa gửi duyệt.

        assertThatThrownBy(() -> rejectEntries(siteManagerUser.getId(), null, entry.id()))
                .isInstanceOf(GradeAlreadyPublishedException.class);
    }

    @Test
    void publishGrades_UC20_A1_publishesOneEntryIndependentlyInBatch() {
        Student student2 = newStudent();
        GradeEntryResponse entry1 = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        GradeEntryResponse entry2 = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student2.getId(), new BigDecimal("6"), false, null), teacher.getId());
        submitEntries(entry1.id(), entry2.id());

        // A1 -- duyệt tách lẻ chỉ entry1, entry2 vẫn SUBMITTED (chưa duyệt).
        approveEntries(siteManagerUser.getId(), entry1.id());

        assertThat(gradeService.listUnpublishedForSite(siteManagerUser.getId()))
                .extracting(GradeEntryResponse::id).containsExactly(entry2.id());
    }

    @Test
    void publishGrades_rejectsWhenActorNotSiteManagerForSite() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        User outsiderManager = newUser("outsider.sitemanager");
        assignRole(outsiderManager, "SITE_MANAGER");

        assertThatThrownBy(() -> approveEntries(outsiderManager.getId(), entry.id()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void publishGrades_UC20_Precondition_rejectsWhenActorHasNoGradeApprovePermission() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        // TEACHER không được cấp academic.grade.approve (V38/V77 chỉ gán cho SITE_MANAGER/HEAD_ACADEMIC).

        assertThatThrownBy(() -> approveEntries(teacher.getId(), entry.id()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void publishGrades_UC20_Precondition_supportsHeadAcademicApprovingAnySite() {
        // Mở rộng ngoài SDD gốc, đã xác nhận với người dùng: HEAD_ACADEMIC (academic.grade.approve
        // + academic.grade.manage) duyệt được dù không có bản ghi site_managers cho site của lớp.
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());

        List<GradeEntryResponse> approved = approveEntries(headAcademic.getId(), entry.id());

        assertThat(approved.get(0).status()).isEqualTo("OFFICIAL");
    }

    @Test
    void listUnpublishedForSite_UC20_Precondition_headAcademicSeesAllSitesNotJustOwnAssignment() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());

        assertThat(gradeService.listUnpublishedForSite(headAcademic.getId()))
                .extracting(GradeEntryResponse::id).contains(entry.id());
    }

    @Test
    void listMyGrades_UC61_MainFlow_returnsOfficialEntriesForEnrolledClass() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        approveEntries(siteManagerUser.getId(), entry.id());

        List<GradeEntryResponse> myGrades = gradeService.listMyGrades(student.getUser().getId(), null);

        assertThat(myGrades).extracting(GradeEntryResponse::id).contains(entry.id());
        assertThat(myGrades).extracting(GradeEntryResponse::status).containsOnly("OFFICIAL");
    }

    @Test
    void listMyGrades_UC61_excludesUnpublishedEntries() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        GradeEntryResponse draftEntry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());

        List<GradeEntryResponse> myGrades = gradeService.listMyGrades(student.getUser().getId(), null);

        assertThat(myGrades).extracting(GradeEntryResponse::id).doesNotContain(draftEntry.id());
    }

    @Test
    void listMyGrades_UC20_boSung_excludesRejectedEntries() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        rejectEntries(siteManagerUser.getId(), null, entry.id());

        List<GradeEntryResponse> myGrades = gradeService.listMyGrades(student.getUser().getId(), null);

        assertThat(myGrades).extracting(GradeEntryResponse::id).doesNotContain(entry.id());
    }

    @Test
    void getMyEvaluationResult_UC61_MainFlow_returnsOfficialResult() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        GradeEvaluationResultResponse result = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.5"), "BAND", "B2", null, null, null), teacher.getId());
        submitResults(result.id());
        approveResults(siteManagerUser.getId(), result.id());

        GradeEvaluationResultResponse myResult = gradeService.getMyEvaluationResult(
                student.getUser().getId(), schoolClass.id(), academicTerm.getId(), gradeSetup.evaluationType());

        assertThat(myResult.status()).isEqualTo("OFFICIAL");
        assertThat(myResult.level()).isEqualTo("B2");
        assertThat(myResult.overallScore()).isEqualByComparingTo("7.5");
    }

    @Test
    void getMyEvaluationResult_UC61_A_rejectsWhenNotYetPublished() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.5"), "BAND", "B2", null, null, null), teacher.getId());

        assertThatThrownBy(() -> gradeService.getMyEvaluationResult(
                student.getUser().getId(), schoolClass.id(), academicTerm.getId(), gradeSetup.evaluationType()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): điểm lớp cũ vẫn tự xem được sau khi chuyển lớp. */
    @Test
    void listMyGrades_boSung_stillVisibleAfterTransferToAnotherClass() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        submitEntries(entry.id());
        approveEntries(siteManagerUser.getId(), entry.id());
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "8A3", newSite().getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        studentService.recordTransfer(student.getId(),
                new RecordTransferRequest("CLASS_CHANGE", schoolClass.id(), otherClass.id(), null, LocalDate.now(), "Chuyển lớp test"),
                headAcademic.getId());

        List<GradeEntryResponse> myGrades = gradeService.listMyGrades(student.getUser().getId(), null);

        assertThat(myGrades).extracting(GradeEntryResponse::id).contains(entry.id());
    }

    /** Bổ sung (đã xác nhận với người dùng 2026-07-29): Overall/Level lớp cũ vẫn tự xem được sau khi chuyển lớp. */
    @Test
    void getMyEvaluationResult_boSung_stillVisibleAfterTransferToAnotherClass() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        GradeEvaluationResultResponse result = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.5"), "BAND", "B2", null, null, null), teacher.getId());
        submitResults(result.id());
        approveResults(siteManagerUser.getId(), result.id());
        ClassResponse otherClass = classService.create(
                new CreateClassRequest(classCode(), "8A3", newSite().getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        studentService.recordTransfer(student.getId(),
                new RecordTransferRequest("CLASS_CHANGE", schoolClass.id(), otherClass.id(), null, LocalDate.now(), "Chuyển lớp test"),
                headAcademic.getId());

        GradeEvaluationResultResponse myResult = gradeService.getMyEvaluationResult(
                student.getUser().getId(), schoolClass.id(), academicTerm.getId(), gradeSetup.evaluationType());

        assertThat(myResult.status()).isEqualTo("OFFICIAL");
    }

    @Test
    void getMyEvaluationResult_UC61_rejectsWhenStudentNotEnrolledInClass() {
        GradeEvaluationResultResponse result = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.5"), "BAND", "B2", null, null, null), teacher.getId());
        submitResults(result.id());
        approveResults(siteManagerUser.getId(), result.id());
        // Cố tình KHÔNG gọi classService.enroll -- học sinh chưa ghi danh lớp này.

        assertThatThrownBy(() -> gradeService.getMyEvaluationResult(
                student.getUser().getId(), schoolClass.id(), academicTerm.getId(), gradeSetup.evaluationType()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publishGrades_UC20_boSung_notifiesLinkedParentForGradeEntry() {
        User parentUser = newUser("parent.grade");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        linkParent(parent, student);
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.5"), false, null), teacher.getId());
        submitEntries(entry.id());

        approveEntries(siteManagerUser.getId(), entry.id());

        var notifications = notificationService.listMine(parentUser.getId(), PageRequest.of(0, 10));
        assertThat(notifications.getContent())
                .anySatisfy(n -> {
                    assertThat(n.notificationType()).isEqualTo("GRADE_PUBLISHED");
                    assertThat(n.entityType()).isEqualTo("GRADE_ENTRY");
                    assertThat(n.entityId()).isEqualTo(entry.id());
                });
    }

    @Test
    void publishGrades_UC20_boSung_notifiesLinkedParentForEvaluationResult() {
        User parentUser = newUser("parent.period");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        linkParent(parent, student);
        GradeEvaluationResultResponse result = gradeService.enterEvaluationResult(schoolClass.id(), student.getId(), gradeSetup.id(),
                new EnterGradeEvaluationResultRequest(new BigDecimal("7.5"), "BAND", "B2", null, null, null), teacher.getId());
        submitResults(result.id());

        approveResults(siteManagerUser.getId(), result.id());

        var notifications = notificationService.listMine(parentUser.getId(), PageRequest.of(0, 10));
        assertThat(notifications.getContent())
                .anySatisfy(n -> {
                    assertThat(n.notificationType()).isEqualTo("GRADE_PUBLISHED");
                    assertThat(n.entityType()).isEqualTo("GRADE_PERIOD_RESULT");
                    assertThat(n.entityId()).isEqualTo(result.id());
                });
    }

    @Test
    void publishGrades_UC20_boSung_doesNotFailWhenStudentHasNoLinkedParent() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.5"), false, null), teacher.getId());
        submitEntries(entry.id());

        var approved = approveEntries(siteManagerUser.getId(), entry.id());

        assertThat(approved).extracting(GradeEntryResponse::id).containsExactly(entry.id());
    }

    private void linkParent(Parent parent, Student student) {
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        parentStudentRepository.save(link);
    }

    private List<GradeEntryResponse> submitEntries(Long... entryIds) {
        return gradeService.submitGradesForApproval(new SubmitGradesRequest(List.of(entryIds), null), teacher.getId());
    }

    private void submitResults(Long... resultIds) {
        gradeService.submitGradesForApproval(new SubmitGradesRequest(null, List.of(resultIds)), teacher.getId());
    }

    private List<GradeEntryResponse> approveEntries(Long actorId, Long... entryIds) {
        return gradeService.publishGrades(new PublishGradesRequest("APPROVE", List.of(entryIds), null, null, null, null), actorId);
    }

    private void approveResults(Long actorId, Long... resultIds) {
        gradeService.publishGrades(new PublishGradesRequest("APPROVE", null, List.of(resultIds), null, null, null), actorId);
    }

    private List<GradeEntryResponse> rejectEntries(Long actorId, String reason, Long... entryIds) {
        return gradeService.publishGrades(new PublishGradesRequest("REJECT", List.of(entryIds), null, reason, null, null), actorId);
    }

    /** Đẩy lùi mốc "lần đầu nhập" (grade_period_edit_windows) quá hạn X ngày hiện hành -- dùng để mô phỏng mốc X ngày (thông tin, V39) hết hạn. */
    private void expireEditWindow(Long classId, Long setupId) {
        GradePeriodEditWindow window = gradePeriodEditWindowRepository
                .findBySchoolClassIdAndGradeComponentSetupId(classId, setupId).orElseThrow();
        window.setFirstEnteredAt(OffsetDateTime.now().minusDays(academicSettingsService.gradeEditWindowDays() + 1L));
        gradePeriodEditWindowRepository.save(window);
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
