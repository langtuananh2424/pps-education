package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);
    List<UserRole> findByUserIdIn(List<Long> userIds);
    List<UserRole> findByRoleId(Long roleId);
    Optional<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);
}
