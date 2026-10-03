package vn.com.pps.education.hr.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Thêm nhiều nhân sự vào 1 phòng ban cùng lúc (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-01). */
public record AddDepartmentMembersRequest(
        @NotEmpty List<Long> employeeIds
) {}
