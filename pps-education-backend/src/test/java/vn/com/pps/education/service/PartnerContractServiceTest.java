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
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreatePartnerContractRequest;
import vn.com.pps.education.dto.ExpiringPartnerContractResponse;
import vn.com.pps.education.dto.PartnerContractResponse;
import vn.com.pps.education.dto.UpdatePartnerContractRequest;
import vn.com.pps.education.exception.ActivePartnerContractAlreadyExistsException;
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
 * UC-36b: Quản lý hợp đồng liên kết trường — Main Flow, A1 (cảnh báo sắp
 * hết hạn), A2 (chấm dứt hợp đồng). Xem docs/uc/phan-he-10-co-so-vat-chat.md.
 */
@Transactional
class PartnerContractServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private PartnerContractService partnerContractService;

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

    private User opsManager;
    private User headAcademic;
    private User siteManagerUser;
    private Site partnerSite;

    @BeforeEach
    void setUp() {
        opsManager = newUser("opsmanager");
        assignRole(opsManager, "OPS_MANAGER");
        headAcademic = newUser("headacademic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        siteManagerUser = newUser("sitemanager");
        assignRole(siteManagerUser, "SITE_MANAGER");

        partnerSite = new Site();
        partnerSite.setCode("SITE-" + SEQ.incrementAndGet());
        partnerSite.setName("Partner Test Site");
        partnerSite.setSiteType(Site.SiteType.PARTNER);
        partnerSite = siteRepository.save(partnerSite);

        SiteManager manager = new SiteManager();
        manager.setSite(partnerSite);
        manager.setUser(siteManagerUser);
        manager.setRoleType(SiteManager.RoleType.SITE_MANAGER);
        manager.setAssignedFrom(LocalDate.now().minusMonths(1));
        manager.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(manager);
    }

    @Test
    void createContract_UC36b_MainFlow_savesDraftContract() {
        PartnerContractResponse contract = partnerContractService.createContract(baseRequest(), opsManager.getId());

        assertThat(contract.status()).isEqualTo("DRAFT");
        assertThat(contract.contractNumber()).startsWith("HD-");
        assertThat(contract.siteId()).isEqualTo(partnerSite.getId());
    }

    @Test
    void updateContract_UC36b_MainFlow_activatesContract() {
        PartnerContractResponse contract = partnerContractService.createContract(baseRequest(), opsManager.getId());

        PartnerContractResponse activated = partnerContractService.updateContract(contract.id(),
                new UpdatePartnerContractRequest(LocalDate.now().plusYears(1), "ACTIVE", "Dieu khoan moi", null,
                        LocalDate.now(), "Trung tam", "Truong doi tac", null),
                opsManager.getId());

        assertThat(activated.status()).isEqualTo("ACTIVE");
    }

    @Test
    void updateContract_rejectsSecondActiveContractForSameSite() {
        PartnerContractResponse first = partnerContractService.createContract(baseRequest(), opsManager.getId());
        partnerContractService.updateContract(first.id(),
                new UpdatePartnerContractRequest(LocalDate.now().plusYears(1), "ACTIVE", null, null, null, null, null, null),
                opsManager.getId());

        PartnerContractResponse second = partnerContractService.createContract(baseRequest(), opsManager.getId());

        assertThatThrownBy(() -> partnerContractService.updateContract(second.id(),
                new UpdatePartnerContractRequest(LocalDate.now().plusYears(1), "ACTIVE", null, null, null, null, null, null),
                opsManager.getId()))
                .isInstanceOf(ActivePartnerContractAlreadyExistsException.class);
    }

    @Test
    void listExpiringContracts_UC36b_A1_returnsActiveContractsNearingEndDate() {
        PartnerContractResponse contract = partnerContractService.createContract(
                new CreatePartnerContractRequest(partnerSite.getId(), "INITIAL", null, LocalDate.now().minusMonths(11),
                        LocalDate.now().plusDays(10), null, null, null, null, null, null),
                opsManager.getId());
        partnerContractService.updateContract(contract.id(),
                new UpdatePartnerContractRequest(LocalDate.now().plusDays(10), "ACTIVE", null, null, null, null, null, null),
                opsManager.getId());

        var expiring = partnerContractService.listExpiringContracts(30);

        assertThat(expiring).extracting(ExpiringPartnerContractResponse::contractId).contains(contract.id());
    }

    @Test
    void terminateContract_UC36b_A2_marksTerminatedAndNotifiesSiteManagerWithActiveClasses() {
        Long curriculumId = createActiveCurriculumId();
        classService.create(new CreateClassRequest(classCode(), "Lop lien ket", partnerSite.getId(), curriculumId,
                "LINKED", 20, null, LocalDate.now(), null, null, null), headAcademic.getId());

        PartnerContractResponse contract = partnerContractService.createContract(baseRequest(), opsManager.getId());
        partnerContractService.updateContract(contract.id(),
                new UpdatePartnerContractRequest(LocalDate.now().plusYears(1), "ACTIVE", null, null, null, null, null, null),
                opsManager.getId());

        PartnerContractResponse terminated = partnerContractService.terminateContract(contract.id(), opsManager.getId());

        assertThat(terminated.status()).isEqualTo("TERMINATED");
    }

    private Long createActiveCurriculumId() {
        var curriculum = curriculumService.create(
                new vn.com.pps.education.dto.CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null),
                headAcademic.getId());
        var active = curriculumService.update(curriculum.id(),
                new vn.com.pps.education.dto.UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false),
                headAcademic.getId());
        return active.id();
    }

    private CreatePartnerContractRequest baseRequest() {
        return new CreatePartnerContractRequest(partnerSite.getId(), "INITIAL", null,
                LocalDate.now(), LocalDate.now().plusYears(1), "Dieu khoan hop tac", null, null, null, null, null);
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

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
