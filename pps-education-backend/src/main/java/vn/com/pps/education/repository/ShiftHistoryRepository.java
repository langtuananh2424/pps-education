package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ShiftHistory;

public interface ShiftHistoryRepository extends JpaRepository<ShiftHistory, Long> {
}
