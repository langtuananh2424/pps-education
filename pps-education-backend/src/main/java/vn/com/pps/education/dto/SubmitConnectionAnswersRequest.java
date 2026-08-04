package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Nộp TOÀN BỘ câu trả lời cho ĐÚNG 1 lượt xem (watchSessionId ở path) — phải khớp chính xác tập câu hỏi của video, không thiếu không thừa. */
public record SubmitConnectionAnswersRequest(
        @NotEmpty @Valid List<ConnectionAnswerItem> answers
) {}
