package vn.com.pps.education.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.dto.DeviceTokenCountResponse;
import vn.com.pps.education.dto.DeviceTokenRequest;
import vn.com.pps.education.dto.NotificationPreferenceRequest;
import vn.com.pps.education.dto.NotificationPreferenceResponse;
import vn.com.pps.education.dto.NotificationResponse;
import vn.com.pps.education.dto.PushSetupLogRequest;
import vn.com.pps.education.dto.SendNotificationRequest;
import vn.com.pps.education.dto.SendNotificationResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ManualNotificationSendService;
import vn.com.pps.education.service.NotificationService;

import java.util.List;

/** Module Notification — tự phục vụ, mỗi user chỉ thấy/thao tác thông báo của chính mình. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final ManualNotificationSendService manualNotificationSendService;

    public NotificationController(NotificationService notificationService,
                                   ManualNotificationSendService manualNotificationSendService) {
        this.notificationService = notificationService;
        this.manualNotificationSendService = manualNotificationSendService;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> listMine(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(notificationService.listMine(actor.userId(), pageable));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long id,
                                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(notificationService.markRead(actor.userId(), id));
    }

    @GetMapping("/preferences/{notificationType}")
    public ResponseEntity<NotificationPreferenceResponse> getPreference(
            @PathVariable String notificationType, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(notificationService.getPreference(
                actor.userId(), Notification.NotificationType.valueOf(notificationType)));
    }

    @PutMapping("/preferences/{notificationType}")
    public ResponseEntity<NotificationPreferenceResponse> upsertPreference(
            @PathVariable String notificationType,
            @Valid @RequestBody NotificationPreferenceRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(notificationService.upsertPreference(
                actor.userId(), Notification.NotificationType.valueOf(notificationType), request));
    }

    /** Đăng ký/refresh device token cho kênh PUSH (gọi lúc app mở/login). */
    @PostMapping("/device-token")
    public ResponseEntity<Void> registerDeviceToken(@Valid @RequestBody DeviceTokenRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser actor,
                                                      HttpServletRequest httpRequest) {
        notificationService.registerDeviceToken(actor.userId(), request, httpRequest);
        return ResponseEntity.ok().build();
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07): ghi log kết quả 1 lần chạy
     * setupPushNotifications() phía client (thành công/thất bại + lý do) — luồng đăng ký push hiện
     * fire-and-forget, nuốt lỗi hoàn toàn phía FE, không có cách nào debug qua SQL nếu không có
     * Safari Web Inspector. Best-effort — không throw để không ảnh hưởng luồng login chính.
     */
    @PostMapping("/push-setup-log")
    public ResponseEntity<Void> logPushSetup(@Valid @RequestBody PushSetupLogRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser actor,
                                              HttpServletRequest httpRequest) {
        notificationService.logPushSetupResult(actor.userId(), request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    /** Vô hiệu hoá device token (gọi lúc logout). */
    @DeleteMapping("/device-token/{token}")
    public ResponseEntity<Void> deactivateDeviceToken(@PathVariable String token,
                                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        notificationService.deactivateDeviceToken(actor.userId(), token);
        return ResponseEntity.ok().build();
    }

    /**
     * Gửi thông báo thủ công tới user được chọn — bổ sung ngoài SDD gốc, đã
     * xác nhận với người dùng 2026-08-08. Công cụ test/gửi tay của Quản trị
     * viên hệ thống (permission notification.send.manual, gán riêng cho
     * SYS_ADMIN — xem V105).
     */
    @PreAuthorize("hasPermission(null, 'notification.send.manual')")
    @PostMapping("/send-manual")
    public ResponseEntity<SendNotificationResponse> sendManual(@Valid @RequestBody SendNotificationRequest request,
                                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(manualNotificationSendService.sendManual(request, actor.userId()));
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12 — biết trước
     * user nào đang có device token PUSH active, dùng ở trang "Gửi thông báo"
     * để chọn đúng người test kênh Push thay vì tự tra DB.
     */
    @PreAuthorize("hasPermission(null, 'notification.send.manual')")
    @GetMapping("/device-token-counts")
    public ResponseEntity<List<DeviceTokenCountResponse>> getActiveDeviceTokenCounts(@RequestParam List<Long> userIds) {
        return ResponseEntity.ok(notificationService.getActiveDeviceTokenCounts(userIds));
    }
}
