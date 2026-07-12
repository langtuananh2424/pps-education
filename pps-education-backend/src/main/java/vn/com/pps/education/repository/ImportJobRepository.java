package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ImportJob;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
}
