package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record ClassSessionResponse(
        Long id,
        Long classId,
        String className,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        Long roomId,
        String roomName,
        Long primaryTeacherId,
        String primaryTeacherName,
        String sessionType,
        String status,
        String cancellationReason,
        Long rescheduledToSessionId,
        String lessonContent,
        /** Loại giáo viên (VIETNAMESE/FOREIGN) dạy buổi này — null nếu chưa xác định. */
        String teacherType,
        /** Tên GV thực tế dạy buổi (nhập tay, khác primaryTeacherName là FK hệ thống) — bổ sung ngoài SDD gốc, 2026-08-06. */
        String actualTeacherName,
        /** Số thứ tự buổi học trong lớp (1-based, đếm cả CANCELLED) — tính động, không lưu DB. */
        Integer sessionNumber,
        /** Chỉ có ý nghĩa khi sessionType=MAKEUP — id buổi CANCELLED mà buổi này bù cho, null nếu chưa liên kết. */
        Long makeupForSessionId
) {}
