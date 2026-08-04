package vn.com.pps.education.dto;

import java.util.List;

public record ReviewVideoConnectionQuestionResponse(
        Long id,
        Long reviewVideoId,
        String prompt,
        int displayOrder,
        List<ReviewVideoConnectionChoiceResponse> choices
) {}
