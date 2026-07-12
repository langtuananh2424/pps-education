package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ApprovalFlow;

import java.util.List;

public interface ApprovalFlowRepository extends JpaRepository<ApprovalFlow, Long> {

    List<ApprovalFlow> findByEntityTypeAndEntityIdOrderBySubmittedAtDesc(ApprovalFlow.EntityType entityType, Long entityId);

    List<ApprovalFlow> findByEntityTypeAndStatusOrderBySubmittedAtAsc(ApprovalFlow.EntityType entityType, ApprovalFlow.Status status);
}
