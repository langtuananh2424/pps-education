package vn.com.pps.education.permission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.com.pps.education.permission.domain.UserRole;

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

    /** Rà soát bảo mật 2026-09-28 - MediaStorageService.storeUpload phân quyền upload theo role, không cần nạp entity. */
    @Query("select ur.role.code from UserRole ur where ur.user.id = :userId")
    List<String> findRoleCodesByUserId(Long userId);
}
