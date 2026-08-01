package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — client
 * gửi theo lô các khoảng "thoát ra ngoài" ĐÃ KẾT THÚC (học sinh đã quay
 * lại), không gửi trạng thái "đang diễn ra". Xem
 * docs/uc/phan-he-07-lms-portal.md.
 */
public record RecordIntegrityEventsRequest(
        @NotEmpty @Valid List<Event> events
) {
    /** eventType: OUT_OF_FOCUS / FULLSCREEN_EXITED (khớp AttemptIntegrityEvent.EventType). */
    public record Event(
            @NotBlank String eventType,
            @NotNull OffsetDateTime startedAt,
            @NotNull OffsetDateTime endedAt,
            String userAgent
    ) {}
}
