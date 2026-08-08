package vn.com.pps.education.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record SystemSettingUpdateRequest(
        @NotNull JsonNode settingValue
) {}
