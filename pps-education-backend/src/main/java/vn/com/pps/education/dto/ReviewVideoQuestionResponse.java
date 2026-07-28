package vn.com.pps.education.dto;

public record ReviewVideoQuestionResponse(
        Long id,
        Long reviewVideoId,
        Integer timestampSeconds,
        String prompt,
        Integer maxRecordingSeconds,
        Integer maxAttempts,
        Integer displayOrder
) {}
