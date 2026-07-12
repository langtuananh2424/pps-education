package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.SiteManager;

import java.util.List;

public interface SiteManagerRepository extends JpaRepository<SiteManager, Long> {
    boolean existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(Long siteId, Long userId, SiteManager.RoleType roleType);
    List<SiteManager> findByUserIdAndRoleTypeAndAssignedToIsNull(Long userId, SiteManager.RoleType roleType);
    List<SiteManager> findBySiteIdAndRoleTypeAndAssignedToIsNull(Long siteId, SiteManager.RoleType roleType);
}
