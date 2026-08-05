package vn.com.pps.education.dto;

import java.util.UUID;

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
        /** V75 (Kho đề): Ngân hàng câu hỏi nội bộ tự sinh cùng Đề — thêm câu hỏi qua QuestionBankService#createQuestion(questionBankId, ...). */
        Long questionBankId
) {}
