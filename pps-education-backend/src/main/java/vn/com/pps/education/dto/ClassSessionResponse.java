package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ClassSessionResponse(
        Long id,
        Long classId,
        String className,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        /** Buổi Sáng/Chiều/Tối của các tiết bên dưới — bổ sung ngoài SDD gốc, 2026-08-20. */
        String dayPart,
        /** periodNumber (theo site_period_templates, trong phạm vi dayPart) buổi này chiếm, tăng dần — bổ sung ngoài SDD gốc, 2026-08-19. */
        List<Integer> periodNumbers,
        Long roomId,
        String roomName,
        Long primaryTeacherId,
        String primaryTeacherName,
        /** GV phụ của buổi (tuỳ chọn) — gán riêng theo buổi, bổ sung ngoài SDD gốc, 2026-08-19. */
        Long assistantTeacherId,
        String assistantTeacherName,
        /** CM (Class Manager) của buổi (tuỳ chọn) — gán riêng theo buổi, bổ sung ngoài SDD gốc, 2026-08-19. */
        Long cmTeacherId,
        String cmTeacherName,
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
        Long makeupForSessionId,
        /** Màu của lớp (SchoolClass.color) — tô thẻ buổi học trên lịch làm việc dạng lưới, bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21. */
        String classColor
) {}
