package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ApprovalFlow;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumHistory;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.CurriculumSubjectHistory;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateCurriculumSubjectRequest;
import vn.com.pps.education.dto.CreateCustomCurriculumRequest;
import vn.com.pps.education.dto.CurriculumApprovalResponse;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.CurriculumSubjectResponse;
import vn.com.pps.education.dto.DecideCurriculumApprovalRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateCustomCurriculumRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.CurriculumNotEditableException;
import vn.com.pps.education.exception.CurriculumUpdateConfirmationRequiredException;
import vn.com.pps.education.exception.DuplicateCurriculumCodeException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ApprovalFlowRepository;
import vn.com.pps.education.repository.CurriculumHistoryRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectHistoryRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.SkillRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-16: Quản lý khung chương trình (FR-ACA-01) + UC-16b: Đề xuất khung
 * chương trình tùy biến + UC-17: Phê duyệt khung chương trình tùy biến.
 * Xem docs/uc/phan-he-06-hoc-thuat.md.
 *
 * Gộp cả 3 UC vào 1 Service — cùng 1 entity (curriculums) + 1 luồng trạng
 * thái DRAFT→PENDING_APPROVAL→ACTIVE (hoặc quay lại DRAFT nếu bị từ chối),
 * chỉ khác actor theo từng bước (HEAD_ACADEMIC tạo khung chuẩn + duyệt,
 * SITE_MANAGER đề xuất tùy biến) — giống cách LeaveRequestService gộp
 * UC-10 (nộp) + UC-11 (duyệt) vì "2 UC cùng 1 workflow trạng thái".
 *
 * UC-17 dùng lại bảng approval_flows dùng chung (entity_type=CURRICULUM)
 * thay vì tự thêm cột riêng vào curriculums — đúng theo SDD (ERD-Nhom5A:
 * "approval_flows đã thiết kế ở Nhóm 1, không tạo bảng riêng"). Khi bị
 * REJECTED, curriculum quay về DRAFT (không có giá trị "REJECTED" riêng
 * trong curriculums.status — lý do từ chối lưu ở approval_flows.comment).
 *
 * HEAD_ACADEMIC (create/update/addSubject/listPendingApprovals/decideApproval)
 * qua @PreAuthorize("hasPermission(null,'academic.curriculum.manage')") ở
 * CurriculumController (Hybrid PBAC — V28). SITE_MANAGER (createCustomCopy/
 * updateCustomCopy/submitForApproval) vẫn giữ requireSiteManagerForSite —
 * đây là row-level scope check (site cụ thể), không phải role-hardcode nên
 * hasPermission(null,...) không thay thế được (tham số object luôn null).
 */
