package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * UC-23b: Học sinh nộp/nộp lại audio trả lời cho video REFLEX — audioUrl
 * đã upload sẵn qua POST /api/media/upload.
 *
 * integrityEvents (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-31, tùy chọn — null/rỗng nếu không giám sát hoặc không có sự
 * kiện nào): UC-23b không có "phiên bắt đầu ghi âm" ở backend, nên sự
 * kiện thoát ra ngoài trong lúc ghi âm được đệm ở client rồi gửi kèm CÙNG
 * LÚC nộp audio thay vì gửi real-time — xem Javadoc
 * ReviewVideoQuestionIntegrityContextResolver.
 */
public record SubmitReviewVideoAudioRequest(
        @NotBlank String audioUrl,
        @Valid RecordIntegrityEventsRequest integrityEvents
) {}
