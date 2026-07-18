package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.SiteHistory;

public interface SiteHistoryRepository extends JpaRepository<SiteHistory, Long> {
}
