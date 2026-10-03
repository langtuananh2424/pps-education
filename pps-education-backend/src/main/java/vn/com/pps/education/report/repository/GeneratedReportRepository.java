package vn.com.pps.education.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.report.domain.GeneratedReport;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, Long> {
}
