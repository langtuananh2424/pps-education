package vn.com.pps.education.dto;

import java.time.LocalDate;

public record SiteTeacherResponse(
        Long id,
        Long siteId,
        Long teacherUserId,
        String teacherFullName,
        LocalDate assignedFrom,
        LocalDate assignedTo,
        String notes
) {}
