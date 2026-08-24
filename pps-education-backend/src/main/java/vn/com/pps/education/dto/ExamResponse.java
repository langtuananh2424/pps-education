package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) —
 * skillCategory/allowRetake/maxAttempts/passThresholdPercent chuyển từ
 * ExerciseResponse lên đây (cấu hình chung cho cả Đề, xem Exam.SkillCategory).
 */
public record ExamResponse(
        Long id,
        UUID uuid,
        String code,
        String title,
        Long curriculumId,
        String curriculumCode,
        Long createdBy,
        String teacherType,
        String examType,
        /** NULL = chưa phân loại (dữ liệu cũ trước V144). */
        String skillCategory,
        boolean allowRetake,
        Integer maxAttempts,
        BigDecimal passThresholdPercent,
        /** V75 (Kho đề): Ngân hàng câu hỏi nội bộ tự sinh cùng Đề — thêm câu hỏi qua QuestionBankService#createQuestion(questionBankId, ...). */
        Long questionBankId
) {}
