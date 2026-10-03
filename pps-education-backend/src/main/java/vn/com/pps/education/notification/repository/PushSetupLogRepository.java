package vn.com.pps.education.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.notification.domain.PushSetupLog;

public interface PushSetupLogRepository extends JpaRepository<PushSetupLog, Long> {
}
