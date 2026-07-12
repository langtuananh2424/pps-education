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
import vn.com.pps.education.dto.AddFeedbackExchangeRequest;
import vn.com.pps.education.dto.PartnerFeedbackResponse;
import vn.com.pps.education.dto.ResolveFeedbackRequest;
import vn.com.pps.education.dto.SubmitPartnerFeedbackRequest;
import vn.com.pps.education.exception.InvalidFeedbackStatusTransitionException;
import vn.com.pps.education.exception.NotAuthorizedForFeedbackException;
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
 * UC-38: Gửi phản hồi tới Quản lý điểm trường — Main Flow, A1 (xem lại
 * lịch sử đã gửi) + UC-39: Xử lý phản hồi từ trường liên kết — Main Flow
 * (Mới → Đang xử lý → Đã giải quyết → Đóng), A1 (trao đổi qua lại). Xem
 * docs/uc/phan-he-10-co-so-vat-chat.md.
 */
@Transactional
class PartnerFeedbackServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private PartnerFeedbackService partnerFeedbackService;

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

    private User partnerRep;
    private User siteManagerUser;
    private Site partnerSite;

    @BeforeEach
    void setUp() {
        partnerSite = new Site();
        partnerSite.setCode("SITE-" + SEQ.incrementAndGet());
        partnerSite.setName("Partner Test Site");
        partnerSite.setSiteType(Site.SiteType.PARTNER);
        partnerSite = siteRepository.save(partnerSite);

        partnerRep = newUser("partnerrep");
        assignRole(partnerRep, "PARTNER_REP");
        SiteManager repAssignment = new SiteManager();
        repAssignment.setSite(partnerSite);
        repAssignment.setUser(partnerRep);
        repAssignment.setRoleType(SiteManager.RoleType.PARTNER_REP);
        repAssignment.setAssignedFrom(LocalDate.now().minusMonths(1));
        repAssignment.setAssignedBy(partnerRep);
        siteManagerRepository.save(repAssignment);

        siteManagerUser = newUser("sitemanager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        SiteManager managerAssignment = new SiteManager();
        managerAssignment.setSite(partnerSite);
        managerAssignment.setUser(siteManagerUser);
        managerAssignment.setRoleType(SiteManager.RoleType.SITE_MANAGER);
        managerAssignment.setAssignedFrom(LocalDate.now().minusMonths(1));
        managerAssignment.setAssignedBy(siteManagerUser);
        siteManagerRepository.save(managerAssignment);
    }

    @Test
    void submitFeedback_UC38_MainFlow_createsNewFeedbackAssignedToSiteManager() {
        PartnerFeedbackResponse feedback = partnerFeedbackService.submitFeedback(
                new SubmitPartnerFeedbackRequest("TEACHER", "Giáo viên thường xuyên đến trễ", "HIGH"), partnerRep.getId());

        assertThat(feedback.status()).isEqualTo("NEW");
        assertThat(feedback.assignedTo()).isEqualTo(siteManagerUser.getId());
        assertThat(feedback.priority()).isEqualTo("HIGH");
    }

    @Test
    void listMySubmittedFeedbacks_UC38_A1_returnsOwnFeedbackHistory() {
        partnerFeedbackService.submitFeedback(new SubmitPartnerFeedbackRequest("CLASS", "Lớp học đông quá", null), partnerRep.getId());

        assertThat(partnerFeedbackService.listMySubmittedFeedbacks(partnerRep.getId())).hasSize(1);
    }

    @Test
    void fullLifecycle_UC39_MainFlow_newToInProgressToResolvedToClosed() {
        PartnerFeedbackResponse submitted = partnerFeedbackService.submitFeedback(
                new SubmitPartnerFeedbackRequest("OPERATIONS", "Phòng học thiếu máy chiếu", null), partnerRep.getId());

        PartnerFeedbackResponse inProgress = partnerFeedbackService.startProcessing(submitted.id(), siteManagerUser.getId());
        assertThat(inProgress.status()).isEqualTo("IN_PROGRESS");

        PartnerFeedbackResponse resolved = partnerFeedbackService.resolve(submitted.id(),
                new ResolveFeedbackRequest("Đã lắp thêm máy chiếu mới"), siteManagerUser.getId());
        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.resolutionNotes()).isEqualTo("Đã lắp thêm máy chiếu mới");

        PartnerFeedbackResponse closed = partnerFeedbackService.close(submitted.id(), siteManagerUser.getId());
        assertThat(closed.status()).isEqualTo("CLOSED");
    }

    @Test
    void addExchange_UC39_A1_allowsBothPartnerRepAndSiteManagerBeforeClosed() {
        PartnerFeedbackResponse submitted = partnerFeedbackService.submitFeedback(
                new SubmitPartnerFeedbackRequest("TEACHER", "Cần trao đổi thêm", null), partnerRep.getId());
        partnerFeedbackService.startProcessing(submitted.id(), siteManagerUser.getId());

        partnerFeedbackService.addExchange(submitted.id(), new AddFeedbackExchangeRequest("Anh/chị cho em xin thêm chi tiết"), siteManagerUser.getId());
        PartnerFeedbackResponse afterReply = partnerFeedbackService.addExchange(submitted.id(),
                new AddFeedbackExchangeRequest("Giáo viên A dạy lớp 8A2 tuần trước"), partnerRep.getId());

        assertThat(afterReply.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void addExchange_rejectsOutsiderNotPartOfFeedback() {
        PartnerFeedbackResponse submitted = partnerFeedbackService.submitFeedback(
                new SubmitPartnerFeedbackRequest("TEACHER", "Nội dung", null), partnerRep.getId());
        User outsider = newUser("outsider");
        assignRole(outsider, "SITE_MANAGER");

        assertThatThrownBy(() -> partnerFeedbackService.addExchange(submitted.id(),
                new AddFeedbackExchangeRequest("Không liên quan"), outsider.getId()))
                .isInstanceOf(NotAuthorizedForFeedbackException.class);
    }

    @Test
    void resolve_rejectsWhenStatusNotInProgress() {
        PartnerFeedbackResponse submitted = partnerFeedbackService.submitFeedback(
                new SubmitPartnerFeedbackRequest("TEACHER", "Nội dung", null), partnerRep.getId());

        assertThatThrownBy(() -> partnerFeedbackService.resolve(submitted.id(),
                new ResolveFeedbackRequest("Giải quyết luôn"), siteManagerUser.getId()))
                .isInstanceOf(InvalidFeedbackStatusTransitionException.class);
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
