package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.PartnerContract;
import vn.com.pps.education.domain.PartnerContractHistory;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreatePartnerContractRequest;
import vn.com.pps.education.dto.ExpiringPartnerContractResponse;
import vn.com.pps.education.dto.PartnerContractResponse;
import vn.com.pps.education.dto.UpdatePartnerContractRequest;
import vn.com.pps.education.exception.ActivePartnerContractAlreadyExistsException;
import vn.com.pps.education.exception.DuplicatePartnerContractNumberException;
import vn.com.pps.education.exception.PartnerContractNotDeletableException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.PartnerContractHistoryRepository;
import vn.com.pps.education.repository.PartnerContractRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-36b: Quản lý hợp đồng liên kết trường (FR-FAC-01). Xem
 * docs/uc/phan-he-10-co-so-vat-chat.md. Gate dưới quyền finance... không —
 * dưới quyền {@code facility.manage} giống UC-36 (cùng actor Quản lý vận
 * hành, cùng Precondition "Điểm trường liên quan đã được khởi tạo").
 *
 * A1 (cảnh báo sắp hết hạn) là truy vấn theo yêu cầu (withinDays do người
 * gọi chỉ định), không phải push-notification/cron — đúng pattern đã
 * dùng cho UC-08 A2 (EmployeeService.listExpiringContracts), không có
 * ngưỡng cố định nào được UC quy định.
 *
 * A3 (bổ sung — xóa hợp đồng nhập nhầm): UC-36b không mô tả bằng chữ
 * nhưng SDD ghi rõ deleted_at "Có soft-delete" và unique partial index
 * idx_partner_contracts_active có điều kiện {@code deleted_at IS NULL} —
 * bằng chứng kỹ thuật rõ ràng cho cơ chế này, khác terminate (chấm dứt
 * hợp lệ, giữ nguyên trong partner_contracts_history).
 */
@Service
public class PartnerContractService {

    private static final String CONTRACT_NUMBER_PREFIX = "HD-";

