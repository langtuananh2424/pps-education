package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.SiteManager;

import java.util.List;

public interface SiteManagerRepository extends JpaRepository<SiteManager, Long> {
    boolean existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(Long siteId, Long userId, SiteManager.RoleType roleType);
    List<SiteManager> findByUserIdAndRoleTypeAndAssignedToIsNull(Long userId, SiteManager.RoleType roleType);
    List<SiteManager> findBySiteIdAndRoleTypeAndAssignedToIsNull(Long siteId, SiteManager.RoleType roleType);

    /**
     * Số điện thoại (users.phone) của Quản lý điểm trường đang phụ trách
     * điểm trường chứa lớp {@code classId} — dùng làm Hotline trong email
     * gửi Phụ huynh. Trả về scalar (không trả entity) để dùng được ngoài
     * transaction ở kênh gửi email, không vướng lazy-loading. Người được
     * gán gần nhất đứng đầu.
     */
    @Query("""
            SELECT u.phone FROM SiteManager sm JOIN sm.user u, SchoolClass c
            WHERE c.id = :classId AND sm.site = c.site
              AND sm.roleType = vn.com.pps.education.domain.SiteManager.RoleType.SITE_MANAGER
              AND sm.assignedTo IS NULL
              AND u.phone IS NOT NULL AND u.phone <> ''
            ORDER BY sm.assignedFrom DESC
            """)
    List<String> findActiveSiteManagerPhonesByClassId(@Param("classId") Long classId);
}
