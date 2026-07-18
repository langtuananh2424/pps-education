package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.RoleHistory;

public interface RoleHistoryRepository extends JpaRepository<RoleHistory, Long> {
}
