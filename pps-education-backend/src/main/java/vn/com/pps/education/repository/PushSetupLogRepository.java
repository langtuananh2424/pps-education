package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.PushSetupLog;

public interface PushSetupLogRepository extends JpaRepository<PushSetupLog, Long> {
}
