package vn.com.pps.education.lms.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * V226 (Nghe ép) — kết quả 1 bài tập con của học sinh. CỐ TÌNH không có câu nào đúng/sai: học sinh chỉ biết câu
 * sai khi tapescript câu đó đã mở ({@code unlockedQuestionIds}). {@code attemptsUntilNextTapescript} NULL
 * khi bài đã đạt (không còn mở thêm).
 */
public record SectionResultResponse(
        Long sectionId,
        String title,
        int displayOrder,
        BigDecimal passThresholdPercent,
        boolean passed,
        BigDecimal latestScorePercent,
        BigDecimal bestScorePercent,
        int failedAttemptsCount,
        Integer attemptsUntilNextTapescript,
        List<Long> unlockedQuestionIds
) {}
