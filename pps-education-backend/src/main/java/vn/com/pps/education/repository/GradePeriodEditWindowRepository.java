package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradePeriodEditWindow;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface GradePeriodEditWindowRepository extends JpaRepository<GradePeriodEditWindow, Long> {

    /** Test-only lookup (mô phỏng hết hạn X ngày bằng cách đẩy lùi first_entered_at) + tiện tra cứu chung. */
    Optional<GradePeriodEditWindow> findBySchoolClassIdAndGradePeriodId(Long classId, Long gradePeriodId);

    boolean existsBySchoolClassIdAndGradePeriodId(Long classId, Long gradePeriodId);

    /** GradeSchedulerService: mọi (lớp, kỳ đánh giá) đã hết hạn X ngày kể từ lần đầu nhập — ứng viên tự động công bố. */
    List<GradePeriodEditWindow> findByFirstEnteredAtBefore(OffsetDateTime cutoff);
}
