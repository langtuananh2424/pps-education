package vn.com.pps.education.facility.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.facility.domain.RoomHistory;

public interface RoomHistoryRepository extends JpaRepository<RoomHistory, Long> {
}
