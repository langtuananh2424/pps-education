package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateCustomCurriculumRequest;
import vn.com.pps.education.dto.CurriculumApprovalResponse;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DecideCurriculumApprovalRequest;
import vn.com.pps.education.dto.UpdateCustomCurriculumRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.CurriculumNotEditableException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-16b: Đề xuất khung chương trình tùy biến + UC-17: Phê duyệt khung
 * chương trình tùy biến. Main Flow, A1 (lưu nháp / đề xuất lại sau từ
 * chối). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class CurriculumCustomizationTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

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

    private User headAcademic;
    private CurriculumResponse standardCurriculum;
    private User siteManagerUser;
    private Site site;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        standardCurriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn gốc", "MAIN", "Lớp 8", 90, null), headAcademic.getId());

        site = newSite();
        siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        assignSiteManager(site, siteManagerUser);
    }

    @Test
    void createCustomCopy_UC16b_MainFlow_copiesFieldsFromParentAndLinksSite() {
        CurriculumResponse copy = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                siteManagerUser.getId());

        assertThat(copy.siteId()).isEqualTo(site.getId());
        assertThat(copy.parentCurriculumId()).isEqualTo(standardCurriculum.id());
        assertThat(copy.status()).isEqualTo("DRAFT");
        assertThat(copy.level()).isEqualTo("Lớp 8");
        assertThat(copy.totalPeriods()).isEqualTo(90);
    }

    @Test
    void createCustomCopy_rejectsWhenActorNotSiteManagerForSite() {
        User outsider = newUser("not.site.manager");
        assignRole(outsider, "SITE_MANAGER");

        assertThatThrownBy(() -> curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                outsider.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void createCustomCopy_rejectsWhenParentIsAlreadyCustomized() {
        CurriculumResponse copy = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                siteManagerUser.getId());

        assertThatThrownBy(() -> curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), copy.id(), site.getId(), null),
                siteManagerUser.getId()))
                .isInstanceOf(CurriculumNotEditableException.class);
    }

    @Test
    void updateCustomCopy_UC16b_A1_allowsEditingWhileDraft() {
        CurriculumResponse copy = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                siteManagerUser.getId());

        CurriculumResponse updated = curriculumService.updateCustomCopy(copy.id(),
                new UpdateCustomCurriculumRequest("Tên tùy biến riêng", "Lớp 8 nâng cao", 100, null),
                siteManagerUser.getId());

        assertThat(updated.name()).isEqualTo("Tên tùy biến riêng");
        assertThat(updated.totalPeriods()).isEqualTo(100);
        assertThat(updated.status()).isEqualTo("DRAFT");
    }

    @Test
    void submitForApproval_UC16b_MainFlow_transitionsToPendingApprovalAndCreatesApprovalFlow() {
        CurriculumResponse copy = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                siteManagerUser.getId());

        CurriculumApprovalResponse approval = curriculumService.submitForApproval(copy.id(), siteManagerUser.getId());

        assertThat(approval.status()).isEqualTo("PENDING");
        assertThat(curriculumService.getById(copy.id()).status()).isEqualTo("PENDING_APPROVAL");
        assertThat(curriculumService.listPendingApprovals(headAcademic.getId()))
                .extracting(CurriculumApprovalResponse::curriculumId).contains(copy.id());
    }

    @Test
    void updateCustomCopy_rejectsWhenNotDraft() {
        CurriculumResponse copy = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                siteManagerUser.getId());
        curriculumService.submitForApproval(copy.id(), siteManagerUser.getId());

        assertThatThrownBy(() -> curriculumService.updateCustomCopy(copy.id(),
                new UpdateCustomCurriculumRequest("X", null, null, null), siteManagerUser.getId()))
                .isInstanceOf(CurriculumNotEditableException.class);
    }

    @Test
    void decideApproval_UC17_MainFlow_approvedActivatesCurriculum() {
        CurriculumResponse copy = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                siteManagerUser.getId());
        CurriculumApprovalResponse submitted = curriculumService.submitForApproval(copy.id(), siteManagerUser.getId());

        CurriculumApprovalResponse decided = curriculumService.decideApproval(submitted.id(),
                new DecideCurriculumApprovalRequest("APPROVED", "Đạt yêu cầu"), headAcademic.getId());

        assertThat(decided.status()).isEqualTo("APPROVED");
        assertThat(curriculumService.getById(copy.id()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void decideApproval_UC17_MainFlow_rejectedRevertsToDraftAndRequiresComment() {
        CurriculumResponse copy = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                siteManagerUser.getId());
        CurriculumApprovalResponse submitted = curriculumService.submitForApproval(copy.id(), siteManagerUser.getId());

        assertThatThrownBy(() -> curriculumService.decideApproval(submitted.id(),
                new DecideCurriculumApprovalRequest("REJECTED", null), headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        CurriculumApprovalResponse decided = curriculumService.decideApproval(submitted.id(),
                new DecideCurriculumApprovalRequest("REJECTED", "Thiếu học phần Nói"), headAcademic.getId());

        assertThat(decided.status()).isEqualTo("REJECTED");
        assertThat(curriculumService.getById(copy.id()).status()).isEqualTo("DRAFT");
    }

    @Test
    void decideApproval_UC17_A1_resubmitAfterRejectionGoesBackToPending() {
        CurriculumResponse copy = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                siteManagerUser.getId());
        CurriculumApprovalResponse firstSubmit = curriculumService.submitForApproval(copy.id(), siteManagerUser.getId());
        curriculumService.decideApproval(firstSubmit.id(),
                new DecideCurriculumApprovalRequest("REJECTED", "Cần sửa lại"), headAcademic.getId());

        curriculumService.updateCustomCopy(copy.id(),
                new UpdateCustomCurriculumRequest("Đã sửa", null, null, null), siteManagerUser.getId());
        CurriculumApprovalResponse resubmit = curriculumService.submitForApproval(copy.id(), siteManagerUser.getId());

        assertThat(resubmit.id()).isNotEqualTo(firstSubmit.id());
        assertThat(curriculumService.getById(copy.id()).status()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void decideApproval_rejectsWhenAlreadyDecided() {
        CurriculumResponse copy = curriculumService.createCustomCopy(
                new CreateCustomCurriculumRequest(curriculumCode(), standardCurriculum.id(), site.getId(), null),
                siteManagerUser.getId());
        CurriculumApprovalResponse submitted = curriculumService.submitForApproval(copy.id(), siteManagerUser.getId());
        curriculumService.decideApproval(submitted.id(),
                new DecideCurriculumApprovalRequest("APPROVED", null), headAcademic.getId());

        assertThatThrownBy(() -> curriculumService.decideApproval(submitted.id(),
                new DecideCurriculumApprovalRequest("APPROVED", null), headAcademic.getId()))
                .isInstanceOf(ApprovalAlreadyDecidedException.class);
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private void assignSiteManager(Site targetSite, User manager) {
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(targetSite);
        siteManager.setUser(manager);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(manager);
        siteManagerRepository.save(siteManager);
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
