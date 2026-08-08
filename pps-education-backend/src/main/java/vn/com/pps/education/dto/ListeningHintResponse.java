package vn.com.pps.education.dto;

import java.util.List;

/** correctAnswerText/correctChoiceIds chỉ điền theo đúng loại câu hỏi (FILL_IN_BLANK/MULTIPLE_CHOICE) — rỗng/null với SPEAKING (Nghe & nộp audio, chấm tay, không có đáp án mẫu). */
public record ListeningHintResponse(
        String transcript,
        String correctAnswerText,
        List<Long> correctChoiceIds,
        String explanation
) {}
