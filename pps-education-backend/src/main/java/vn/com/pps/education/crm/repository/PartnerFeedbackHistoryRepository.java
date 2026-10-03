package vn.com.pps.education.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.crm.domain.PartnerFeedbackHistory;

import java.util.List;

public interface PartnerFeedbackHistoryRepository extends JpaRepository<PartnerFeedbackHistory, Long> {

    List<PartnerFeedbackHistory> findByFeedbackIdOrderByCreatedAtAsc(Long feedbackId);
}
