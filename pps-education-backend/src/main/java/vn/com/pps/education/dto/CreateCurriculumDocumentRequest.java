package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-60: GV/Admin upload tài liệu tham khảo gắn theo curriculum.
 * curriculumId không đánh dấu @NotNull — client gọi qua
 * POST /api/curriculums/{curriculumId}/documents không cần truyền lại
 * trong body, Controller tự merge path variable vào trước khi gọi
 * Service (xem CurriculumDocumentController).
 */
public record CreateCurriculumDocumentRequest(
        Long curriculumId,
        @NotBlank String title,
        String description,
        @NotBlank String documentType,
        @NotBlank String fileUrl,
        Integer displayOrder
) {}
