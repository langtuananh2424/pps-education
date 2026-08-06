package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ReviewVideoAssignment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewVideoAssignmentRepository extends JpaRepository<ReviewVideoAssignment, Long> {
    List<ReviewVideoAssignment> findBySchoolClassIdAndStatus(Long classId, ReviewVideoAssignment.Status status);

    List<ReviewVideoAssignment> findByReviewVideoSetIdAndSchoolClassIdAndStatus(
            Long reviewVideoSetId, Long classId, ReviewVideoAssignment.Status status);

    Optional<ReviewVideoAssignment> findByUuid(UUID uuid);

    /** V82: quét job hết hạn — due_at NULL (không hạn nộp) tự động không khớp phép so sánh <=. */
    List<ReviewVideoAssignment> findByStatusAndDueAtLessThanEqualAndTeacherNotifiedAtIsNull(
            ReviewVideoAssignment.Status status, OffsetDateTime cutoff);

    /** V92 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): quét job nhắc Phụ huynh trước hạn nộp. */
    List<ReviewVideoAssignment> findByStatusAndDueAtBetweenAndParentReminderSentAtIsNull(
            ReviewVideoAssignment.Status status, OffsetDateTime from, OffsetDateTime to);
}
