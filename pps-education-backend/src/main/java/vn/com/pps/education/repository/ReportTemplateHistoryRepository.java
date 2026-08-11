package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReportTemplateHistory;

public interface ReportTemplateHistoryRepository extends JpaRepository<ReportTemplateHistory, Long> {
}
