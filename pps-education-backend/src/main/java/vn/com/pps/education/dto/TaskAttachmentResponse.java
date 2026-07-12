package vn.com.pps.education.dto;

public record TaskAttachmentResponse(
        Long id,
        Long taskId,
        String fileUrl,
        String fileName,
        Long uploadedBy
) {}
