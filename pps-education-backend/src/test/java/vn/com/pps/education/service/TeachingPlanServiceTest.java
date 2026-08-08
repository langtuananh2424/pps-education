package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddTeachingPlanItemRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateAcademicYearRequest;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateTeachingPlanRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.TeachingPlanItemResponse;
import vn.com.pps.education.dto.TeachingPlanResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateTeachingPlanItemRequest;
import vn.com.pps.education.dto.UpdateTeachingPlanRequest;
import vn.com.pps.education.exception.InvalidTeachingPlanPeriodException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-28: Điền kế hoạch giảng dạy — Main Flow (bước 1-4), A1 (cập nhật kế hoạch đã gửi). */
@Transactional
class TeachingPlanServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private TeachingPlanService teachingPlanService;

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

    private User headAcademic;
    private User teacher;
    private ClassResponse schoolClass;
    private Long academicYearId;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        academicYearId = academicYearService.create(
                new CreateAcademicYearRequest("AY-" + SEQ.incrementAndGet(), "2026-2027", null, null),
                headAcademic.getId()).id();
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "LINKED", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());
    }

    @Test
    void createPlan_UC28_MainFlow_savesWeeklyDraftPlan() {
        TeachingPlanResponse plan = teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "WEEKLY", null, 1,
                        LocalDate.now(), LocalDate.now().plusDays(6), "Tuần 1: Chào hỏi", "HS chào hỏi được bằng tiếng Anh", true),
                teacher.getId());

        assertThat(plan.status()).isEqualTo("DRAFT");
        assertThat(plan.planType()).isEqualTo("WEEKLY");
        assertThat(plan.visibleToPartner()).isTrue();
    }

    @Test
    void createPlan_rejectsWeeklyWithoutDateRange() {
        assertThatThrownBy(() -> teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "WEEKLY", null, 1, null, null, "X", null, true),
                teacher.getId()))
                .isInstanceOf(InvalidTeachingPlanPeriodException.class);
    }

    @Test
    void createPlan_rejectsYearlyWithoutAcademicYear() {
        assertThatThrownBy(() -> teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "YEARLY", null, null, null, null, "X", null, true),
                teacher.getId()))
                .isInstanceOf(InvalidTeachingPlanPeriodException.class);
    }

    @Test
    void createPlan_rejectsWhenActorNotAssignedTeacher() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "YEARLY", academicYearId, null, null, null, "X", null, true),
                outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void createPlan_allowsAdminWithManagePermissionBypassingAssignedTeacherCheck() {
        User admin = newUser("teaching.plan.admin");
        assignRole(admin, "HEAD_ACADEMIC");

        TeachingPlanResponse plan = teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "YEARLY", academicYearId, null, null, null,
                        "Kế hoạch năm (quản trị nhập hộ)", "Mục tiêu", true),
                admin.getId());

        assertThat(plan.status()).isEqualTo("DRAFT");
        assertThat(plan.teacherId()).isEqualTo(admin.getId());
    }

    /** V107: vá lỗ hổng V106 (gán nhầm role SUPER_ADMIN không tồn tại) — SYS_ADMIN là tài khoản quản trị thực tế đang dùng. */
    @Test
    void createPlan_allowsSysAdminBypassingAssignedTeacherCheck() {
        User sysAdmin = newUser("teaching.plan.sysadmin");
        assignRole(sysAdmin, "SYS_ADMIN");

        TeachingPlanResponse plan = teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "YEARLY", academicYearId, null, null, null,
                        "Kế hoạch năm (SYS_ADMIN nhập hộ)", "Mục tiêu", true),
                sysAdmin.getId());

        assertThat(plan.status()).isEqualTo("DRAFT");
        assertThat(plan.teacherId()).isEqualTo(sysAdmin.getId());
    }

    @Test
    void updatePlan_UC28_A1_publishingMakesVisibleAndSetsPublishedAt() {
        TeachingPlanResponse plan = teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "YEARLY", academicYearId, null, null, null, "Kế hoạch năm", "Mục tiêu", true),
                teacher.getId());

        TeachingPlanResponse published = teachingPlanService.updatePlan(plan.id(),
                new UpdateTeachingPlanRequest("Kế hoạch năm - đã cập nhật", "Mục tiêu mới", "PUBLISHED", true), teacher.getId());

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.summary()).isEqualTo("Kế hoạch năm - đã cập nhật");
    }

    @Test
    void addItem_UC28_MainFlow_addsDetailItemToPlan() {
        TeachingPlanResponse plan = teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "WEEKLY", null, 1,
                        LocalDate.now(), LocalDate.now().plusDays(6), "Tuần 1", null, true),
                teacher.getId());

        TeachingPlanItemResponse item = teachingPlanService.addItem(plan.id(),
                new AddTeachingPlanItemRequest(1, LocalDate.now(), "Buổi 1: Chào hỏi",
                        "Chào hỏi cơ bản", "Giới thiệu bản thân, chào hỏi", "SPEAKING", "Học thuộc 10 từ mới", null),
                teacher.getId());

        assertThat(item.topic()).isEqualTo("Buổi 1: Chào hỏi");
        List<TeachingPlanItemResponse> items = teachingPlanService.listItems(plan.id());
        assertThat(items).extracting(TeachingPlanItemResponse::id).contains(item.id());
    }

    @Test
    void updateItem_UC28_A1_updatesItemContent() {
        TeachingPlanResponse plan = teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "WEEKLY", null, 1,
                        LocalDate.now(), LocalDate.now().plusDays(6), "Tuần 1", null, true),
                teacher.getId());
        TeachingPlanItemResponse item = teachingPlanService.addItem(plan.id(),
                new AddTeachingPlanItemRequest(1, LocalDate.now(), "Buổi 1: Chào hỏi",
                        "Chào hỏi cơ bản", "Giới thiệu bản thân, chào hỏi", "SPEAKING", "Học thuộc 10 từ mới", null),
                teacher.getId());

        TeachingPlanItemResponse updated = teachingPlanService.updateItem(plan.id(), item.id(),
                new UpdateTeachingPlanItemRequest(1, LocalDate.now(), "Buổi 1: Chào hỏi (đã sửa)",
                        "Chào hỏi nâng cao", "Giới thiệu bản thân, chào hỏi, tạm biệt", "SPEAKING,LISTENING",
                        "Học thuộc 15 từ mới", null),
                teacher.getId());

        assertThat(updated.id()).isEqualTo(item.id());
        assertThat(updated.topic()).isEqualTo("Buổi 1: Chào hỏi (đã sửa)");
        assertThat(updated.skillsFocus()).isEqualTo("SPEAKING,LISTENING");
        List<TeachingPlanItemResponse> items = teachingPlanService.listItems(plan.id());
        assertThat(items).filteredOn(i -> i.id().equals(item.id()))
                .extracting(TeachingPlanItemResponse::topic)
                .containsExactly("Buổi 1: Chào hỏi (đã sửa)");
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
        s.setSiteType(Site.SiteType.PARTNER);
        return siteRepository.save(s);
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
