package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.EmployeeShift;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Long> {
    /**
     * V124 (2026-08-14): 1 nhân sự có thể có NHIỀU ca active song song (VD "T7
     * xen kẽ" = 2 ca weekParity ODD/EVEN cùng active) -- không còn Optional.
     */
    List<EmployeeShift> findByEmployeeIdAndEffectiveToIsNull(Long employeeId);

    List<EmployeeShift> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    /**
     * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: batch nhiều nhân viên cùng
     * lúc cho trang roster "Lịch làm việc" toàn công ty
     * (EmployeeScheduleService) -- employeeIds LUÔN non-empty (Service tự
     * chặn danh sách rỗng trước khi gọi).
     */
    @Query("""
            SELECT es FROM EmployeeShift es
            WHERE es.employee.id IN :employeeIds
            AND es.effectiveFrom <= :toDate
            AND (es.effectiveTo IS NULL OR es.effectiveTo >= :fromDate)
            """)
    List<EmployeeShift> findByEmployeeIdInAndDateRangeOverlap(@Param("employeeIds") List<Long> employeeIds,
                                                               @Param("fromDate") LocalDate fromDate,
                                                               @Param("toDate") LocalDate toDate);
}
