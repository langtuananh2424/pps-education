package vn.com.pps.education.lms.dto;

/**
 * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b: 1 dạng đề giáo viên được chọn cho
 * chương trình của bộ Video phản xạ.
 *
 * @param format                 SHORT / PART2 / PET4 / PICTURE.
 * @param label                  tên dạng đề theo bộ tiêu chí (VD "IELTS Speaking Part 2").
 * @param recommendedSeconds     thời gian ghi âm tối đa mà ngưỡng của rubric được hiệu chuẩn theo — FE điền sẵn khi
 *                               chọn dạng đề (giáo viên vẫn sửa được, lệch thì điểm các ô đếm có thể lệch).
 * @param requiresPictureBrief   true với dạng tả tranh: bắt buộc nhập mô tả tranh.
 */
public record ReflexQuestionFormatOptionResponse(String format, String label, int recommendedSeconds,
                                                 boolean requiresPictureBrief) {
}
