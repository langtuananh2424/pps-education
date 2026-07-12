package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.Lead;
import vn.com.pps.education.domain.LeadHistory;
import vn.com.pps.education.domain.LeadSource;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
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
import vn.com.pps.education.exception.NotAuthorizedForLeadManagementException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.LeadHistoryRepository;
import vn.com.pps.education.repository.LeadRepository;
import vn.com.pps.education.repository.LeadSourceRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-33: Quản lý lead & tư vấn tuyển sinh (FR-CRM-01, FR-CRM-02) + UC-34:
 * Chuyển đổi lead thành học sinh (FR-CRM-03). Xem
 * docs/uc/phan-he-09-crm.md + docs/sdd-groups/08-tuyen-sinh-and-crm.md.
 *
 * Không theo dõi lịch sử tư vấn chi tiết (SDD) — chỉ status + final_note
 * trên chính bảng leads, không có bảng lead_contacts riêng.
 *
 * UC-34 Main Flow bước 5 ("ghi nhận khoản đóng phí lần đầu vào Phân hệ
 * Tài chính") KHÔNG được cài đặt: cơ chế chuyển đổi chi tiết trong SDD
 * ("Cơ chế chuyển đổi, thực hiện trong 1 transaction") và
 * ActivityDiagram-ChuyenDoiLead.mmd đều chỉ liệt kê 4 bước (tạo
 * users+parents, tạo users+students, tạo parent_student, cập nhật lead)
 * — không có bước ghi hóa đơn/thanh toán nào, không có số tiền/tuition
 * plan nào được xác định tại thời điểm này. Coi đây là gap giống cách đã
 * note UC-29 (xuất PDF/Excel)/UC-31 (duyệt chi) — không tự bịa cơ chế ghi
 * nhận phí khi 2 nguồn tài liệu chi tiết hơn đều không mô tả.
 */
@Service
public class LeadService {

    private static final Set<String> STAFF_ROLE_CODES = Set.of("STAFF");
    private static final Set<String> ASSIGN_ROLE_CODES = Set.of("STAFF", "SITE_MANAGER");
    private static final String LEAD_CODE_PREFIX = "LEAD-";

    private final LeadRepository leadRepository;
    private final LeadHistoryRepository leadHistoryRepository;
    private final LeadSourceRepository leadSourceRepository;
    private final SiteRepository siteRepository;
    private final CurriculumRepository curriculumRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final StudentRepository studentRepository;

    public LeadService(LeadRepository leadRepository,
                        LeadHistoryRepository leadHistoryRepository,
                        LeadSourceRepository leadSourceRepository,
                        SiteRepository siteRepository,
                        CurriculumRepository curriculumRepository,
                        UserRepository userRepository,
                        UserRoleRepository userRoleRepository,
                        RoleRepository roleRepository,
                        ParentRepository parentRepository,
                        ParentStudentRepository parentStudentRepository,
                        StudentRepository studentRepository) {
        this.leadRepository = leadRepository;
        this.leadHistoryRepository = leadHistoryRepository;
        this.leadSourceRepository = leadSourceRepository;
        this.siteRepository = siteRepository;
        this.curriculumRepository = curriculumRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.parentRepository = parentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * Main Flow bước 1: ghi nhận lead mới. A1: từ chối nếu phone đã tồn tại
     * ở lead active khác (idx_leads_phone) — gợi ý cập nhật lead hiện có
     * thay vì tạo mới (không tạo bản ghi DUPLICATE riêng vì partial unique
     * index trên phone sẽ chặn ngay, xem Javadoc chi tiết trong lớp).
     */
    @Transactional
    public LeadResponse createLead(CreateLeadRequest request, Long actorUserId) {
        requireStaff(actorUserId);
        leadRepository.findByPhoneAndDeletedAtIsNull(request.phone()).ifPresent(existing -> {
            throw new DuplicateLeadPhoneException(
                    "Số điện thoại " + request.phone() + " đã tồn tại ở lead id=" + existing.getId()
                            + " (" + existing.getLeadCode() + "), trạng thái " + existing.getStatus()
                            + ". Vui lòng cập nhật lead hiện có thay vì tạo mới.");
        });

        LeadSource source = leadSourceRepository.findByCode(request.leadSourceCode())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nguồn lead code=" + request.leadSourceCode()));
        User actor = getUserOrThrow(actorUserId);

        Lead lead = new Lead();
        lead.setLeadCode(generateLeadCode());
        lead.setFullName(request.fullName());
        lead.setPhone(request.phone());
        lead.setEmail(request.email());
        if (request.contactRelationship() != null) {
            lead.setContactRelationship(Lead.ContactRelationship.valueOf(request.contactRelationship()));
        }
        lead.setStudentName(request.studentName());
        lead.setStudentDob(request.studentDob());
        lead.setStudentGrade(request.studentGrade());
        lead.setStudentCurrentSchool(request.studentCurrentSchool());
        lead.setLeadSource(source);
        if (request.interestedSiteId() != null) {
            lead.setInterestedSite(getSiteOrThrow(request.interestedSiteId()));
        }
        if (request.interestedCurriculumId() != null) {
            lead.setInterestedCurriculum(getCurriculumOrThrow(request.interestedCurriculumId()));
        }
        lead.setInitialMessage(request.initialMessage());
        lead.setStatus(Lead.Status.NEW);
        lead = leadRepository.save(lead);

        writeLeadHistory(lead, actor, LeadHistory.Action.CREATED);
        return toResponse(lead);
    }

    /** Main Flow bước 2: phân phối lead cho Nhân viên tư vấn phụ trách (Hệ thống/Quản lý điểm trường). */
    @Transactional
    public LeadResponse assignLead(Long leadId, AssignLeadRequest request, Long actorUserId) {
        requireRole(actorUserId, ASSIGN_ROLE_CODES);
        Lead lead = getLeadOrThrow(leadId);
        User assignee = getUserOrThrow(request.assignToUserId());
        User actor = getUserOrThrow(actorUserId);

        lead.setAssignedTo(assignee);
        lead.setAssignedAt(OffsetDateTime.now());
        lead = leadRepository.save(lead);

        writeLeadHistory(lead, actor, LeadHistory.Action.UPDATED);
        return toResponse(lead);
    }

    /**
     * Main Flow bước 3-5, A2: cập nhật trạng thái sau khi tư vấn.
     * NEW/CONTACTED → CONTACTED/QUALIFIED, hoặc → LOST (A2, kèm outcome/lý do)
     * bất kỳ lúc nào trước khi WON. Không cho chuyển trạng thái nếu lead đã
     * WON/LOST/DUPLICATE (trạng thái cuối).
     */
    @Transactional
    public LeadResponse updateStatus(Long leadId, UpdateLeadStatusRequest request, Long actorUserId) {
        requireStaff(actorUserId);
        Lead lead = getLeadOrThrow(leadId);
        if (isFinalStatus(lead.getStatus())) {
            throw new InvalidLeadStatusTransitionException(
                    "Lead id=" + leadId + " đang ở trạng thái cuối (" + lead.getStatus() + "), không thể cập nhật tiếp.");
        }
        Lead.Status newStatus = Lead.Status.valueOf(request.status());
        if (newStatus == Lead.Status.WON) {
            throw new InvalidLeadStatusTransitionException(
                    "Không thể chuyển lead sang WON qua API này — dùng convertToStudent (UC-34).");
        }
        if (newStatus == Lead.Status.LOST && request.outcome() == null) {
            throw new InvalidLeadStatusTransitionException("Chuyển lead sang LOST phải kèm outcome (lý do).");
        }
        User actor = getUserOrThrow(actorUserId);

        lead.setStatus(newStatus);
        if (request.outcome() != null) {
            lead.setOutcome(Lead.Outcome.valueOf(request.outcome()));
        }
        if (request.finalNote() != null) {
            lead.setFinalNote(request.finalNote());
        }
        lead = leadRepository.save(lead);

        writeLeadHistory(lead, actor, LeadHistory.Action.UPDATED);
        return toResponse(lead);
    }

    /**
     * UC-34 Main Flow: transaction tạo users+parents (nếu chưa có PH với
     * số phone này) + users+students + parent_student, cập nhật lead sang
     * WON. A2 (lỗi giữa chừng): @Transactional tự rollback toàn bộ, không
     * để lead WON thiếu hồ sơ học sinh tương ứng.
     */
    @Transactional
    public LeadResponse convertToStudent(Long leadId, Long actorUserId) {
        requireStaff(actorUserId);
        Lead lead = getLeadOrThrow(leadId);
        if (lead.getStatus() != Lead.Status.QUALIFIED) {
            throw new LeadNotQualifiedException(
                    "Lead id=" + leadId + " phải ở trạng thái QUALIFIED để chuyển đổi (hiện tại: " + lead.getStatus() + ").");
        }
        if (lead.getStudentName() == null || lead.getStudentName().isBlank() || lead.getStudentDob() == null) {
            throw new IncompleteLeadDataException(
                    "Lead id=" + leadId + " thiếu student_name hoặc student_dob — cập nhật đầy đủ thông tin học sinh trước khi chuyển đổi.");
        }
        User actor = getUserOrThrow(actorUserId);

        // 1. Tạo users (nếu chưa có PH với số phone này) + parents
        Parent parent = findOrCreateParent(lead, actor);

        // 2. Tạo users + students cho HS
        Student student = createStudentAccount(lead, actor);

        // 3. Tạo parent_student liên kết
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(lead.getContactRelationship() == null
                ? ParentStudent.Relationship.OTHER
                : ParentStudent.Relationship.valueOf(lead.getContactRelationship().name()));
        link.setPrimaryContact(true);
        link.setFinancialResponsible(true);
        parentStudentRepository.save(link);

        // 4. Cập nhật leads.converted_student_id, converted_at
        lead.setStatus(Lead.Status.WON);
        lead.setOutcome(Lead.Outcome.WON_ENROLLED);
        lead.setConvertedStudent(student);
        lead.setConvertedAt(OffsetDateTime.now());
        lead.setConvertedBy(actor);
        lead = leadRepository.save(lead);

        writeLeadHistory(lead, actor, LeadHistory.Action.UPDATED);
        return toResponse(lead);
    }

    @Transactional(readOnly = true)
    public LeadResponse getLead(Long id) {
        return toResponse(getLeadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> listMyLeads(Long actorUserId) {
        return leadRepository.findByAssignedToIdAndDeletedAtIsNull(actorUserId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> listOpenLeads() {
        return leadRepository.findByStatusInAndDeletedAtIsNull(
                        List.of(Lead.Status.NEW, Lead.Status.CONTACTED, Lead.Status.QUALIFIED))
                .stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    private Parent findOrCreateParent(Lead lead, User actor) {
        User parentUser = userRepository.findByPhone(lead.getPhone()).orElseGet(() -> {
            User newUser = new User();
            newUser.setUsername(generateUsername(lead.getPhone()));
            newUser.setEmail(lead.getEmail() != null && !lead.getEmail().isBlank()
                    ? lead.getEmail() : generatePlaceholderEmail(lead));
            newUser.setFullName(lead.getFullName());
            newUser.setPhone(lead.getPhone());
            newUser.setStatus(User.Status.ACTIVE);
            User saved = userRepository.save(newUser);
            assignRole(saved, "PARENT", actor);
            return saved;
        });

        return parentRepository.findByUserId(parentUser.getId()).orElseGet(() -> {
            Parent newParent = new Parent();
            newParent.setUser(parentUser);
            return parentRepository.save(newParent);
        });
    }

    private Student createStudentAccount(Lead lead, User actor) {
        User studentUser = new User();
        studentUser.setUsername(generateUsername(lead.getPhone()) + "-hs");
        studentUser.setEmail(generatePlaceholderEmail(lead).replace("@", "-hs@"));
        studentUser.setFullName(lead.getStudentName());
        studentUser.setStatus(User.Status.ACTIVE);
        studentUser = userRepository.save(studentUser);
        assignRole(studentUser, "STUDENT", actor);

        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode(generateStudentCode());
        student.setDateOfBirth(lead.getStudentDob());
        if (lead.getInterestedSite() != null) {
            student.setPrimarySite(lead.getInterestedSite());
        }
        student.setOriginalSchool(lead.getStudentCurrentSchool());
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
    }

    private void assignRole(User user, String roleCode, User actor) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(actor);
        userRoleRepository.save(userRole);
    }

    private boolean isFinalStatus(Lead.Status status) {
        return status == Lead.Status.WON || status == Lead.Status.LOST || status == Lead.Status.DUPLICATE;
    }

    private void requireStaff(Long actorUserId) {
        requireRole(actorUserId, STAFF_ROLE_CODES);
    }

    private void requireRole(Long actorUserId, Set<String> allowedRoleCodes) {
        Set<String> roleCodes = roleCodesOf(actorUserId);
        if (roleCodes.stream().noneMatch(allowedRoleCodes::contains)) {
            throw new NotAuthorizedForLeadManagementException(
                    "Tài khoản id=" + actorUserId + " không có role phù hợp (" + allowedRoleCodes + ") để thao tác lead.");
        }
    }

    private Set<String> roleCodesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet());
    }

    private String generateUsername(String phone) {
        String base = "lead" + phone.replaceAll("[^0-9]", "");
        String candidate = base;
        int suffix = 0;
        while (userRepository.findByUsername(candidate).isPresent()) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }

    /** Đã xác nhận với user: sinh email placeholder khi lead không cung cấp email (users.email NOT NULL). */
    private String generatePlaceholderEmail(Lead lead) {
        return "lead" + lead.getId() + "@placeholder.pps.edu.vn";
    }

    private String generateLeadCode() {
        String prefix = LEAD_CODE_PREFIX + Year.now().getValue() + "-" + String.format("%02d", LocalDate.now().getMonthValue()) + "-";
        long sequence = leadRepository.countByLeadCodeStartingWith(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    private String generateStudentCode() {
        String prefix = "HS" + Year.now().getValue() + "-";
        long sequence = studentRepository.countByStudentCodeStartingWith(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    private void writeLeadHistory(Lead lead, User actor, LeadHistory.Action action) {
        LeadHistory history = new LeadHistory();
        history.setLead(lead);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> details = new HashMap<>();
        details.put("status", lead.getStatus().name());
        details.put("finalNote", lead.getFinalNote());
        history.setDetails(details);
        leadHistoryRepository.save(history);
    }

    private Lead getLeadOrThrow(Long id) {
        return leadRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lead id=" + id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + id));
    }

    private Site getSiteOrThrow(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + id));
    }

    private Curriculum getCurriculumOrThrow(Long id) {
        return curriculumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + id));
    }

    private LeadResponse toResponse(Lead l) {
        return new LeadResponse(
                l.getId(), l.getLeadCode(), l.getFullName(), l.getPhone(), l.getEmail(),
                l.getContactRelationship() == null ? null : l.getContactRelationship().name(),
                l.getStudentName(), l.getStudentDob(), l.getStudentGrade(), l.getStudentCurrentSchool(),
                l.getLeadSource().getCode(), l.getInterestedSite() == null ? null : l.getInterestedSite().getId(),
                l.getInterestedCurriculum() == null ? null : l.getInterestedCurriculum().getId(),
                l.getInitialMessage(), l.getStatus().name(), l.getOutcome() == null ? null : l.getOutcome().name(),
                l.getFinalNote(), l.getAssignedTo() == null ? null : l.getAssignedTo().getId(), l.getAssignedAt(),
                l.getConvertedStudent() == null ? null : l.getConvertedStudent().getId(), l.getConvertedAt());
    }
}
