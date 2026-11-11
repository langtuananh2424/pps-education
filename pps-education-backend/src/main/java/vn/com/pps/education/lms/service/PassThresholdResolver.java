package vn.com.pps.education.lms.service;

import vn.com.pps.education.lms.domain.ExerciseSection;
import vn.com.pps.education.student.domain.Student;

import java.math.BigDecimal;

/**
 * V226 (Nghe ép) — mức % cần đạt của 1 bài tập con đối với 1 học sinh. Tách thành interface để SAU NÀY thay bằng
 * mức theo level học sinh (suy từ điểm kiểm tra các kỳ) mà không sửa ForcedListeningService; hiện chỉ có
 * {@link DefaultPassThresholdResolver}.
 */
public interface PassThresholdResolver {
    BigDecimal thresholdFor(ExerciseSection section, Student student);
}
