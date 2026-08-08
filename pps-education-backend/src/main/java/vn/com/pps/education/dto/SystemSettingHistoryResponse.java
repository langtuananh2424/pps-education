package vn.com.pps.education.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record SystemSettingHistoryResponse(
        Long id,
        JsonNode oldValue,
        JsonNode newValue,
        String changedByName,
        OffsetDateTime createdAt
) {}
