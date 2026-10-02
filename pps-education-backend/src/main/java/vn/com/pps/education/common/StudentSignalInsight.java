package vn.com.pps.education.common;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * UC-74 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) — tín hiệu ngoài lời giáo viên cho trợ lý
 * nhận xét: điểm danh buổi này, chuyên cần nhiều buổi, độ tuổi. Thuần tính toán,
 * trả LỜI không kèm chữ số.
 *
 * <p>Quy tắc đã chốt:</p>
 * <ul>
 *   <li>Buổi này: Đi muộn / Về sớm từ {@link #LATE_MIN_MINUTES} phút → nhắc nhẹ. Không có số phút (ô không bắt buộc
 *       khi điểm danh) → KHÔNG nhắc, vì không biết có vượt ngưỡng không.</li>
 *   <li>Chuyên cần {@link #ATTENDANCE_WINDOW} buổi gần nhất (tính cả buổi này, mọi Loại giáo viên): đủ
 *       {@link #ATTENDANCE_WINDOW} buổi đều Có mặt → khen; Vắng không phép ≥ 2 hoặc Đi muộn ≥ 3 → nhắc nhẹ. Vắng có
 *       phép không tính.</li>
 *   <li>Độ tuổi: dưới {@link #YOUNG_AGE} tuổi → chỉ chỉnh giọng văn (đơn giản, ấm áp), KHÔNG ghi tuổi.</li>
 * </ul>
 */
public final class StudentSignalInsight {

    static final int LATE_MIN_MINUTES = 10;
    public static final int ATTENDANCE_WINDOW = 8;
    static final int ABSENT_REMIND_COUNT = 2;
    static final int LATE_REMIND_COUNT = 3;
    static final int YOUNG_AGE = 10;

    /**
     * Nhận xét nói tới độ tuổi lấy từ hệ thống — dữ liệu có thể chưa chính xác nên dòng đó gắn cảnh báo cần xác
     * thực. (2026-10-02, giáo viên test: bỏ hẳn việc nhắc "mới vào lớp" — không còn tín hiệu studentInfo.)
     */
    private static final Pattern STUDENT_INFO_MENTION = Pattern.compile(
            "(?iu)(?<!\\p{L})tuổi(?!\\p{L})");

    private StudentSignalInsight() {
    }

    /** @param status tên {@code AttendanceMark.Status} của buổi này (PRESENT/LATE/EARLY_LEAVE/...), có thể null. */
    public static Optional<String> today(String status, Integer minutesLate, Integer minutesEarlyLeave) {
        if ("LATE".equals(status) && minutesLate != null && minutesLate >= LATE_MIN_MINUTES) {
            return Optional.of("hôm nay đến lớp muộn (nhắc nhẹ đến lớp đúng giờ)");
        }
        if ("EARLY_LEAVE".equals(status) && minutesEarlyLeave != null && minutesEarlyLeave >= LATE_MIN_MINUTES) {
            return Optional.of("hôm nay về sớm (nhắc nhẹ, không suy đoán lý do)");
        }
        return Optional.empty();
    }

    /**
     * @param statuses trạng thái điểm danh các buổi gần nhất, MỚI NHẤT TRƯỚC (tính cả buổi này), chỉ các buổi đã điểm
     *                 danh học sinh này; tối đa {@link #ATTENDANCE_WINDOW} phần tử được xét.
     */
    public static Optional<String> attendanceHistory(List<String> statuses) {
        List<String> window = statuses.stream().limit(ATTENDANCE_WINDOW).toList();
        long absent = window.stream().filter("ABSENT"::equals).count();
        long late = window.stream().filter("LATE"::equals).count();
        if (absent >= ABSENT_REMIND_COUNT) {
            return Optional.of("nghỉ học không phép nhiều buổi gần đây (nhắc nhẹ đi học đều, không trách móc)");
        }
        if (late >= LATE_REMIND_COUNT) {
            return Optional.of("hay đến lớp muộn trong các buổi gần đây (nhắc nhẹ đến lớp đúng giờ)");
        }
        if (window.size() == ATTENDANCE_WINDOW && window.stream().allMatch("PRESENT"::equals)) {
            return Optional.of("đi học đầy đủ, đúng giờ nhiều buổi liên tiếp (khen sự chuyên cần)");
        }
        return Optional.empty();
    }

    /** Gợi ý giọng văn theo độ tuổi — không phải thông tin được viết vào nhận xét. */
    public static Optional<String> ageTone(LocalDate dateOfBirth, LocalDate sessionDate) {
        if (dateOfBirth == null || sessionDate == null || dateOfBirth.isAfter(sessionDate)) {
            return Optional.empty();
        }
        return Period.between(dateOfBirth, sessionDate).getYears() < YOUNG_AGE
                ? Optional.of("học sinh nhỏ tuổi: câu chữ đơn giản, ấm áp, khích lệ nhiều hơn") : Optional.empty();
    }

    public static boolean mentionsStudentInfo(String content) {
        return content != null && STUDENT_INFO_MENTION.matcher(content).find();
    }
}
