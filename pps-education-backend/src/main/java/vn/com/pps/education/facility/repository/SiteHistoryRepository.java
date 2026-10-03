package vn.com.pps.education.facility.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.facility.domain.SiteHistory;

public interface SiteHistoryRepository extends JpaRepository<SiteHistory, Long> {
}
