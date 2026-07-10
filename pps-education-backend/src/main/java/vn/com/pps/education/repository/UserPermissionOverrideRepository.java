package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.UserPermissionOverride;

import java.util.List;
import java.util.Optional;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, Long> {
    // Override còn hiệu lực: expires_at IS NULL hoặc expires_at > now() -- lọc ở tầng Service (UC-04 A1)
    List<UserPermissionOverride> findByUserId(Long userId);
    List<UserPermissionOverride> findByPermissionId(Long permissionId);
    Optional<UserPermissionOverride> findByUserIdAndPermissionId(Long userId, Long permissionId);
}
