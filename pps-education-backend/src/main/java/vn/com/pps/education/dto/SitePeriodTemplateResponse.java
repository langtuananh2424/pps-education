package vn.com.pps.education.dto;

import java.time.LocalTime;

public record SitePeriodTemplateResponse(
        Long id,
        Long siteId,
        String dayPart,
        int periodNumber,
        String label,
        LocalTime startTime,
        LocalTime endTime
) {}
