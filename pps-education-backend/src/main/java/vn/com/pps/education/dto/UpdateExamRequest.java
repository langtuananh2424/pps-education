package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** Kho đề — chỉ sửa được tiêu đề; khung chương trình bất biến sau khi tạo (mirror ReviewVideoSet không đổi scope). */
public record UpdateExamRequest(
        @NotBlank String title
) {}