    private final PartnerContractRepository partnerContractRepository;
    private final PartnerContractHistoryRepository partnerContractHistoryRepository;
    private final SiteRepository siteRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public PartnerContractService(PartnerContractRepository partnerContractRepository,
                                   PartnerContractHistoryRepository partnerContractHistoryRepository,
                                   SiteRepository siteRepository,
                                   SiteManagerRepository siteManagerRepository,
                                   SchoolClassRepository schoolClassRepository,
                                   UserRepository userRepository,
                                   NotificationService notificationService) {
        this.partnerContractRepository = partnerContractRepository;
        this.partnerContractHistoryRepository = partnerContractHistoryRepository;
        this.siteRepository = siteRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** Main Flow bước 1-3: khởi tạo hợp đồng hợp tác mới. */
    @Transactional
    public PartnerContractResponse createContract(CreatePartnerContractRequest request, Long actorUserId) {
        Site site = getSiteOrThrow(request.siteId());
        User actor = getUserOrThrow(actorUserId);
        PartnerContract parent = request.parentContractId() == null ? null
                : partnerContractRepository.findByIdAndDeletedAtIsNull(request.parentContractId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng gốc id=" + request.parentContractId()));

        PartnerContract contract = new PartnerContract();
        contract.setSite(site);
        contract.setContractNumber(generateContractNumber());
        contract.setContractType(PartnerContract.ContractType.valueOf(request.contractType()));
        contract.setParentContract(parent);
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setTermsSummary(request.termsSummary());
        contract.setFileUrl(request.fileUrl());
        contract.setSignedAt(request.signedAt());
        contract.setSignedByCenter(request.signedByCenter());
        contract.setSignedByPartner(request.signedByPartner());
        contract.setRevenueShareNotes(request.revenueShareNotes());
        contract.setCreatedBy(actor);
        contract = partnerContractRepository.save(contract);

        writeHistory(contract, actor, PartnerContractHistory.Action.CREATED);
        return toResponse(contract);
    }

    /** Main Flow bước 2-3: cập nhật thời hạn/điều khoản/trạng thái hợp đồng. */
    @Transactional
    public PartnerContractResponse updateContract(Long contractId, UpdatePartnerContractRequest request, Long actorUserId) {
        PartnerContract contract = getContractOrThrow(contractId);
        User actor = getUserOrThrow(actorUserId);
        PartnerContract.Status newStatus = PartnerContract.Status.valueOf(request.status());

        if (newStatus == PartnerContract.Status.ACTIVE && contract.getStatus() != PartnerContract.Status.ACTIVE) {
            Long siteId = contract.getSite().getId();
            Long currentContractId = contract.getId();
            partnerContractRepository.findBySiteIdAndStatusAndDeletedAtIsNull(siteId, PartnerContract.Status.ACTIVE)
                    .filter(existing -> !existing.getId().equals(currentContractId))
                    .ifPresent(existing -> {
                        throw new ActivePartnerContractAlreadyExistsException(
                                "Điểm trường id=" + siteId + " đã có hợp đồng ACTIVE khác (id=" + existing.getId() + ").");
                    });
        }

        contract.setEndDate(request.endDate());
        contract.setStatus(newStatus);
        contract.setTermsSummary(request.termsSummary());
        contract.setFileUrl(request.fileUrl());
        contract.setSignedAt(request.signedAt());
        contract.setSignedByCenter(request.signedByCenter());
        contract.setSignedByPartner(request.signedByPartner());
        contract.setRevenueShareNotes(request.revenueShareNotes());
        contract = partnerContractRepository.save(contract);

        writeHistory(contract, actor, PartnerContractHistory.Action.UPDATED);
        return toResponse(contract);
    }

    /**
     * A2: chấm dứt hợp đồng — cảnh báo các lớp đang mở tại điểm trường đó
     * cho Quản lý điểm trường phụ trách để xử lý chuyển tiếp. Dùng
     * NotificationType.SYSTEM_ANNOUNCEMENT (enum đóng, không có type riêng
     * cho "chấm dứt hợp đồng").
     */
    @Transactional
    public PartnerContractResponse terminateContract(Long contractId, Long actorUserId) {
        PartnerContract contract = getContractOrThrow(contractId);
        User actor = getUserOrThrow(actorUserId);

        contract.setStatus(PartnerContract.Status.TERMINATED);
        contract = partnerContractRepository.save(contract);
        writeHistory(contract, actor, PartnerContractHistory.Action.UPDATED);

        long activeClassCount = schoolClassRepository.findBySiteIdAndDeletedAtIsNull(contract.getSite().getId()).stream()
                .filter(c -> c.getStatus() != SchoolClass.Status.COMPLETED && c.getStatus() != SchoolClass.Status.CANCELLED)
                .count();
        if (activeClassCount > 0) {
            List<SiteManager> managers = siteManagerRepository.findBySiteIdAndRoleTypeAndAssignedToIsNull(
                    contract.getSite().getId(), SiteManager.RoleType.SITE_MANAGER);
            for (SiteManager manager : managers) {
                notificationService.notify(manager.getUser().getId(), Notification.NotificationType.SYSTEM_ANNOUNCEMENT,
                        "Hợp đồng liên kết trường đã chấm dứt",
                        "Hợp đồng " + contract.getContractNumber() + " của điểm trường " + contract.getSite().getName()
                                + " đã chấm dứt — còn " + activeClassCount + " lớp đang hoạt động cần xử lý chuyển tiếp.");
            }
        }
        return toResponse(contract);
    }

    /**
     * A3: xóa mềm 1 hợp đồng nhập nhầm — chỉ áp dụng khi đang DRAFT (chưa
     * từng có hiệu lực pháp lý). Hợp đồng đã ACTIVE/EXPIRED/TERMINATED
     * dùng terminateContract thay thế để giữ đúng
     * partner_contracts_history (chứng cứ pháp lý, bắt buộc truy vết —
     * SDD). Khớp unique partial index idx_partner_contracts_active
     * (WHERE status='ACTIVE' AND deleted_at IS NULL) đã thiết kế sẵn cho
     * cơ chế soft-delete này.
     */
    @Transactional
    public void deleteContract(Long contractId, Long actorUserId) {
        PartnerContract contract = getContractOrThrow(contractId);
        if (contract.getStatus() != PartnerContract.Status.DRAFT) {
            throw new PartnerContractNotDeletableException(
                    "Chỉ xóa được hợp đồng đang DRAFT; hợp đồng id=" + contractId + " đang " + contract.getStatus()
                            + " — dùng chức năng chấm dứt (terminate) thay thế.");
        }
        User actor = getUserOrThrow(actorUserId);

        contract.setDeletedAt(OffsetDateTime.now());
        contract = partnerContractRepository.save(contract);
        writeHistory(contract, actor, PartnerContractHistory.Action.UPDATED);
    }

    /** A1: hợp đồng ACTIVE sắp/đã hết hạn trong vòng withinDays ngày kể từ hôm nay. */
    @Transactional(readOnly = true)
    public List<ExpiringPartnerContractResponse> listExpiringContracts(int withinDays) {
        LocalDate threshold = LocalDate.now().plusDays(withinDays);
        return partnerContractRepository
                .findByDeletedAtIsNullAndStatusAndEndDateLessThanEqualOrderByEndDateAsc(PartnerContract.Status.ACTIVE, threshold)
                .stream()
                .map(c -> new ExpiringPartnerContractResponse(c.getId(), c.getSite().getId(), c.getSite().getName(),
                        c.getContractNumber(), c.getEndDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerContractResponse> listBySite(Long siteId) {
        return partnerContractRepository.findBySiteIdAndDeletedAtIsNullOrderByStartDateDesc(siteId).stream()
                .map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    private String generateContractNumber() {
        String prefix = CONTRACT_NUMBER_PREFIX + Year.now().getValue() + "-";
        long sequence = partnerContractRepository.countByContractNumberStartingWith(prefix) + 1;
        String candidate = prefix + String.format("%04d", sequence);
        if (partnerContractRepository.existsByContractNumber(candidate)) {
            throw new DuplicatePartnerContractNumberException("Số hợp đồng đã tồn tại: " + candidate);
        }
        return candidate;
    }

    private void writeHistory(PartnerContract contract, User actor, PartnerContractHistory.Action action) {
        PartnerContractHistory history = new PartnerContractHistory();
        history.setContract(contract);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> details = new HashMap<>();
        details.put("status", contract.getStatus().name());
        details.put("endDate", contract.getEndDate().toString());
        if (contract.getDeletedAt() != null) {
            details.put("deletedAt", contract.getDeletedAt().toString());
        }
        history.setDetails(details);
        partnerContractHistoryRepository.save(history);
    }

    private PartnerContract getContractOrThrow(Long id) {
        return partnerContractRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng id=" + id));
    }

    private Site getSiteOrThrow(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private PartnerContractResponse toResponse(PartnerContract c) {
        return new PartnerContractResponse(
                c.getId(), c.getSite().getId(), c.getContractNumber(), c.getContractType().name(),
                c.getParentContract() == null ? null : c.getParentContract().getId(),
                c.getStartDate(), c.getEndDate(), c.getStatus().name(), c.getTermsSummary(), c.getFileUrl(),
                c.getSignedAt(), c.getSignedByCenter(), c.getSignedByPartner(), c.getRevenueShareNotes());
    }
}
