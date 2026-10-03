package vn.com.pps.education.lms.dto;

public record ReviewVideoQuestionResponse(
        Long id,
        Long reviewVideoId,
        Integer timestampSeconds,
        String prompt,
        Integer maxRecordingSeconds,
        Integer maxAttempts,
        Integer displayOrder,
        /** V200 — dạng đề; null = câu hỏi cũ (suy theo thời lượng). */
        String questionFormat,
        /** V200 — dạng PICTURE: ảnh tranh. null với học sinh. */
        String pictureImageUrl,
        /** V200 — dạng PICTURE: mô tả tranh dùng để xét lạc đề. LUÔN null với học sinh (không lộ "đáp án" nội dung tranh). */
        String pictureBrief
) {}
