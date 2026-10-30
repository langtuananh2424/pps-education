package vn.com.pps.education.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.system.domain.PpsScoringConfig;

public interface PpsScoringConfigRepository extends JpaRepository<PpsScoringConfig, Short> {
}
