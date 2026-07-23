package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Room;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findBySiteId(Long siteId);

    boolean existsBySiteIdAndCode(Long siteId, String code);

    /** UC-57: tra cứu phòng theo mã khi import Excel lịch học (mã phòng chỉ duy nhất trong phạm vi 1 điểm trường). */
    Optional<Room> findBySiteIdAndCode(Long siteId, String code);
}
