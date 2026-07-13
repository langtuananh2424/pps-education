package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-37 Main Flow bước 3: khai báo thiết bị dạy học, gắn với 1 phòng (roomId để trống = chưa gán phòng). */
public record CreateEquipmentRequest(
        Long roomId,
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String equipmentType
) {}
