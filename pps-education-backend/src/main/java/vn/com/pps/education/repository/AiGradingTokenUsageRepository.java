package vn.com.pps.education.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.AiGradingTokenUsage;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * V192 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — truy vấn chi phí token AI cho
 * trang Quản trị hệ thống → Sử dụng token AI.
 *
 * Các truy vấn gộp trả về projection interface (không phải Entity) vì trang chỉ cần con số đã cộng sẵn —
 * kéo hết Entity về rồi cộng trong Java sẽ nạp thừa hàng chục nghìn dòng mỗi lần mở trang.
 */
public interface AiGradingTokenUsageRepository extends JpaRepository<AiGradingTokenUsage, Long> {

    /** 1 dòng cho mỗi học sinh trong khoảng ngày — dùng cho bảng "theo học sinh". */
    interface StudentUsageRow {
        Long getStudentId();
        String getStudentName();
        long getCallCount();
        long getPromptTokens();
        long getCachedTokens();
        long getCompletionTokens();
        long getReasoningTokens();
    }

    /** 1 dòng cho mỗi bước chấm — dùng cho bảng "theo bước chấm". */
    interface StepUsageRow {
        String getStep();
        long getCallCount();
        long getPromptTokens();
        long getCachedTokens();
        long getCompletionTokens();
        long getReasoningTokens();
        double getAvgElapsedMs();
    }

    /** 1 dòng cho mỗi bài (lần giao video) — dùng cho bảng "theo bài tập". */
    interface AssignmentUsageRow {
        Long getAssignmentId();
        String getAssignmentName();
        long getCallCount();
        long getPromptTokens();
        long getCachedTokens();
        long getCompletionTokens();
        long getReasoningTokens();
    }

    @Query("""
            SELECT u.student.id AS studentId,
                   CONCAT(u.student.user.fullName, '') AS studentName,
                   COUNT(u) AS callCount,
                   COALESCE(SUM(u.promptTokens), 0) AS promptTokens,
                   COALESCE(SUM(u.cachedTokens), 0) AS cachedTokens,
                   COALESCE(SUM(u.completionTokens), 0) AS completionTokens,
                   COALESCE(SUM(u.reasoningTokens), 0) AS reasoningTokens
            FROM AiGradingTokenUsage u
            WHERE u.createdAt >= :from AND u.createdAt < :to AND u.student IS NOT NULL
            GROUP BY u.student.id, u.student.user.fullName
            ORDER BY COALESCE(SUM(u.promptTokens), 0) + COALESCE(SUM(u.completionTokens), 0) DESC
            """)
    List<StudentUsageRow> aggregateByStudent(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("""
            SELECT CONCAT(u.step, '') AS step,
                   COUNT(u) AS callCount,
                   COALESCE(SUM(u.promptTokens), 0) AS promptTokens,
                   COALESCE(SUM(u.cachedTokens), 0) AS cachedTokens,
                   COALESCE(SUM(u.completionTokens), 0) AS completionTokens,
                   COALESCE(SUM(u.reasoningTokens), 0) AS reasoningTokens,
                   COALESCE(AVG(u.elapsedMs), 0) AS avgElapsedMs
            FROM AiGradingTokenUsage u
            WHERE u.createdAt >= :from AND u.createdAt < :to
            GROUP BY u.step
            ORDER BY COALESCE(SUM(u.promptTokens), 0) + COALESCE(SUM(u.completionTokens), 0) DESC
            """)
    List<StepUsageRow> aggregateByStep(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("""
            SELECT u.reviewVideoAssignment.id AS assignmentId,
                   CONCAT(u.reviewVideoAssignment.reviewVideoSet.title, '') AS assignmentName,
                   COUNT(u) AS callCount,
                   COALESCE(SUM(u.promptTokens), 0) AS promptTokens,
                   COALESCE(SUM(u.cachedTokens), 0) AS cachedTokens,
                   COALESCE(SUM(u.completionTokens), 0) AS completionTokens,
                   COALESCE(SUM(u.reasoningTokens), 0) AS reasoningTokens
            FROM AiGradingTokenUsage u
            WHERE u.createdAt >= :from AND u.createdAt < :to AND u.reviewVideoAssignment IS NOT NULL
            GROUP BY u.reviewVideoAssignment.id, u.reviewVideoAssignment.reviewVideoSet.title
            ORDER BY COALESCE(SUM(u.promptTokens), 0) + COALESCE(SUM(u.completionTokens), 0) DESC
            """)
    List<AssignmentUsageRow> aggregateByAssignment(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("""
            SELECT u FROM AiGradingTokenUsage u
            WHERE u.createdAt >= :from AND u.createdAt < :to
            ORDER BY u.createdAt DESC
            """)
    Page<AiGradingTokenUsage> findRecent(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, Pageable pageable);

    /** Số lượt bị loại kết quả nhưng VẪN tốn tiền — trang hiển thị riêng, không giấu trong tổng. */
    @Query("""
            SELECT COUNT(u) FROM AiGradingTokenUsage u
            WHERE u.createdAt >= :from AND u.createdAt < :to AND u.accepted = false
            """)
    long countRejected(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
