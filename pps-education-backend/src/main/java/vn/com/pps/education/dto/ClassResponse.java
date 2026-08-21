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
        Long academicYearId,
        String academicYear,
        String status,
        /** Màu hiển thị trên lịch làm việc dạng lưới — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21. */
        String color
) {}
