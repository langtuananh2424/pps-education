package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.LeaveRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByStatusAndCurrentApproverId(LeaveRequest.Status status, Long currentApproverId);

    List<LeaveRequest> findByStatusAndCurrentApproverIsNull(LeaveRequest.Status status);

    /** Danh sách đơn từ đã nộp của chính người dùng (self-service, để xem lại trạng thái đơn của mình). */
    List<LeaveRequest> findByEmployeeUserIdOrderBySubmittedAtDesc(Long userId);

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-11 — dùng ở
     * AttendanceMissingSchedulerService để loại nhân sự đang nghỉ phép ĐÃ
     * DUYỆT (nguyên ngày) khỏi danh sách quét MISSING, vì AttendanceService
     * hiện KHÔNG liên kết với leave_requests (leave đã duyệt không tạo
     * work_calendar override) — nếu không loại trừ sẽ báo MISSING sai cho
     * nhân sự nghỉ phép hợp lệ. Cố ý CHỈ nhận leaveType nghỉ nguyên ngày
     * (ANNUAL/SICK/UNPAID/PERSONAL) qua tham số fullDayTypes — LATE/
     * EARLY_LEAVE (xin đi trễ/về sớm) không được loại trừ vì nhân sự vẫn
     * phải chấm công phần buổi còn lại.
     */
    @Query("""
            SELECT DISTINCT lr.employee.id FROM LeaveRequest lr
            WHERE lr.status = vn.com.pps.education.domain.LeaveRequest.Status.APPROVED
            AND lr.employee.id IN :employeeIds
            AND lr.startDate <= :date AND lr.endDate >= :date
            AND lr.leaveType IN :fullDayTypes
            """)
    Set<Long> findEmployeeIdsOnApprovedFullDayLeave(@Param("employeeIds") List<Long> employeeIds,
                                                      @Param("date") LocalDate date,
                                                      @Param("fullDayTypes") List<LeaveRequest.LeaveType> fullDayTypes);
}
