package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradePeriodEditWindow;

import java.util.Optional;

public interface GradePeriodEditWindowRepository extends JpaRepository<GradePeriodEditWindow, Long> {

    /** Test-only lookup (mô phỏng đẩy lùi first_entered_at) + tiện tra cứu chung. */
    Optional<GradePeriodEditWindow> findBySchoolClassIdAndGradeComponentSetupId(Long classId, Long gradeComponentSetupId);

    boolean existsBySchoolClassIdAndGradeComponentSetupId(Long classId, Long gradeComponentSetupId);

    /** UC-19 (xoá setup): chặn xoá setup đã bắt đầu nhập điểm (đã có cửa sổ chỉnh sửa ở bất kỳ lớp nào). */
    boolean existsByGradeComponentSetupId(Long gradeComponentSetupId);
}
