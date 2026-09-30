package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 1 dòng của trang "Lịch sử thay đổi dữ liệu" (V203 — bổ sung ngoài SDD gốc, xác nhận với người
 * dùng 2026-09-30). details là snapshot ghi tại thời điểm thay đổi; previousDetails là snapshot của
 * bản ghi liền trước cùng đối tượng (null nếu là bản ghi đầu tiên) để FE hiển thị "cũ → mới".
 * valueLabels: nhãn đọc được cho các giá trị là khoá tham chiếu, khoá dạng "tênTrường:giáTrị"
 * (VD "teacherUserId:12" → "Nguyễn Văn A").
 *
 * entityType: CLASS / CLASS_SESSION / CLASS_ENROLLMENT / CLASS_TEACHER / STUDENT / EMPLOYEE.
 * subjectName/subjectCode: học sinh (ghi danh, học sinh), giáo viên (phân công GV) hoặc nhân sự.
 */
public record ChangeHistoryItemResponse(
        String id,
        String entityType,
        Long entityId,
        String action,
        Long classId,
        String className,
        String classCode,
        Long studentId,
        String subjectName,
        String subjectCode,
        LocalDate sessionDate,
        Map<String, Object> details,
        Map<String, Object> previousDetails,
        Map<String, String> valueLabels,
        Long changedById,
        String changedByName,
        OffsetDateTime changedAt
) {}
