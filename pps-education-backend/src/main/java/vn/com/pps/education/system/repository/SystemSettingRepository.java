package vn.com.pps.education.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.system.domain.SystemSetting;

import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    Optional<SystemSetting> findBySettingKey(String settingKey);
}
