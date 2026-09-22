package vn.com.pps.education.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.com.pps.education.dto.AiTokenUsageSummaryResponse;
import vn.com.pps.education.service.AiGradingTokenUsageQueryService;
import vn.com.pps.education.service.AiTokenUsageStream;

import java.time.LocalDate;

/**
 * Sử dụng token AI — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 (V192). Trang Quản trị
 * hệ thống xem chi phí token của luồng chấm AI theo học sinh / bài tập / bước chấm.
 *
 * Gate bằng {@code system.settings.manage} (đã có sẵn, gán riêng cho SYS_ADMIN — xem V105) thay vì tạo
 * mã quyền mới: số liệu này gắn với chi phí vận hành và có kèm tên học sinh, cùng nhóm người xem với
 * "Cài đặt hệ thống". Thêm mã quyền mới sẽ phải kèm migration chèn bảng permissions + gán role, tức tự
 * đặt ra quy tắc phân quyền chưa được thống nhất (xem .claude/rules/business-fidelity.md) — nếu sau này
 * cần tách quyền riêng cho Kế toán/BGH thì làm bằng 1 migration có xác nhận, không tự quyết ở đây.
 */
@RestController
@RequestMapping("/api/ai-token-usage")
@PreAuthorize("hasPermission(null, 'system.settings.manage')")
public class AiTokenUsageController {

    private final AiGradingTokenUsageQueryService queryService;
    private final AiTokenUsageStream stream;

    public AiTokenUsageController(AiGradingTokenUsageQueryService queryService, AiTokenUsageStream stream) {
        this.queryService = queryService;
        this.stream = stream;
    }

    /** Để trống from/to thì lấy 30 ngày gần nhất (xem {@link AiGradingTokenUsageQueryService#summarize}). */
    @GetMapping("/summary")
    public ResponseEntity<AiTokenUsageSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(queryService.summarize(from, to));
    }

    /**
     * Kênh SSE đẩy từng lượt chấm ngay khi xong, để trang tự cập nhật không cần F5 (đã xác nhận với
     * người dùng 2026-09-22 — chọn SSE thay vì bắn webhook ra dịch vụ ngoài, dữ liệu học sinh không rời
     * hệ thống). Trình duyệt tự kết nối lại khi rớt; số liệu đầy đủ vẫn lấy qua {@code /summary}.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return stream.subscribe();
    }
}
