package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.LeaveRequest;
import vn.com.pps.education.domain.LeaveSubstitution;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveSubstitutionRepository extends JpaRepository<LeaveSubstitution, Long> {

    /** UC-11 A2: các lượt dạy thay đang mở của 1 đơn, để thu hồi ngay khi bị Từ chối. */
    List<LeaveSubstitution> findByLeaveRequestIdAndRevokedAtIsNull(Long leaveRequestId);

    Optional<LeaveSubstitution> findByClassSessionIdAndRevokedAtIsNull(Long classSessionId);

    /** Còn lượt dạy thay nào khác đang mở cho cùng dòng class_teachers không — quyết định có đóng assigned_to hay chưa. */
    boolean existsByClassTeacherIdAndRevokedAtIsNull(Long classTeacherId);

    /**
     * UC-11 Mở rộng: scheduled job tự thu hồi — đơn Đã duyệt, đã qua
     * end_date + 2 ngày, lượt dạy thay còn đang mở.
     */
    @Query("""
            SELECT ls FROM LeaveSubstitution ls
            WHERE ls.revokedAt IS NULL
            AND ls.leaveRequest.status = :status
            AND ls.leaveRequest.endDate <= :cutoffDate
            """)
    List<LeaveSubstitution> findDueForAutoRevoke(@Param("status") LeaveRequest.Status status,
                                                  @Param("cutoffDate") LocalDate cutoffDate);

    /** Trang lịch sử dạy thay. */
    List<LeaveSubstitution> findByOrderByCreatedAtDesc();
}
