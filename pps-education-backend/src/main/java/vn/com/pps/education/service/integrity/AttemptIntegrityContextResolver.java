package vn.com.pps.education.service.integrity;

import vn.com.pps.education.domain.AttemptIntegrityEvent.AttemptType;

/**
 * Open/Closed cho từng loại "bài làm" (mirror AttendanceMethod ở
 * service/attendance/ theo .claude/rules/solid.md) — xác định học sinh/
 * lớp/quyền ghi sự kiện khác nhau thật sự theo từng loại (khác repository,
 * khác cách suy ra lớp). Thêm 1 loại attempt mới (VD LISTENING_PRACTICE
 * khi có UI làm bài) = thêm 1 implementation mới, không sửa
 * AttemptIntegrityService.
 */
public interface AttemptIntegrityContextResolver {

    boolean supports(AttemptType type);

    /** Dùng khi HỌC SINH gửi sự kiện — ném lỗi nếu attemptId không tồn tại hoặc không thuộc actorUserId. */
    AttemptContext resolveForOwner(Long attemptId, Long actorUserId);

    /** Dùng khi GIÁO VIÊN xem tổng hợp — không kiểm tra chủ sở hữu, chỉ tra cứu. */
    AttemptContext resolveForReading(Long attemptId);
}
