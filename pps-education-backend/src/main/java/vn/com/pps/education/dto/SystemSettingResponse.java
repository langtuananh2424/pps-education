package vn.com.pps.education.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record SystemSettingResponse(
        Long id,
        String settingKey,
        JsonNode settingValue,
        String category,
        String description,
        String updatedByName,
        OffsetDateTime updatedAt
) {}
