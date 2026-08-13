package vn.com.pps.education.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * UC-18 (bổ sung ngoài SDD gốc, xác nhận 2026-08-13): 1 dòng lịch sử thay
 * đổi giáo viên phụ trách lớp. `details` giữ nguyên snapshot JSONB đã ghi
 * ở ClassService — 2 dạng tuỳ action: CREATED = {teacherUserId,
 * teacherRole, teacherType}, UPDATED = {assignedTo}.
 */
public record ClassTeacherHistoryResponse(
        Long id,
        Long classTeacherId,
        Long teacherUserId,
        String teacherFullName,
        String teacherRole,
        String action,
        Long changedByUserId,
        String changedByName,
        Map<String, Object> details,
        OffsetDateTime createdAt
) {}
