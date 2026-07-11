package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.ClassSession;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findBySchoolClassIdOrderBySessionDateAsc(Long classId);

    /**
     * FR-FAC-03 — kiểm tra trùng phòng: cùng room_id, cùng ngày, khoảng
     * thời gian giao nhau, status không phải CANCELLED/RESCHEDULED, loại
     * trừ chính session đang sửa (editingSessionId=null khi tạo mới).
     * Chỉ gọi khi room.isFlexible()=false (Service tự lọc trước).
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.room.id = :roomId
            AND cs.sessionDate = :date
            AND cs.status NOT IN (:excludedStatuses)
            AND :startTime < cs.endTime AND :endTime > cs.startTime
            AND (:editingSessionId IS NULL OR cs.id <> :editingSessionId)
            """)
    List<ClassSession> findOverlappingInRoom(@Param("roomId") Long roomId, @Param("date") LocalDate date,
                                              @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
                                              @Param("editingSessionId") Long editingSessionId,
                                              @Param("excludedStatuses") List<ClassSession.Status> excludedStatuses);
}
