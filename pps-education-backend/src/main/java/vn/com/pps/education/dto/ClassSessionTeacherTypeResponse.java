package vn.com.pps.education.dto;

/** "Loại giáo viên" của 1 buổi học — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05, mirror ClassSessionLessonContentResponse. */
public record ClassSessionTeacherTypeResponse(Long classSessionId, String teacherType) {
}
