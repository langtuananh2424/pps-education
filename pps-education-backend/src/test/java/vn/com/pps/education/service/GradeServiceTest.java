package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.GradePeriodEditWindow;
import vn.com.pps.education.domain.Notification;
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
import vn.com.pps.education.dto.CreateGradeComponentRequest;
import vn.com.pps.education.dto.CreateGradePeriodRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterGradePeriodResultRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeAppealResponse;
import vn.com.pps.education.dto.GradeComponentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.SubmitGradeAppealRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateGradeComponentRequest;
import vn.com.pps.education.exception.GradeAlreadyPublishedException;
import vn.com.pps.education.exception.GradeComponentLockedException;
import vn.com.pps.education.exception.GradeNotEditableException;
import vn.com.pps.education.exception.GradePeriodWeightExceededException;
import vn.com.pps.education.exception.InvalidGradeScoreException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
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
 * UC-19: Nhập điểm — Main Flow (bước 1-3), A1 (điểm không hợp lệ), A2 (sửa/xoá
 * bản ghi không ở trạng thái cho phép) + UC-20: Công bố điểm dự kiến — Main
 * Flow (bước 1-5), A1 (công bố tách lẻ), A2 (công bố lại bản ghi không còn
 * DRAFT). V43 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — sửa đổi
 * lần 2 sau V39): luồng 4 trạng thái DRAFT → PROVISIONAL_PUBLISHED → APPEAL
 * → OFFICIAL, khoá sửa/xoá theo TRẠNG THÁI (không còn theo hạn X ngày như
 * V39). Test UC-62 (phúc khảo, GradeAppealService) nằm ở GradeAppealServiceTest
 * riêng — file này chỉ test phần "sửa điểm trong lúc APPEAL" thuộc GradeService.
 * Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class GradeServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private GradeService gradeService;

    @Autowired
    private GradeAppealService gradeAppealService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private AcademicSettingsService academicSettingsService;

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
                new CreateGradeComponentRequest(null, null, "SPEAKING", "Nói", new BigDecimal("10.00"), null, null, 1),
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
    void updateGradeComponent_rejectsMaxScoreChangeWhenEntriesExist() {
        gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8.0"), false, null), teacher.getId());

        assertThatThrownBy(() -> gradeService.updateGradeComponent(gradeComponent.id(),
                new UpdateGradeComponentRequest("Nói", new BigDecimal("50"), null, 1), headAcademic.getId()))
                .isInstanceOf(GradeComponentLockedException.class);
    }

    @Test
    void addGradeComponent_UC16_A2_themThanhCongKhongCanDuyetKhung_thanhCong() {
        // Khung đang ACTIVE, đã có lớp dùng (schoolClass) -- vẫn thêm được, không cần qua UC-16b/17.
        GradePeriodResponse newPeriod = gradeService.createGradePeriod(gradePeriod.curriculumId(),
                new CreateGradePeriodRequest("END_1", "Cuối kỳ 1", 2, new BigDecimal("50"), null, null), headAcademic.getId());

        GradeComponentResponse component = gradeService.addGradeComponent(newPeriod.id(),
                new CreateGradeComponentRequest(null, null, "GRAMMAR", "Ngữ pháp", new BigDecimal("9.00"),
                        null, "BAND", 1),
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
    void enterPeriodResult_suaLaiTruocKhiCongBo_thanhCong() {
        gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("5.0"), "BAND", "B1"), teacher.getId());

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
    void enterGrade_UC19_MainFlow_draftEditableWithNoTimeLimit() {
        // V43: bỏ hẳn hạn X ngày làm giới hạn sửa -- DRAFT sửa được vô thời hạn, kể cả sau mốc X ngày cũ.
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        expireEditWindow(schoolClass.id(), gradePeriod.id());

        GradeEntryResponse edited = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId());

        assertThat(edited.status()).isEqualTo("DRAFT");
        assertThat(edited.score()).isEqualByComparingTo("9");
        assertThat(entry.id()).isEqualTo(edited.id());
    }

    @Test
    void enterGrade_UC19_A2_rejectsEditWhenProvisionalPublishedWithoutOverride() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());

        assertThatThrownBy(() -> gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId()))
                .isInstanceOf(GradeNotEditableException.class);
    }

    @Test
    void enterGrade_allowsEditingProvisionalPublishedWithOverridePermission() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());

        // siteManagerUser có quyền academic.grade.edit.override mặc định (V39) -- bỏ qua mọi ràng buộc trạng thái (V43).
        GradeEntryResponse edited = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9.5"), false, "Sửa bởi HEAD"), siteManagerUser.getId());

        assertThat(edited.score()).isEqualByComparingTo("9.5");
        assertThat(edited.status()).isEqualTo("PROVISIONAL_PUBLISHED");
    }

    @Test
    void enterGrade_UC62_MainFlow_editableDuringAcceptedAppealAndResolvesAppeal() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        GradeAppealResponse appeal = gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), "Nghĩ là chấm sai"), student.getUser().getId());
        gradeAppealService.acceptAppeal(appeal.id(), teacher.getId());

        GradeEntryResponse edited = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, "Chấm lại sau phúc khảo"), teacher.getId());

        assertThat(edited.status()).isEqualTo("PROVISIONAL_PUBLISHED");
        assertThat(edited.score()).isEqualByComparingTo("9");
        assertThat(gradeAppealService.listMyAppeals(student.getUser().getId()))
                .filteredOn(a -> a.id().equals(appeal.id()))
                .extracting(GradeAppealResponse::status)
                .containsExactly("RESOLVED");
    }

    @Test
    void enterGrade_UC62_A2_rejectsAppealEditByTeacherWhoDidNotAccept() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        gradeAppealService.submitAppeal(
                new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId());

        User otherTeacher = newUser("teacher.other");
        assignRole(otherTeacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(otherTeacher.getId(), "ASSISTANT", null, LocalDate.now()), headAcademic.getId());

        // otherTeacher là GV của lớp nhưng chưa tiếp nhận yêu cầu phúc khảo này -- không sửa được.
        assertThatThrownBy(() -> gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), otherTeacher.getId()))
                .isInstanceOf(GradeNotEditableException.class);
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
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());

        assertThatThrownBy(() -> gradeService.deleteGradeEntry(schoolClass.id(), gradeComponent.id(), student.getId(), teacher.getId()))
                .isInstanceOf(GradeNotEditableException.class);
        assertThat(gradeEntryRepository.findById(entry.id())).isPresent();
    }

    @Test
    void deletePeriodResult_UC53_MainFlow_deletesDraftResult() {
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("6.5"), "BAND", "B2"), teacher.getId());

        gradeService.deletePeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(), teacher.getId());

        assertThat(gradeService.listPeriodResults(schoolClass.id(), gradePeriod.id()))
                .extracting(GradePeriodResultResponse::id).doesNotContain(result.id());
    }

    @Test
    void deletePeriodResult_rejectsWhenNotDraft() {
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("6.5"), "BAND", "B2"), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(null, List.of(result.id())), siteManagerUser.getId());

        assertThatThrownBy(() -> gradeService.deletePeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(), teacher.getId()))
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

    @Test
    void publishGrades_UC20_MainFlow_publishesGradeAsProvisional() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId());

        List<GradeEntryResponse> published = gradeService.publishGrades(
                new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());

        assertThat(published.get(0).status()).isEqualTo("PROVISIONAL_PUBLISHED");
        assertThat(published.get(0).publishedBy()).isEqualTo(siteManagerUser.getId());
        assertThat(published.get(0).publishedAt()).isNotNull();
    }

    @Test
    void publishGrades_UC53_publishesPeriodResultAsProvisional() {
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("6.5"), "BAND", "B2"), teacher.getId());

        gradeService.publishGrades(new PublishGradesRequest(null, List.of(result.id())), siteManagerUser.getId());

        assertThat(gradeService.listPeriodResults(schoolClass.id(), gradePeriod.id()))
                .filteredOn(r -> r.id().equals(result.id()))
                .extracting(GradePeriodResultResponse::status)
                .containsExactly("PROVISIONAL_PUBLISHED");
    }

    @Test
    void publishGrades_UC20_A2_rejectsWhenAlreadyPublished() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());

        assertThatThrownBy(() -> gradeService.publishGrades(
                new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId()))
                .isInstanceOf(GradeAlreadyPublishedException.class);
    }

    @Test
    void publishGrades_UC20_A1_publishesOneEntryIndependentlyInBatch() {
        Student student2 = newStudent();
        GradeEntryResponse entry1 = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        GradeEntryResponse entry2 = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student2.getId(), new BigDecimal("6"), false, null), teacher.getId());

        // A1 -- công bố tách lẻ chỉ entry1, entry2 vẫn DRAFT (chưa công bố).
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry1.id()), null), siteManagerUser.getId());

        assertThat(gradeService.listUnpublishedForSite(siteManagerUser.getId()))
                .extracting(GradeEntryResponse::id).containsExactly(entry2.id());
    }

    @Test
    void publishGrades_rejectsWhenActorNotSiteManagerForSite() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        User outsiderManager = newUser("outsider.sitemanager");
        assignRole(outsiderManager, "SITE_MANAGER");

        assertThatThrownBy(() -> gradeService.publishGrades(
                new PublishGradesRequest(List.of(entry.id()), null), outsiderManager.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void publishGrades_UC20_Precondition_rejectsWhenActorHasNoGradePublishPermission() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        // TEACHER không được cấp academic.grade.publish (V38/V39 chỉ gán cho SITE_MANAGER/HEAD_ACADEMIC).

        assertThatThrownBy(() -> gradeService.publishGrades(
                new PublishGradesRequest(List.of(entry.id()), null), teacher.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void publishGrades_UC20_Precondition_supportsHeadAcademicPublishingAnySite() {
        // Mở rộng ngoài SDD gốc, đã xác nhận với người dùng: HEAD_ACADEMIC (academic.grade.publish
        // + academic.grade.manage) công bố được dù không có bản ghi site_managers cho site của lớp.
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());

        List<GradeEntryResponse> published = gradeService.publishGrades(
                new PublishGradesRequest(List.of(entry.id()), null), headAcademic.getId());

        assertThat(published.get(0).status()).isEqualTo("PROVISIONAL_PUBLISHED");
    }

    @Test
    void listUnpublishedForSite_UC20_Precondition_headAcademicSeesAllSitesNotJustOwnAssignment() {
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());

        assertThat(gradeService.listUnpublishedForSite(headAcademic.getId()))
                .extracting(GradeEntryResponse::id).contains(entry.id());
    }

    @Test
    void listMyGrades_UC61_MainFlow_returnsProvisionalPublishedEntriesForEnrolledClass() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());

        List<GradeEntryResponse> myGrades = gradeService.listMyGrades(student.getUser().getId(), null);

        assertThat(myGrades).extracting(GradeEntryResponse::id).contains(entry.id());
        assertThat(myGrades).extracting(GradeEntryResponse::status).containsOnly("PROVISIONAL_PUBLISHED");
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
    void listMyGrades_UC61_boSung_stillVisibleWhileUnderAppeal() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), gradeComponent.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("8"), false, null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());
        gradeAppealService.submitAppeal(new SubmitGradeAppealRequest("GRADE_ENTRY", entry.id(), null), student.getUser().getId());

        List<GradeEntryResponse> myGrades = gradeService.listMyGrades(student.getUser().getId(), null);

        assertThat(myGrades).filteredOn(e -> e.id().equals(entry.id()))
                .extracting(GradeEntryResponse::status).containsExactly("APPEAL");
    }

    @Test
    void getMyPeriodResult_UC61_MainFlow_returnsProvisionalPublishedResult() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("7.5"), "BAND", "B2"), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(null, List.of(result.id())), siteManagerUser.getId());

        GradePeriodResultResponse myResult = gradeService.getMyPeriodResult(student.getUser().getId(), schoolClass.id(), gradePeriod.id());

        assertThat(myResult.status()).isEqualTo("PROVISIONAL_PUBLISHED");
        assertThat(myResult.level()).isEqualTo("B2");
        assertThat(myResult.overallScore()).isEqualByComparingTo("7.5");
    }

    @Test
    void getMyPeriodResult_UC61_A_rejectsWhenNotYetPublished() {
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("7.5"), "BAND", "B2"), teacher.getId());

        assertThatThrownBy(() -> gradeService.getMyPeriodResult(student.getUser().getId(), schoolClass.id(), gradePeriod.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMyPeriodResult_UC61_rejectsWhenStudentNotEnrolledInClass() {
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("7.5"), "BAND", "B2"), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest(null, List.of(result.id())), siteManagerUser.getId());
        // Cố tình KHÔNG gọi classService.enroll -- học sinh chưa ghi danh lớp này.

        assertThatThrownBy(() -> gradeService.getMyPeriodResult(student.getUser().getId(), schoolClass.id(), gradePeriod.id()))
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

        gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());

        var notifications = notificationService.listMine(parentUser.getId(), PageRequest.of(0, 10));
        assertThat(notifications.getContent())
                .anySatisfy(n -> {
                    assertThat(n.notificationType()).isEqualTo("GRADE_PUBLISHED");
                    assertThat(n.entityType()).isEqualTo("GRADE_ENTRY");
                    assertThat(n.entityId()).isEqualTo(entry.id());
                });
    }

    @Test
    void publishGrades_UC20_boSung_notifiesLinkedParentForPeriodResult() {
        User parentUser = newUser("parent.period");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        linkParent(parent, student);
        GradePeriodResultResponse result = gradeService.enterPeriodResult(schoolClass.id(), student.getId(), gradePeriod.id(),
                new EnterGradePeriodResultRequest(new BigDecimal("7.5"), "BAND", "B2"), teacher.getId());

        gradeService.publishGrades(new PublishGradesRequest(null, List.of(result.id())), siteManagerUser.getId());

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

        var published = gradeService.publishGrades(new PublishGradesRequest(List.of(entry.id()), null), siteManagerUser.getId());

        assertThat(published).extracting(GradeEntryResponse::id).containsExactly(entry.id());
    }

    private void linkParent(Parent parent, Student student) {
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        parentStudentRepository.save(link);
    }

    /** Đẩy lùi mốc "lần đầu nhập" (grade_period_edit_windows) quá hạn X ngày hiện hành -- dùng để mô phỏng độ trễ tự động công bố dự kiến (UC-20 A3) hết hạn. */
    private void expireEditWindow(Long classId, Long gradePeriodId) {
        GradePeriodEditWindow window = gradePeriodEditWindowRepository
                .findBySchoolClassIdAndGradePeriodId(classId, gradePeriodId).orElseThrow();
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
