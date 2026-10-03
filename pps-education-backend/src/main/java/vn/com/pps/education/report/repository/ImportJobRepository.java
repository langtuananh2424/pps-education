package vn.com.pps.education.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.report.domain.ImportJob;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
}
