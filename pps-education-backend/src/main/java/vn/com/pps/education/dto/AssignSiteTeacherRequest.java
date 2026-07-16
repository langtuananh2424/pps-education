package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Gán giáo viên (nhân viên) vào điểm trường — 1 giáo viên có thể gán nhiều điểm trường. */
public record AssignSiteTeacherRequest(
        @NotNull Long teacherUserId,
        @NotNull LocalDate assignedFrom,
        String notes
) {}
