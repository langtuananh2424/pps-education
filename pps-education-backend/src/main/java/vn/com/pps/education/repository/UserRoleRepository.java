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

    /** PositionRoleSyncService — role hệ thống từng tự gán theo 1 chức vụ cụ thể (FR-HRM-06). */
    List<UserRole> findByUserIdAndGrantedViaPositionId(Long userId, Long grantedViaPositionId);

    /** UC-10 bước 3 — tra cứu toàn bộ tài khoản mang 1 role (VD "TEACHER") để chọn giáo viên dạy thay. */
    List<UserRole> findByRole_Code(String roleCode);
}
