package vn.com.pps.education.dto;

public record EquipmentResponse(
        Long id,
        Long roomId,
        String code,
        String name,
        String equipmentType,
        String status,
        String notes
) {}
