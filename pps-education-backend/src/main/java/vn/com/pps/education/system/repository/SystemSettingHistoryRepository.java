package vn.com.pps.education.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.system.domain.SystemSettingHistory;

import java.util.List;

public interface SystemSettingHistoryRepository extends JpaRepository<SystemSettingHistory, Long> {

    List<SystemSettingHistory> findBySystemSettingIdOrderByCreatedAtDesc(Long systemSettingId);
}