@Service
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final CurriculumHistoryRepository curriculumHistoryRepository;
    private final CurriculumSubjectHistoryRepository curriculumSubjectHistoryRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SiteRepository siteRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final ApprovalFlowRepository approvalFlowRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;

    public CurriculumService(CurriculumRepository curriculumRepository,
                              CurriculumSubjectRepository curriculumSubjectRepository,
                              CurriculumHistoryRepository curriculumHistoryRepository,
                              CurriculumSubjectHistoryRepository curriculumSubjectHistoryRepository,
                              SchoolClassRepository schoolClassRepository,
                              SiteRepository siteRepository,
                              SiteManagerRepository siteManagerRepository,
                              ApprovalFlowRepository approvalFlowRepository,
                              SkillRepository skillRepository,
                              UserRepository userRepository) {
        this.curriculumRepository = curriculumRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.curriculumHistoryRepository = curriculumHistoryRepository;
        this.curriculumSubjectHistoryRepository = curriculumSubjectHistoryRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.siteRepository = siteRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.approvalFlowRepository = approvalFlowRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CurriculumResponse> listStandard() {
        return curriculumRepository.findByDeletedAtIsNullAndSiteIdIsNull().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CurriculumResponse getById(Long id) {
        return toResponse(getCurriculumOrThrow(id));
    }

    /** Main Flow bước 1-3: khởi tạo khung chương trình chuẩn mới (status DRAFT). */
    @Transactional
    public CurriculumResponse create(CreateCurriculumRequest request, Long actorUserId) {
        if (curriculumRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateCurriculumCodeException("Mã khung chương trình đã tồn tại: " + request.code());
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        Curriculum curriculum = new Curriculum();
        curriculum.setCode(request.code());
        curriculum.setName(request.name());
        curriculum.setClassCategory(Curriculum.ClassCategory.valueOf(request.classCategory()));
        curriculum.setLevel(request.level());
        curriculum.setTotalPeriods(request.totalPeriods());
        if (request.defaultGradePassThreshold() != null) {
            curriculum.setDefaultGradePassThreshold(request.defaultGradePassThreshold());
        }
        curriculum.setCreatedBy(actor);
        curriculum = curriculumRepository.save(curriculum);

        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.CREATED);
        return toResponse(curriculum);
    }

    /** Main Flow bước 2, A1: cập nhật khung chương trình đã có. */
    @Transactional
    public CurriculumResponse update(Long id, UpdateCurriculumRequest request, Long actorUserId) {
        Curriculum curriculum = getCurriculumOrThrow(id);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        // A1 -- khung đang dùng bởi lớp IN_PROGRESS, cần xác nhận lại trước khi lưu.
        boolean inUseByRunningClass = schoolClassRepository
                .countByCurriculumIdAndStatus(id, SchoolClass.Status.IN_PROGRESS) > 0;
        if (inUseByRunningClass && !request.confirm()) {
            throw new CurriculumUpdateConfirmationRequiredException(
                    "Khung chương trình id=" + id + " đang được lớp IN_PROGRESS sử dụng. "
                            + "Xác nhận lại (confirm=true) để tiếp tục lưu.");
        }

        curriculum.setName(request.name());
        curriculum.setLevel(request.level());
        curriculum.setTotalPeriods(request.totalPeriods());
        if (request.defaultGradePassThreshold() != null) {
            curriculum.setDefaultGradePassThreshold(request.defaultGradePassThreshold());
        }
        Curriculum.Status newStatus = Curriculum.Status.valueOf(request.status());
        curriculum.setStatus(newStatus);
        if (newStatus == Curriculum.Status.ACTIVE && curriculum.getApprovedAt() == null) {
            curriculum.setApprovedBy(actor);
            curriculum.setApprovedAt(java.time.OffsetDateTime.now());
        }
        curriculum = curriculumRepository.save(curriculum);

        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.UPDATED);
        return toResponse(curriculum);
    }

    /** Main Flow bước 2: thêm 1 học phần vào khung chương trình. */
    @Transactional
    public CurriculumSubjectResponse addSubject(Long curriculumId, CreateCurriculumSubjectRequest request, Long actorUserId) {
        Curriculum curriculum = getCurriculumOrThrow(curriculumId);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        CurriculumSubject subject = new CurriculumSubject();
        subject.setCurriculum(curriculum);
        subject.setSubjectCode(CurriculumSubject.SubjectCode.valueOf(request.subjectCode()));
        if (request.skillId() != null) {
            subject.setSkill(skillRepository.findById(request.skillId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỹ năng id=" + request.skillId())));
        }
        subject.setName(request.name());
        subject.setPeriodCount(request.periodCount());
        subject.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        subject = curriculumSubjectRepository.save(subject);

        CurriculumSubjectHistory history = new CurriculumSubjectHistory();
        history.setCurriculumSubject(subject);
        history.setChangedBy(actor);
        history.setAction(CurriculumSubjectHistory.Action.CREATED);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("subjectCode", subject.getSubjectCode().name());
        snapshot.put("name", subject.getName());
        snapshot.put("periodCount", subject.getPeriodCount());
        history.setDetails(snapshot);
        curriculumSubjectHistoryRepository.save(history);

        return toResponse(subject);
    }

    @Transactional(readOnly = true)
    public List<CurriculumSubjectResponse> listSubjects(Long curriculumId) {
        getCurriculumOrThrow(curriculumId);
        return curriculumSubjectRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** UC-16b Main Flow bước 1-2: tạo bản sao tùy biến gắn với 1 điểm trường. */
    @Transactional
    public CurriculumResponse createCustomCopy(CreateCustomCurriculumRequest request, Long actorUserId) {
        Site site = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + request.siteId()));
        requireSiteManagerForSite(site.getId(), actorUserId);
        Curriculum parent = getCurriculumOrThrow(request.parentCurriculumId());
        if (parent.getSite() != null) {
            throw new CurriculumNotEditableException(
                    "Khung chương trình id=" + parent.getId() + " không phải khung chuẩn (đã là bản tùy biến), không thể làm gốc.");
        }
        if (curriculumRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateCurriculumCodeException("Mã khung chương trình đã tồn tại: " + request.code());
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        // Main Flow bước 2 -- tạo bản sao, sao chép nội dung từ bản gốc.
        Curriculum copy = new Curriculum();
        copy.setCode(request.code());
        copy.setName(request.name() == null || request.name().isBlank() ? parent.getName() : request.name());
        copy.setSite(site);
        copy.setParentCurriculum(parent);
        copy.setClassCategory(parent.getClassCategory());
        copy.setLevel(parent.getLevel());
        copy.setTotalPeriods(parent.getTotalPeriods());
        copy.setDefaultGradePassThreshold(parent.getDefaultGradePassThreshold());
        copy.setCreatedBy(actor);
        copy = curriculumRepository.save(copy);

        writeCurriculumHistory(copy, actor, CurriculumHistory.Action.CREATED);
        return toResponse(copy);
    }

    /** Main Flow bước 3, A1 (lưu nháp): chỉnh sửa nội dung bản tùy biến — chỉ khi đang DRAFT. */
    @Transactional
    public CurriculumResponse updateCustomCopy(Long id, UpdateCustomCurriculumRequest request, Long actorUserId) {
        Curriculum curriculum = getCurriculumOrThrow(id);
        requireCustomCopy(curriculum);
        requireSiteManagerForSite(curriculum.getSite().getId(), actorUserId);
        if (curriculum.getStatus() != Curriculum.Status.DRAFT) {
            throw new CurriculumNotEditableException(
                    "Khung chương trình tùy biến id=" + id + " đang ở trạng thái " + curriculum.getStatus()
                            + " — chỉ chỉnh sửa được khi DRAFT.");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        curriculum.setName(request.name());
        curriculum.setLevel(request.level());
        curriculum.setTotalPeriods(request.totalPeriods());
        if (request.defaultGradePassThreshold() != null) {
            curriculum.setDefaultGradePassThreshold(request.defaultGradePassThreshold());
        }
        curriculum = curriculumRepository.save(curriculum);

        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.UPDATED);
        return toResponse(curriculum);
    }

    /**
     * Main Flow bước 4-5: gửi đề xuất tùy biến để Trưởng phòng đào tạo phê
     * duyệt. Cũng dùng lại cho A1 của UC-17 (đề xuất lại sau khi bị từ chối
     * — curriculum đã quay về DRAFT nên gọi lại y hệt bước này).
     */
    @Transactional
    public CurriculumApprovalResponse submitForApproval(Long id, Long actorUserId) {
        Curriculum curriculum = getCurriculumOrThrow(id);
        requireCustomCopy(curriculum);
        requireSiteManagerForSite(curriculum.getSite().getId(), actorUserId);
        if (curriculum.getStatus() != Curriculum.Status.DRAFT) {
            throw new CurriculumNotEditableException(
                    "Khung chương trình tùy biến id=" + id + " đang ở trạng thái " + curriculum.getStatus()
                            + " — chỉ submit được khi DRAFT.");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        curriculum.setStatus(Curriculum.Status.PENDING_APPROVAL);
        curriculum = curriculumRepository.save(curriculum);
        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.UPDATED);

        ApprovalFlow flow = new ApprovalFlow();
        flow.setEntityType(ApprovalFlow.EntityType.CURRICULUM);
        flow.setEntityId(curriculum.getId());
        flow.setStatus(ApprovalFlow.Status.PENDING);
        flow.setSubmittedBy(actor);
        flow = approvalFlowRepository.save(flow);

        return toResponse(flow, curriculum);
    }

    /** UC-17 Main Flow bước 1: danh sách đề xuất tùy biến đang Chờ duyệt. */
    @Transactional(readOnly = true)
    public List<CurriculumApprovalResponse> listPendingApprovals(Long actorUserId) {
        return approvalFlowRepository
                .findByEntityTypeAndStatusOrderBySubmittedAtAsc(ApprovalFlow.EntityType.CURRICULUM, ApprovalFlow.Status.PENDING)
                .stream()
                .map(flow -> toResponse(flow, getCurriculumOrThrow(flow.getEntityId())))
                .toList();
    }

    /**
     * Main Flow bước 3-5: Trưởng phòng đào tạo Phê duyệt (-> ACTIVE, có hiệu
     * lực) hoặc Từ chối kèm lý do (-> quay về DRAFT để Quản lý điểm trường
     * sửa và đề xuất lại — A1).
     */
    @Transactional
    public CurriculumApprovalResponse decideApproval(Long approvalFlowId, DecideCurriculumApprovalRequest request, Long actorUserId) {
        ApprovalFlow flow = approvalFlowRepository.findById(approvalFlowId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề xuất id=" + approvalFlowId));
        if (flow.getEntityType() != ApprovalFlow.EntityType.CURRICULUM) {
            throw new ResourceNotFoundException("Không tìm thấy đề xuất khung chương trình id=" + approvalFlowId);
        }
        if (flow.getStatus() != ApprovalFlow.Status.PENDING) {
            throw new ApprovalAlreadyDecidedException("Đề xuất id=" + approvalFlowId + " đã được quyết định (" + flow.getStatus() + ").");
        }
        Curriculum curriculum = getCurriculumOrThrow(flow.getEntityId());
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        ApprovalFlow.Decision decision = ApprovalFlow.Decision.valueOf(request.decision());

        flow.setDecision(decision);
        flow.setApprover(actor);
        flow.setComment(request.comment());
        flow.setDecidedAt(OffsetDateTime.now());

        if (decision == ApprovalFlow.Decision.APPROVED) {
            flow.setStatus(ApprovalFlow.Status.APPROVED);
            curriculum.setStatus(Curriculum.Status.ACTIVE);
            curriculum.setApprovedBy(actor);
            curriculum.setApprovedAt(OffsetDateTime.now());
        } else {
            if (request.comment() == null || request.comment().isBlank()) {
                throw new IllegalArgumentException("Từ chối đề xuất bắt buộc phải kèm lý do (comment).");
            }
            flow.setStatus(ApprovalFlow.Status.REJECTED);
            // Không có trạng thái REJECTED riêng trong curriculums.status (SDD) --
            // quay về DRAFT để Quản lý điểm trường sửa và đề xuất lại (A1).
            curriculum.setStatus(Curriculum.Status.DRAFT);
        }
        flow = approvalFlowRepository.save(flow);
        curriculum = curriculumRepository.save(curriculum);
        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.UPDATED);

        return toResponse(flow, curriculum);
    }

    private void requireCustomCopy(Curriculum curriculum) {
        if (curriculum.getSite() == null) {
            throw new CurriculumNotEditableException(
                    "Khung chương trình id=" + curriculum.getId() + " là khung chuẩn, không phải bản tùy biến.");
        }
    }

    private void requireSiteManagerForSite(Long siteId, Long actorUserId) {
        if (!siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                siteId, actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            throw new NotSiteManagerForSiteException(
                    "Tài khoản id=" + actorUserId + " không được gán phụ trách điểm trường id=" + siteId + ".");
        }
    }


    private void writeCurriculumHistory(Curriculum curriculum, User actor, CurriculumHistory.Action action) {
        CurriculumHistory history = new CurriculumHistory();
        history.setCurriculum(curriculum);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(curriculumSnapshot(curriculum));
        curriculumHistoryRepository.save(history);
    }

    private CurriculumApprovalResponse toResponse(ApprovalFlow flow, Curriculum curriculum) {
        return new CurriculumApprovalResponse(
                flow.getId(), curriculum.getId(), curriculum.getCode(), curriculum.getName(),
                flow.getStatus().name(), flow.getSubmittedBy().getId(), flow.getSubmittedAt(),
                flow.getApprover() == null ? null : flow.getApprover().getId(),
                flow.getDecision() == null ? null : flow.getDecision().name(),
                flow.getComment(), flow.getDecidedAt());
    }

    private Map<String, Object> curriculumSnapshot(Curriculum c) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", c.getCode());
        snapshot.put("name", c.getName());
        snapshot.put("classCategory", c.getClassCategory().name());
        snapshot.put("level", c.getLevel());
        snapshot.put("totalPeriods", c.getTotalPeriods());
        snapshot.put("status", c.getStatus().name());
        return snapshot;
    }

    private Curriculum getCurriculumOrThrow(Long id) {
        return curriculumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + id));
    }

    private CurriculumResponse toResponse(Curriculum c) {
        return new CurriculumResponse(
                c.getId(), c.getCode(), c.getName(),
                c.getSite() == null ? null : c.getSite().getId(),
                c.getParentCurriculum() == null ? null : c.getParentCurriculum().getId(),
                c.getClassCategory().name(), c.getLevel(), c.getTotalPeriods(),
                c.getDefaultGradePassThreshold(), c.getStatus().name(),
                c.getCreatedBy().getId(), c.getApprovedBy() == null ? null : c.getApprovedBy().getId());
    }

    private CurriculumSubjectResponse toResponse(CurriculumSubject s) {
        return new CurriculumSubjectResponse(
                s.getId(), s.getCurriculum().getId(), s.getSubjectCode().name(),
                s.getSkill() == null ? null : s.getSkill().getId(), s.getName(),
                s.getPeriodCount(), s.getDisplayOrder());
    }
}
