package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.SiteManager;

import java.util.List;

public interface SiteManagerRepository extends JpaRepository<SiteManager, Long> {
    boolean existsBySiteIdAndUserIdAndAssignedToIsNull(Long siteId, Long userId);
    List<SiteManager> findByUserIdAndAssignedToIsNull(Long userId);
}
