package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-06 Main Flow bước 2: đính kèm tệp tin — tệp coi như đã upload CDN từ trước (giống pattern LessonMaterial), chỉ nhận URL. */
public record AddTaskAttachmentRequest(
        @NotBlank String fileUrl,
        @NotBlank String fileName
) {}
