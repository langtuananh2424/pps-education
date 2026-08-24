package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** UC-21 mở rộng (BTVN online — dán uuid làm phương án thay dropdown, V55). */
    Optional<ExerciseAssignment> findByUuid(UUID uuid);

    /** V82: quét job hết hạn — due_at NULL (bài tự luyện) tự động không khớp phép so sánh <=. */
    List<ExerciseAssignment> findByStatusAndDueAtLessThanEqualAndTeacherNotifiedAtIsNull(
            ExerciseAssignment.Status status, OffsetDateTime cutoff);

    /** V92 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): quét job nhắc Phụ huynh trước hạn nộp. */
    List<ExerciseAssignment> findByStatusAndDueAtBetweenAndParentReminderSentAtIsNull(
            ExerciseAssignment.Status status, OffsetDateTime from, OffsetDateTime to);

    /**
     * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — "cả nhóm" bản giao được tạo
     * cùng 1 đợt "giao cả Đề" (mọi Bài Published của {@code examId}, cùng lớp, cùng buổi Nhận xét nguồn
     * — xem {@code ExamService#deliverToClass}). KHÔNG cần cột nhóm riêng: (Đề, lớp, buổi nguồn) đã đủ
     * xác định 1 đợt giao duy nhất nhờ rào chống trùng có sẵn ở {@code ExerciseService#deliverToClass}
     * (mỗi buổi chỉ khóa đúng 1 lựa chọn/kênh — xem StudentCommentService#requireNoHomeworkConflict).
     * "Giao lẻ 1 Bài" (không qua giao cả Đề) tự nhiên thành nhóm cỡ 1 (chỉ khớp đúng Bài đó). Dùng ở
     * {@code ExerciseAttemptService#applyPassOutcome} để cộng dồn số câu đúng/tổng số câu TOÀN Đề.
     * sessionId truyền {@code null} khớp đúng bản giao KHÔNG có buổi nguồn (dữ liệu trước V123 hoặc
     * caller không truyền ClassSession).
     */
    @Query("SELECT a FROM ExerciseAssignment a WHERE a.exercise.exam.id = :examId AND a.schoolClass.id = :classId "
            + "AND ((:sessionId IS NULL AND a.sourceClassSession IS NULL) OR a.sourceClassSession.id = :sessionId) "
            + "AND a.status = :status")
    List<ExerciseAssignment> findDeliveryGroup(@Param("examId") Long examId, @Param("classId") Long classId,
                                                @Param("sessionId") Long sessionId, @Param("status") ExerciseAssignment.Status status);
}
