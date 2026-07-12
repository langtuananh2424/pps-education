package vn.com.pps.education.dto;

public record LessonMaterialResponse(
        Long id,
        Long lessonId,
        String materialType,
        String title,
        String fileUrl,
        Long fileSizeBytes,
        Integer durationSeconds,
        Integer displayOrder,
        boolean isDownloadable
) {}
