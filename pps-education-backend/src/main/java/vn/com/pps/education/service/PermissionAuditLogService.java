package vn.com.pps.education.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.PermissionAuditLog;
import vn.com.pps.education.dto.PermissionAuditLogResponse;
import vn.com.pps.education.dto.PermissionAuditLogSearchRequest;
import vn.com.pps.education.repository.PermissionAuditLogRepository;

/**
 * UC-05: Xem nhật ký thay đổi quyền (FR-PER-04). Read-only.
 * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow, A1 (không có kết quả = list rỗng, không phải lỗi).
 */
@Service
public class PermissionAuditLogService {

    private final PermissionAuditLogRepository permissionAuditLogRepository;

    public PermissionAuditLogService(PermissionAuditLogRepository permissionAuditLogRepository) {
        this.permissionAuditLogRepository = permissionAuditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<PermissionAuditLogResponse> search(PermissionAuditLogSearchRequest filter, Pageable pageable) {
        Specification<PermissionAuditLog> spec = buildSpecification(filter);
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return permissionAuditLogRepository.findAll(spec, sorted).map(this::toResponse);
    }

    private Specification<PermissionAuditLog> buildSpecification(PermissionAuditLogSearchRequest filter) {
        Specification<PermissionAuditLog> spec = Specification.where(null);
        if (filter.actorUserId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorUser").get("id"), filter.actorUserId()));
        }
        if (filter.targetUserId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetUser").get("id"), filter.targetUserId()));
        }
        if (filter.action() != null && !filter.action().isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"),
                    PermissionAuditLog.Action.valueOf(filter.action())));
        }
        if (filter.fromDate() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), filter.fromDate()));
        }
        if (filter.toDate() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), filter.toDate()));
        }
        return spec;
    }

    private PermissionAuditLogResponse toResponse(PermissionAuditLog log) {
        return new PermissionAuditLogResponse(
                log.getId(),
                log.getActorUser().getId(),
                log.getTargetUser().getId(),
                log.getAction().name(),
                log.getTargetRole() != null ? log.getTargetRole().getId() : null,
                log.getTargetPermission() != null ? log.getTargetPermission().getId() : null,
                log.getDetails(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }
}
