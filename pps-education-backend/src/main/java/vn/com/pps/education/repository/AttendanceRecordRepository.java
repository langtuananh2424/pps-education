package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.AttendanceRecord;

import java.time.LocalDate;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    // gps_location không map qua JPA (xem AttendanceRecord.java) -- ghi qua native query.
    @Modifying
    @Query(value = """
            UPDATE attendance_records
            SET gps_location = ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            WHERE id = :id
            """, nativeQuery = true)
    void updateGpsLocation(@Param("id") Long id, @Param("latitude") double latitude,
                            @Param("longitude") double longitude);
}
