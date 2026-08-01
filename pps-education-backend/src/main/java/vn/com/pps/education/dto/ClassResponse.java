package vn.com.pps.education.dto;

import java.time.LocalDate;

public record ClassResponse(
        Long id,
        String classCode,
        String name,
        Long siteId,
        String siteName,
        Long curriculumId,
        String curriculumCode,
        String classType,
        String classCategory,
        int maxStudents,
        Integer minStudents,
        LocalDate startDate,
        LocalDate endDate,
        String academicYear,
        String status
) {}
