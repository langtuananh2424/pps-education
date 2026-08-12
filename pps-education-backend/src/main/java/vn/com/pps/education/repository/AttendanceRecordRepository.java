package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    // Bổ sung ngoài UC-09 gốc (xác nhận 2026-08-12) -- danh sách chấm công cho admin/HR xem tổng
    // hợp, xem AttendanceService.listRecords. JOIN FETCH employee+user+site để tránh N+1 khi map DTO.
    @Query("""
            select r from AttendanceRecord r
            join fetch r.employee e
            join fetch e.user u
            left join fetch r.site s
            where r.workDate between :from and :to
              and (:employeeId is null or e.id = :employeeId)
              and (:siteId is null or s.id = :siteId)
            order by r.workDate desc, u.fullName
            """)
    List<AttendanceRecord> search(@Param("employeeId") Long employeeId, @Param("siteId") Long siteId,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to);

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
