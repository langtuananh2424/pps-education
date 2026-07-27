package vn.com.pps.education.dto;

public record ReviewVideoResponse(
        Long id,
        Long reviewVideoSetId,
        String sourceType,
        String title,
        String fileUrl,
        Long fileSizeBytes,
        Integer durationSeconds,
        Integer displayOrder
) {}
