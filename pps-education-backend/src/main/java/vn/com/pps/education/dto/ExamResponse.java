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
        Long questionBankId,
        /** V144 — NULL = chưa phân loại vào cấu trúc Sách/Unit/SubTopic mới. */
        Long subTopicId,
        String subTopicTitle,
        /**
         * Bổ sung 2026-09-04 (đã xác nhận với người dùng) — tên Unit chứa subTopic này (VD "UNIT 1: MY
         * NEW SCHOOL"), suy ra qua subTopic.unit — trước đây FE chỉ thấy subTopicTitle, không phân biệt
         * được các Lesson trùng tên (VD "Lesson 1") thuộc Unit/SubTopic khác nhau. NULL cùng điều kiện
         * với subTopicId (Đề chưa phân loại).
         */
        String unitTitle
) {}
