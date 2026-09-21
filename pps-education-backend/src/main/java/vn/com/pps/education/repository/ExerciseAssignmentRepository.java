package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ExerciseAssignment;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExerciseAssignmentRepository extends JpaRepository<ExerciseAssignment, Long> {
    List<ExerciseAssignment> findBySchoolClassIdAndStatus(Long classId, ExerciseAssignment.Status status);

    /** FR-ACA-07: lấy cả ACTIVE lẫn COMPLETED — 1 assignment có thể đã COMPLETED (applyPassOutcome) dù còn học sinh chưa làm. */
    List<ExerciseAssignment> findBySchoolClassIdAndStatusIn(Long classId, Collection<ExerciseAssignment.Status> statuses);

    List<ExerciseAssignment> findByExerciseIdAndSchoolClassIdAndStatus(
            Long exerciseId, Long classId, ExerciseAssignment.Status status);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-19 — "gán nhanh" 1 Bài cho lớp từ Kho
     * đề (không qua UC-21), phân biệt với bản giao có {@code sourceClassSession} thật bằng điều kiện
     * NULL này, xem Javadoc {@code ExerciseService#quickAssignToClass}. */
    Optional<ExerciseAssignment> findByExerciseIdAndSchoolClassIdAndStatusAndSourceClassSessionIsNull(
            Long exerciseId, Long classId, ExerciseAssignment.Status status);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-19 — liệt kê các lớp đã "gán nhanh" 1 Bài. */
    List<ExerciseAssignment> findByExerciseIdAndStatusAndSourceClassSessionIsNull(
            Long exerciseId, ExerciseAssignment.Status status);

    /** UC-21 mở rộng (BTVN online — dán uuid làm phương án thay dropdown, V55). */
    Optional<ExerciseAssignment> findByUuid(UUID uuid);

    /** V82: quét job hết hạn — due_at NULL (bài tự luyện) tự động không khớp phép so sánh <=. */
    List<ExerciseAssignment> findByStatusAndDueAtLessThanEqualAndTeacherNotifiedAtIsNull(
            ExerciseAssignment.Status status, OffsetDateTime cutoff);

    /** V92 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): quét job nhắc Phụ huynh trước hạn nộp. */
    List<ExerciseAssignment> findByStatusAndDueAtBetweenAndParentReminderSentAtIsNull(
            ExerciseAssignment.Status status, OffsetDateTime from, OffsetDateTime to);

    /** V150: toàn bộ bản giao (1/Bài) thuộc 1 lô BTVN theo kỹ năng — dùng cho startBatchSession/submitBatchSession/cancelBatch. */
    List<ExerciseAssignment> findByHomeworkBatchId(Long homeworkBatchId);
}
