package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** V148: thêm 1 Sách vào 1 khung chương trình (Kho đề — Curriculum (chương trình+khối) -&gt; Sách -&gt; Unit -&gt; Sub Topic -&gt; Lesson). */
public record CreateBookRequest(
        @NotBlank String title,
        Integer displayOrder
) {}
