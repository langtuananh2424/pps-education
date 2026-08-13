package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): đổi giáo viên chính
 * (PRIMARY) đang phụ trách 1 lớp, gộp kết thúc phân công cũ + gán phân
 * công mới trong 1 transaction, kèm cascade cập nhật giáo viên phụ trách
 * các buổi học SCHEDULED tương lai cùng loại giáo viên. Không cho đổi
 * teacherType/subjectId qua thao tác này — đổi loại giáo viên là gán mới
 * (assignTeacher) 1 PRIMARY loại kia, không phải "đổi giáo viên chính".
 */
public record ChangeTeacherRequest(
        @NotNull Long newTeacherUserId,
        @NotNull LocalDate effectiveDate
) {}
