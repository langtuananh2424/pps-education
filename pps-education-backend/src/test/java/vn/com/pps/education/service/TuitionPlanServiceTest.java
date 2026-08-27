package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTuitionPlanRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateTuitionPlanRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.TuitionPlanResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateTuitionPlanStatusRequest;
import vn.com.pps.education.exception.TuitionPlanNotActiveException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Định mức học phí — hạ tầng cho UC-30 (không có UC riêng, xem Javadoc
 * TuitionPlanService). Test bổ sung: updateStatus (ACTIVE/INACTIVE) và
 * ràng buộc assignToClass chỉ chấp nhận plan đang ACTIVE.
 */
@Transactional
class TuitionPlanServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private TuitionPlanService tuitionPlanService;

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

    private User accountant;
    private User headAcademic;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;

    @BeforeEach
    void setUp() {
        accountant = newUser("accountant");
        assignRole(accountant, "STAFF");
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");

        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
    }

    @Test
    void createPlan_MainFlow_defaultsToActiveStatus() {
        TuitionPlanResponse plan = tuitionPlanService.createPlan(new CreateTuitionPlanRequest(
                planCode(), "Học phí tháng", activeCurriculum.id(), "MONTHLY", null,
                new BigDecimal("2000000"), new BigDecimal("2000000"), null, null, null), accountant.getId());

        assertThat(plan.status()).isEqualTo("ACTIVE");
    }

    @Test
    void assignToClass_MainFlow_succeedsForActivePlan() {
        TuitionPlanResponse plan = tuitionPlanService.createPlan(new CreateTuitionPlanRequest(
                planCode(), "Học phí tháng", activeCurriculum.id(), "MONTHLY", null,
                new BigDecimal("2000000"), new BigDecimal("2000000"), null, null, null), accountant.getId());

        var assignment = tuitionPlanService.assignToClass(
                new AssignTuitionPlanRequest(schoolClass.id(), plan.id(), null, null, null), accountant.getId());

        assertThat(assignment.tuitionPlanId()).isEqualTo(plan.id());
    }

    @Test
    void updateStatus_boSung_deactivatesPlan() {
        TuitionPlanResponse plan = tuitionPlanService.createPlan(new CreateTuitionPlanRequest(
                planCode(), "Học phí tháng", activeCurriculum.id(), "MONTHLY", null,
                new BigDecimal("2000000"), new BigDecimal("2000000"), null, null, null), accountant.getId());

        TuitionPlanResponse deactivated = tuitionPlanService.updateStatus(plan.id(),
                new UpdateTuitionPlanStatusRequest("INACTIVE"), accountant.getId());

        assertThat(deactivated.status()).isEqualTo("INACTIVE");
    }

    @Test
    void assignToClass_boSung_rejectsInactivePlan() {
        TuitionPlanResponse plan = tuitionPlanService.createPlan(new CreateTuitionPlanRequest(
                planCode(), "Học phí tháng cũ", activeCurriculum.id(), "MONTHLY", null,
                new BigDecimal("1500000"), new BigDecimal("1500000"), null, null, null), accountant.getId());
        tuitionPlanService.updateStatus(plan.id(), new UpdateTuitionPlanStatusRequest("INACTIVE"), accountant.getId());

        assertThatThrownBy(() -> tuitionPlanService.assignToClass(
                new AssignTuitionPlanRequest(schoolClass.id(), plan.id(), null, null, null), accountant.getId()))
                .isInstanceOf(TuitionPlanNotActiveException.class);
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private String planCode() {
        return "PLAN-" + SEQ.incrementAndGet();
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

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
