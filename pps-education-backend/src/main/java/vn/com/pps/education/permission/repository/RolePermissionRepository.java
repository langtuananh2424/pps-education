package vn.com.pps.education.permission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.permission.domain.RolePermission;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRoleId(Long roleId);
    List<RolePermission> findByPermissionId(Long permissionId);
}
