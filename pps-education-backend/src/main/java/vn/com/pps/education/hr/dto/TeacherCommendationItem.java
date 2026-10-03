package vn.com.pps.education.hr.dto;

import java.time.LocalDate;

/** Khen thưởng/kỷ luật trong Hồ sơ giáo viên (V203) — không kèm số tiền thưởng/phạt. */
public record TeacherCommendationItem(
        Long id,
        String recordType,
        LocalDate recordDate,
        String title
) {}
