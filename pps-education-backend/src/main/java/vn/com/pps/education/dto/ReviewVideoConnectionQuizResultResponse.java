package vn.com.pps.education.dto;

import java.util.List;

/** Kết quả tự chấm ngay sau khi nộp quiz cho 1 lượt xem + tiến độ (viewCount/completed) mới nhất. */
public record ReviewVideoConnectionQuizResultResponse(
        List<ConnectionAnswerResult> results,
        ReviewVideoProgressResponse progress
) {}
