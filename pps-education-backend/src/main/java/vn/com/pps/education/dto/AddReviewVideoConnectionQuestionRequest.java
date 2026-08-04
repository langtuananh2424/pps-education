package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: Giáo viên thêm 1 câu hỏi trắc nghiệm tự chấm vào video CONNECTION. */
public record AddReviewVideoConnectionQuestionRequest(
        @NotBlank String prompt,
        Integer displayOrder,
        @NotEmpty @Valid List<ConnectionChoiceRequest> choices
) {}
