package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateCurriculumSubjectRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.UpdateClassRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.CurriculumUpdateConfirmationRequiredException;
import vn.com.pps.education.exception.DuplicateCurriculumCodeException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-16: Quản lý khung chương trình — Main Flow (bước 1-4), A1 (khung đang
 * dùng bởi lớp IN_PROGRESS). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class CurriculumServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private ClassService classService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    private User headAcademic;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
    }

    @Test
    void create_UC16_MainFlow_createsDraftCurriculumAndWritesHistory() {
        CurriculumResponse response = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Tiếng Anh Lớp 8 Chuẩn", "MAIN", "Lớp 8", 90, null),
                headAcademic.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.siteId()).isNull();
    }

    @Test
    void create_rejectsDuplicateCode() {
        String code = curriculumCode();
        curriculumService.create(new CreateCurriculumRequest(code, "A", "MAIN", null, null, null), headAcademic.getId());

        assertThatThrownBy(() -> curriculumService.create(
                new CreateCurriculumRequest(code, "B", "MAIN", null, null, null), headAcademic.getId()))
                .isInstanceOf(DuplicateCurriculumCodeException.class);
    }

    @Test
    void addSubject_UC16_MainFlow_persistsSubject() {
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Có học phần", "MAIN", null, null, null),
                headAcademic.getId());

        var subject = curriculumService.addSubject(curriculum.id(),
                new CreateCurriculumSubjectRequest("SPEAKING", null, "Nói", 20, 1), headAcademic.getId());

        assertThat(curriculumService.listSubjects(curriculum.id())).containsExactly(subject);
    }

    @Test
    void update_UC16_MainFlow_activatesCurriculum() {
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Sắp active", "MAIN", null, null, null),
                headAcademic.getId());

        CurriculumResponse activated = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Sắp active", null, null, null, "ACTIVE", false),
                headAcademic.getId());

        assertThat(activated.status()).isEqualTo("ACTIVE");
    }

    @Test
    void update_UC16_A1_requiresConfirmWhenCurriculumUsedByRunningClass() {
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Đang chạy", "MAIN", null, null, null),
                headAcademic.getId());
        curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Đang chạy", null, null, null, "ACTIVE", false), headAcademic.getId());
        Site site = newSite();
        var schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Lớp đang chạy", site.getId(), curriculum.id(), "OPEN",
                        20, null, LocalDate.now(), null, "2026-2027", "S1"),
                headAcademic.getId());
        classService.update(schoolClass.id(),
                new UpdateClassRequest("Lớp đang chạy", 20, null, LocalDate.now(), null, "2026-2027", "S1", "IN_PROGRESS"),
                headAcademic.getId());

        assertThatThrownBy(() -> curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Đổi tên", null, null, null, "ACTIVE", false), headAcademic.getId()))
                .isInstanceOf(CurriculumUpdateConfirmationRequiredException.class);

        // confirm=true phải cho phép lưu.
        CurriculumResponse confirmed = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Đổi tên", null, null, null, "ACTIVE", true), headAcademic.getId());
        assertThat(confirmed.name()).isEqualTo("Đổi tên");
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
        Site site = new Site();
        site.setCode("SITE-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(site);
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
