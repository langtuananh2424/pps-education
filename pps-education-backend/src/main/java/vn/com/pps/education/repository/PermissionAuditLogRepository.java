package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.com.pps.education.domain.PermissionAuditLog;

public interface PermissionAuditLogRepository
        extends JpaRepository<PermissionAuditLog, Long>, JpaSpecificationExecutor<PermissionAuditLog> {
}
