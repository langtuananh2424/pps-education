package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** V144/V148: thêm 1 Unit vào 1 Sách (Kho đề — Curriculum -&gt; Sách -&gt; Unit -&gt; Sub Topic -&gt; Lesson). */
public record CreateUnitRequest(
        @NotBlank String title,
        Integer displayOrder
) {}
