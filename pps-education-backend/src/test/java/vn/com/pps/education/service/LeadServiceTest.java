package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignLeadRequest;
import vn.com.pps.education.dto.CreateLeadRequest;
import vn.com.pps.education.dto.LeadResponse;
import vn.com.pps.education.dto.UpdateLeadStatusRequest;
import vn.com.pps.education.exception.DuplicateLeadPhoneException;
import vn.com.pps.education.exception.IncompleteLeadDataException;
import vn.com.pps.education.exception.InvalidLeadStatusTransitionException;
import vn.com.pps.education.exception.LeadNotQualifiedException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-33: Quản lý lead & tư vấn tuyển sinh — Main Flow, A1 (trùng phone),
 * A2 (LOST ngay từ đầu) + UC-34: Chuyển đổi lead thành học sinh — Main
 * Flow, A1 (không chốt), A2 (thiếu dữ liệu/chưa QUALIFIED). Xem
 * docs/uc/phan-he-09-crm.md.
 */
@Transactional
class LeadServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private LeadService leadService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private User staff;
    private User siteManager;

    @BeforeEach
    void setUp() {
        staff = newUser("staff");
        assignRole(staff, "STAFF");
        siteManager = newUser("sitemanager");
        assignRole(siteManager, "SITE_MANAGER");
    }

    @Test
    void createLead_UC33_MainFlow_savesNewLeadWithStatusNew() {
        LeadResponse lead = leadService.createLead(baseLeadRequest(phone()), staff.getId());

        assertThat(lead.status()).isEqualTo("NEW");
        assertThat(lead.leadCode()).startsWith("LEAD-");
    }

    @Test
    void createLead_UC33_A1_rejectsDuplicatePhone() {
        String phone = phone();
        leadService.createLead(baseLeadRequest(phone), staff.getId());

        assertThatThrownBy(() -> leadService.createLead(baseLeadRequest(phone), staff.getId()))
                .isInstanceOf(DuplicateLeadPhoneException.class);
    }

    @Test
    void assignLead_UC33_MainFlow_bySiteManagerOrStaff() {
        LeadResponse lead = leadService.createLead(baseLeadRequest(phone()), staff.getId());
        User consultant = newUser("consultant");
        assignRole(consultant, "STAFF");

        LeadResponse assigned = leadService.assignLead(lead.id(), new AssignLeadRequest(consultant.getId()), siteManager.getId());

        assertThat(assigned.assignedTo()).isEqualTo(consultant.getId());
        assertThat(leadService.listMyLeads(consultant.getId())).extracting(LeadResponse::id).contains(lead.id());
    }

    @Test
    void updateStatus_UC33_MainFlow_transitionsNewToContactedToQualified() {
        LeadResponse lead = leadService.createLead(baseLeadRequest(phone()), staff.getId());

        leadService.updateStatus(lead.id(), new UpdateLeadStatusRequest("CONTACTED", null, "Đã gọi, hẹn tư vấn thêm"), staff.getId());
        LeadResponse qualified = leadService.updateStatus(lead.id(),
                new UpdateLeadStatusRequest("QUALIFIED", null, "Phù hợp, muốn đăng ký"), staff.getId());

        assertThat(qualified.status()).isEqualTo("QUALIFIED");
    }

    @Test
    void updateStatus_UC33_A2_marksLostDirectlyWithReason() {
        LeadResponse lead = leadService.createLead(baseLeadRequest(phone()), staff.getId());

        LeadResponse lost = leadService.updateStatus(lead.id(),
                new UpdateLeadStatusRequest("LOST", "LOST_PRICE", "Học phí cao hơn ngân sách"), staff.getId());

        assertThat(lost.status()).isEqualTo("LOST");
        assertThat(lost.outcome()).isEqualTo("LOST_PRICE");
    }

    @Test
    void updateStatus_rejectsLostWithoutOutcome() {
        LeadResponse lead = leadService.createLead(baseLeadRequest(phone()), staff.getId());

        assertThatThrownBy(() -> leadService.updateStatus(lead.id(),
                new UpdateLeadStatusRequest("LOST", null, "không rõ lý do"), staff.getId()))
                .isInstanceOf(InvalidLeadStatusTransitionException.class);
    }

    @Test
    void updateStatus_rejectsWhenLeadAlreadyFinal() {
        LeadResponse lead = leadService.createLead(baseLeadRequest(phone()), staff.getId());
        leadService.updateStatus(lead.id(), new UpdateLeadStatusRequest("LOST", "LOST_OTHER", null), staff.getId());

        assertThatThrownBy(() -> leadService.updateStatus(lead.id(),
                new UpdateLeadStatusRequest("CONTACTED", null, null), staff.getId()))
                .isInstanceOf(InvalidLeadStatusTransitionException.class);
    }

    @Test
    void convertToStudent_UC34_MainFlow_createsParentAndStudentAndMarksWon() {
        LeadResponse lead = qualifiedLead();

        LeadResponse converted = leadService.convertToStudent(lead.id(), staff.getId());

        assertThat(converted.status()).isEqualTo("WON");
        assertThat(converted.outcome()).isEqualTo("WON_ENROLLED");
        assertThat(converted.convertedStudentId()).isNotNull();
    }

    @Test
    void convertToStudent_reusesExistingParentUserWhenPhoneMatches() {
        User existingParentUser = newUser("existing.parent");
        String sharedPhone = phone();
        existingParentUser.setPhone(sharedPhone);
        userRepository.save(existingParentUser);

        LeadResponse lead = qualifiedLead(sharedPhone);
        leadService.convertToStudent(lead.id(), staff.getId());

        // Không tạo thêm user mới cho parent — chỉ 1 user có phone này.
        assertThat(userRepository.findByPhone(sharedPhone)).isPresent();
    }

    @Test
    void convertToStudent_UC34_Precondition_rejectsWhenNotQualified() {
        LeadResponse lead = leadService.createLead(baseLeadRequest(phone()), staff.getId());

        assertThatThrownBy(() -> leadService.convertToStudent(lead.id(), staff.getId()))
                .isInstanceOf(LeadNotQualifiedException.class);
    }

    @Test
    void convertToStudent_rejectsWhenMissingStudentDob() {
        CreateLeadRequest request = new CreateLeadRequest(
                "Chị Lan", phone(), null, "MOTHER", "Bé Minh", null, "Lớp 3", "Tiểu học ABC",
                "WEBSITE", null, null, null);
        LeadResponse lead = leadService.createLead(request, staff.getId());
        leadService.updateStatus(lead.id(), new UpdateLeadStatusRequest("CONTACTED", null, null), staff.getId());
        leadService.updateStatus(lead.id(), new UpdateLeadStatusRequest("QUALIFIED", null, null), staff.getId());

        assertThatThrownBy(() -> leadService.convertToStudent(lead.id(), staff.getId()))
                .isInstanceOf(IncompleteLeadDataException.class);
    }

    private LeadResponse qualifiedLead() {
        return qualifiedLead(phone());
    }

    private LeadResponse qualifiedLead(String phone) {
        LeadResponse lead = leadService.createLead(baseLeadRequest(phone), staff.getId());
        leadService.updateStatus(lead.id(), new UpdateLeadStatusRequest("CONTACTED", null, null), staff.getId());
        return leadService.updateStatus(lead.id(), new UpdateLeadStatusRequest("QUALIFIED", null, null), staff.getId());
    }

    private CreateLeadRequest baseLeadRequest(String phone) {
        return new CreateLeadRequest(
                "Chị Lan", phone, "lan" + SEQ.incrementAndGet() + "@example.com", "MOTHER",
                "Bé Minh", LocalDate.of(2015, 3, 20), "Lớp 3", "Tiểu học ABC",
                "WEBSITE", null, null, "Quan tâm khóa tiếng Anh giao tiếp");
    }

    private String phone() {
        return "09" + (100000000L + SEQ.incrementAndGet());
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
