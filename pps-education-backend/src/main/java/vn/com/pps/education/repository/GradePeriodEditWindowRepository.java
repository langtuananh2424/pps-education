package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradePeriodEditWindow;

import java.util.Optional;

public interface GradePeriodEditWindowRepository extends JpaRepository<GradePeriodEditWindow, Long> {

    /** Test-only lookup (mô phỏng đẩy lùi first_entered_at) + tiện tra cứu chung. */
    Optional<GradePeriodEditWindow> findBySchoolClassIdAndGradePeriodId(Long classId, Long gradePeriodId);

    boolean existsBySchoolClassIdAndGradePeriodId(Long classId, Long gradePeriodId);

    /** UC-19 (xoá kỳ đánh giá): chặn xoá kỳ đã bắt đầu nhập điểm (đã có cửa sổ chỉnh sửa ở bất kỳ lớp nào). */
    boolean existsByGradePeriodId(Long gradePeriodId);
}
