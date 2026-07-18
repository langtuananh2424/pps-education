package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.PartnerFeedback;

import java.util.List;

public interface PartnerFeedbackRepository extends JpaRepository<PartnerFeedback, Long> {

    List<PartnerFeedback> findBySubmittedByIdOrderByCreatedAtDesc(Long submittedById);

    // Sắp xếp theo mức độ ưu tiên (Main Flow bước 1) làm ở tầng Service — priority
    // lưu dạng EnumType.STRING nên ORDER BY SQL trên cột này sẽ sắp theo alphabet
    // (HIGH/LOW/NORMAL/URGENT), không đúng thứ tự mức độ nghiêm trọng thật.
    List<PartnerFeedback> findBySiteId(Long siteId);
}
