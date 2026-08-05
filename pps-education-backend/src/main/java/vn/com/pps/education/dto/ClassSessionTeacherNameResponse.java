package vn.com.pps.education.dto;

/** "Tên giáo viên giảng dạy" của buổi học — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06, mirror ClassSessionLessonContentResponse. */
public record ClassSessionTeacherNameResponse(Long classSessionId, String actualTeacherName) {
}
