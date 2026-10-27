package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** V223 — số giây học sinh vừa thực sự ở trên màn hình làm bài, xem ExerciseAttemptService#recordActiveTime. */
public record RecordActiveTimeRequest(
        @Min(1) @Max(600) int seconds
) {}
