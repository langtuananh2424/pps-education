package vn.com.pps.education.lms.dto;

import java.util.List;

public record ReviewVideoConnectionQuestionResponse(
        Long id,
        Long reviewVideoId,
        String prompt,
        int displayOrder,
        List<ReviewVideoConnectionChoiceResponse> choices
) {}
