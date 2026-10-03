package vn.com.pps.education.permission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.com.pps.education.permission.domain.PermissionAuditLog;

public interface PermissionAuditLogRepository
        extends JpaRepository<PermissionAuditLog, Long>, JpaSpecificationExecutor<PermissionAuditLog> {

    /** UC-03 bổ sung — chặn xóa role đã từng được gán/thu hồi (UC-46), giữ đúng chứng cứ tra soát (FR-PER-04). */
    boolean existsByTargetRoleId(Long roleId);
}
