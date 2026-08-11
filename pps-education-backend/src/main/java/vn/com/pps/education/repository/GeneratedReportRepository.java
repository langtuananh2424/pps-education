package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GeneratedReport;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, Long> {
}
