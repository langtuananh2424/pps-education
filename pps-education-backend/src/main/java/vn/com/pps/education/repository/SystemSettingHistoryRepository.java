package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.SystemSettingHistory;

import java.util.List;

public interface SystemSettingHistoryRepository extends JpaRepository<SystemSettingHistory, Long> {

    List<SystemSettingHistory> findBySystemSettingIdOrderByCreatedAtDesc(Long systemSettingId);
}
