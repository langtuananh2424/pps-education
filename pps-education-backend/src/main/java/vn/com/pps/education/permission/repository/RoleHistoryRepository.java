package vn.com.pps.education.permission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.permission.domain.RoleHistory;

public interface RoleHistoryRepository extends JpaRepository<RoleHistory, Long> {
}
