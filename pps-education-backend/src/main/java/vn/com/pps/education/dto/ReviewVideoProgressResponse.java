package vn.com.pps.education.dto;

public record ReviewVideoProgressResponse(
        Long reviewVideoId,
        Integer watchedSeconds,
        Integer durationSeconds,
        Integer watchedPercent,
        boolean completed
) {}
