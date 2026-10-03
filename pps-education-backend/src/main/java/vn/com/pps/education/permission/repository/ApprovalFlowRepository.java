package vn.com.pps.education.permission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.permission.domain.ApprovalFlow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApprovalFlowRepository extends JpaRepository<ApprovalFlow, Long> {

    List<ApprovalFlow> findByEntityTypeAndEntityIdOrderBySubmittedAtDesc(ApprovalFlow.EntityType entityType, Long entityId);

    List<ApprovalFlow> findByEntityTypeAndStatusOrderBySubmittedAtAsc(ApprovalFlow.EntityType entityType, ApprovalFlow.Status status);

    /** UC-19/20: đề xuất duyệt điểm mới nhất (PENDING) đang mở của đúng bản ghi điểm này. */
    Optional<ApprovalFlow> findFirstByEntityTypeAndEntityIdAndStatusOrderBySubmittedAtDesc(
            ApprovalFlow.EntityType entityType, Long entityId, ApprovalFlow.Status status);

    /**
     * V207 — mọi lượt gửi duyệt (kể cả đã từ chối/đã duyệt) của nhiều bản ghi cùng lúc, kèm người gửi và
     * người duyệt (tránh N+1) — dựng dòng thời gian nộp/duyệt/gửi lại của báo cáo buổi học.
     */
    @Query("""
            SELECT f FROM ApprovalFlow f
            JOIN FETCH f.submittedBy
            LEFT JOIN FETCH f.approver
            WHERE f.entityType = :entityType AND f.entityId IN :entityIds
            """)
    List<ApprovalFlow> findWithActorsByEntityTypeAndEntityIdIn(@Param("entityType") ApprovalFlow.EntityType entityType,
                                                               @Param("entityIds") Collection<Long> entityIds);
}
