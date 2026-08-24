package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Học sinh tự xem đề đã được giao cho lớp mình đang học — xem Javadoc ExerciseAttemptService.listMyAssignedExercises. */
public record AssignedExerciseResponse(
        Long exerciseId,
        String exerciseCode,
        String title,
        String exerciseType,
        Long assignmentId,
        Long classId,
        String className,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        boolean lateSubmissionAllowed,
        Long myLatestAttemptId,
        String myLatestAttemptStatus,
        BigDecimal myLatestTotalScore,
        /** V89, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05: BTVN <ngưỡng đạt phải làm lại — xem ExerciseAttemptService#applyPassOutcome. */
        BigDecimal myLatestPercentage,
        Boolean myLatestPassed,
        /** V123, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14: giáo viên Việt Nam/nước ngoài phụ trách Đề chứa Bài này (Exam.teacherType), không phải NULL trừ dữ liệu cũ trước khi teacher_type bắt buộc. */
        String teacherType,
        /** V123: ngày buổi học mà Giáo viên đã giao BTVN này (xem ExerciseAssignment#getSourceClassSession()) — NULL với bản giao tạo TRƯỚC V123. */
        LocalDate sessionDate,
        /**
         * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — true khi học sinh BẤM làm lại
         * được NGAY bây giờ (còn lượt theo allowRetake/maxAttempts, bản giao còn ACTIVE, không đang có
         * lượt IN_PROGRESS) — KHÔNG đòi hỏi lượt gần nhất phải chấm xong (FULLY_GRADED) trước, vì
         * backend {@code startAttempt} vốn chưa từng bắt điều kiện đó. Trước đây FE tự suy luận sai
         * (chỉ cho làm lại khi FULLY_GRADED+trượt), khiến học sinh bị kẹt không làm lại được nếu lượt
         * trước đang AUTO_GRADED chờ chấm (VD AI chấm lỗi thoáng qua) dù rõ ràng còn lượt. V148 (bổ
         * sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23): false thêm khi ĐÃ có lượt trước
         * đó VÀ đã quá hạn nộp VÀ bản giao không cho nộp muộn (mirror rào mới ở startAttempt) — dùng để
         * ẩn/hiện nút "Làm lại" tường minh ở Portal (TakeExerciseModal), KHÔNG còn tự động mở lượt mới
         * lúc vào xem bài như trước.
         */
        boolean canStartNewAttempt
) {}
