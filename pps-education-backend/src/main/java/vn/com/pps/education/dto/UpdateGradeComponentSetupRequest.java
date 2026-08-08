package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** scaleType không sửa được sau khi tạo (V97) — component đã tạo theo maxScore của thang cũ, đổi thang sẽ phá tính nhất quán. */
public record UpdateGradeComponentSetupRequest(
        @NotNull LocalDate rosterAsOfDate,
        boolean commentRequired
) {}
