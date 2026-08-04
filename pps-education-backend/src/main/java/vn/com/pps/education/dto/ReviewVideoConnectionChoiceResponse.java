package vn.com.pps.education.dto;

/** isCorrect = null khi trả về cho HỌC SINH (chưa nộp bài) — chỉ Giáo viên soạn bài mới thấy giá trị thật. */
public record ReviewVideoConnectionChoiceResponse(
        Long id,
        String choiceLabel,
        String content,
        Boolean isCorrect,
        int displayOrder
) {}
