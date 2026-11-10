package vn.com.pps.education.lms.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * V226 (Nghe ép) — bảng điểm các bài tập con. {@code overallPercent} = trung bình cộng điểm cao nhất, chỉ có
 * khi mọi bài đã đạt. {@code canRevealAnswers}: mọi bài đã đạt + Bài cho xem đáp án + chưa xem; xem xong thì
 * {@code answersRevealed=true} và không làm lại được nữa.
 */
public record SectionResultsResponse(
        List<SectionResultResponse> sections,
        boolean allPassed,
        BigDecimal overallPercent,
        boolean canRevealAnswers,
        boolean answersRevealed
) {}
