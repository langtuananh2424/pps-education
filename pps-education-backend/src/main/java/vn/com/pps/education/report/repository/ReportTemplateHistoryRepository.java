package vn.com.pps.education.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.report.domain.ReportTemplateHistory;

public interface ReportTemplateHistoryRepository extends JpaRepository<ReportTemplateHistory, Long> {
}
