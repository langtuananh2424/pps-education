package vn.com.pps.education.dto;

/** "Bài học hôm nay" của 1 buổi học (bổ sung ngoài SDD gốc, đã xác nhận với người dùng). */
public record ClassSessionLessonContentResponse(Long classSessionId, String lessonContent) {
}
