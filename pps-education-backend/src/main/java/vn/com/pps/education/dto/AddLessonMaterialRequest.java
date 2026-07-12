package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-23 Main Flow bước 1-2: hệ thống đã upload lên CDN, GV gửi lại URL phân phối + metadata tệp. */
public record AddLessonMaterialRequest(
        @NotBlank String materialType,
        @NotBlank String title,
        @NotBlank String fileUrl,
        Long fileSizeBytes,
        Integer durationSeconds,
        Integer displayOrder,
        boolean isDownloadable
) {}
