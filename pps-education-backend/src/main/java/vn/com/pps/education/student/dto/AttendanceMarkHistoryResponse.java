package vn.com.pps.education.student.dto;

import java.time.OffsetDateTime;

/** 1 dòng trong attendance_marks_history — mirror AttendanceMarkResponse, bổ sung Action/changedBy/createdAt cho timeline "Lịch sử thao tác" (FE bucket nhiều dòng gần nhau thành 1 đợt lưu/nộp, mirror SessionVersionHistoryModal.tsx bên Nhận xét học viên). */
public record AttendanceMarkHistoryResponse(
        Long id,
        Long studentId,
        String studentFullName,
        String studentCode,
        String status,
        String action,
        Long changedByUserId,
        String changedByName,
        OffsetDateTime createdAt
) {}
