package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * {@code uuid} cần lộ ra để dán trực tiếp vào cột "BTVN Ngữ pháp buổi sau"
 * khi import Excel (StudentCommentService.resolveGrammarExerciseSoft) —
 * trước V65 người dùng dán uuid của ExerciseAssignment, giờ dán thẳng uuid
 * của Exercise (nguồn), không qua bản giao nào.
 *
 * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * {@code examId}/{@code examCode}/{@code examTitle} thay cho
 * {@code curriculumId} cũ — mỗi Bài (Exercise) thuộc 1 Đề (Exam), khung
 * chương trình nay là thuộc tính của Đề, không còn trên Bài. examCode/
 * examTitle denormalize (mirror ExerciseAssignmentResponse.exerciseTitle/
 * exerciseCode) để FE render nhãn "Mã Đề - Tên bài" không cần round-trip
 * thêm.
 *
 * Nhận xét học viên (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-05): {@code examTeacherType} denormalize từ {@code Exam.teacherType}
 * (VIETNAMESE/FOREIGN) — để FE lọc dropdown "BTVN buổi sau" theo đúng loại
 * giáo viên đang chọn ở màn Nhận xét học viên mà không cần gọi thêm listExams().
 *
 * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) —
 * skillCategory/allowRetake/maxAttempts/passThresholdPercent đã bỏ khỏi
 * đây, chuyển lên {@link ExamResponse} (cấu hình chung cho cả Đề — xem
 * Javadoc Exam.SkillCategory). {@code examSkillCategory} denormalize từ
 * {@code Exam.skillCategory} (mirror examTeacherType ở trên) — để FE lọc
 * dropdown "BTVN online Reading/Writing buổi sau" (Nhận xét học viên) theo
 * đúng nhóm kỹ năng mà không cần gọi thêm listExams(); null nếu Đề chưa
 * phân loại (dữ liệu cũ trước V144).
 */
public record ExerciseResponse(
        Long id,
        UUID uuid,
        String code,
        String title,
        Long examId,
        String examCode,
        String examTitle,
        String examTeacherType,
        String examSkillCategory,
        Long subjectId,
        String exerciseType,
        BigDecimal totalPoints,
        Integer timeLimitMinutes,
        boolean showCorrectAnswers,
        String status,
        Long createdBy,
        boolean hasEssayOrSpeaking
) {}
