package vn.com.pps.education.lms.dto;

public record ReviewVideoProgressResponse(
        Long reviewVideoId,
        Integer watchedSeconds,
        Integer durationSeconds,
        Integer watchedPercent,
        boolean completed,
        Integer viewCount,
        Integer requiredViewCount
) {}
