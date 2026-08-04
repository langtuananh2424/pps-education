package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ApprovalFlow;

import java.util.List;
import java.util.Optional;

public interface ApprovalFlowRepository extends JpaRepository<ApprovalFlow, Long> {

    List<ApprovalFlow> findByEntityTypeAndEntityIdOrderBySubmittedAtDesc(ApprovalFlow.EntityType entityType, Long entityId);

    List<ApprovalFlow> findByEntityTypeAndStatusOrderBySubmittedAtAsc(ApprovalFlow.EntityType entityType, ApprovalFlow.Status status);

    /** UC-19/20: đề xuất duyệt điểm mới nhất (PENDING) đang mở của đúng bản ghi điểm này. */
    Optional<ApprovalFlow> findFirstByEntityTypeAndEntityIdAndStatusOrderBySubmittedAtDesc(
            ApprovalFlow.EntityType entityType, Long entityId, ApprovalFlow.Status status);
}
