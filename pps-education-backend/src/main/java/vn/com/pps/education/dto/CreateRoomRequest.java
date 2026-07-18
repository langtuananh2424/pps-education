package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** UC-37 Main Flow bước 1-2: khai báo phòng học mới tại 1 điểm trường. */
public record CreateRoomRequest(
        @NotNull Long siteId,
        @NotBlank String code,
        String name,
        @NotBlank String roomType,
        @Positive int capacity,
        boolean flexible,
        boolean managedByCenter
) {}
