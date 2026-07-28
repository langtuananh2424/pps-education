package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradeAppealRequest;

import java.util.List;
import java.util.Optional;

public interface GradeAppealRequestRepository extends JpaRepository<GradeAppealRequest, Long> {

    /** UC-62 A — check đang có phúc khảo mở (PENDING/ACCEPTED) trên đúng bản ghi điểm này chưa. */
    Optional<GradeAppealRequest> findFirstByEntityTypeAndEntityIdAndStatusIn(
            GradeAppealRequest.EntityType entityType, Long entityId, List<GradeAppealRequest.Status> statuses);

    /** UC-62 Main Flow: GV xem hàng chờ tiếp nhận theo lớp phụ trách. */
    List<GradeAppealRequest> findBySchoolClass_IdInAndStatusOrderByCreatedAtAsc(List<Long> classIds, GradeAppealRequest.Status status);

    /** UC-62: Học sinh/Phụ huynh tự xem lịch sử phúc khảo đã gửi. */
    List<GradeAppealRequest> findByRequestedByIdOrderByCreatedAtDesc(Long requestedByUserId);

    /** Sửa điểm trong lúc APPEAL (GradeService#requireEditableState) — tìm đúng yêu cầu ACCEPTED đang mở. */
    Optional<GradeAppealRequest> findFirstByEntityTypeAndEntityIdAndStatus(
            GradeAppealRequest.EntityType entityType, Long entityId, GradeAppealRequest.Status status);
}
