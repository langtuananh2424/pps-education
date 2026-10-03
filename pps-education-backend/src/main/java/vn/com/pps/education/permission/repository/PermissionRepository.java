package vn.com.pps.education.permission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.permission.domain.Permission;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    List<Permission> findByModule(String module);
}
