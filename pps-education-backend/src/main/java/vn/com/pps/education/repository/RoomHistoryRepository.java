package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.RoomHistory;

public interface RoomHistoryRepository extends JpaRepository<RoomHistory, Long> {
}
