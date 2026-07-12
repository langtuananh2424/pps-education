package vn.com.pps.education.dto;

public record RoomResponse(
        Long id,
        Long siteId,
        String code,
        String name,
        String roomType,
        int capacity,
        boolean flexible,
        boolean managedByCenter,
        String status,
        String notes
) {}
