package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.SystemSettingHistoryResponse;
import vn.com.pps.education.dto.SystemSettingResponse;
import vn.com.pps.education.dto.SystemSettingUpdateRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.SystemSettingService;

import java.util.List;

/**
 * Cài đặt hệ thống — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-08. Chỉ Quản trị viên hệ thống (permission system.settings.manage,
 * gán riêng cho SYS_ADMIN — xem V105).
 */
@RestController
@RequestMapping("/api/system-settings")
@PreAuthorize("hasPermission(null, 'system.settings.manage')")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    public SystemSettingController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    public ResponseEntity<List<SystemSettingResponse>> listAll() {
        return ResponseEntity.ok(systemSettingService.listAll());
    }

    @PutMapping("/{settingKey}")
    public ResponseEntity<SystemSettingResponse> update(@PathVariable String settingKey,
                                                          @Valid @RequestBody SystemSettingUpdateRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(systemSettingService.update(settingKey, request.settingValue(), actor.userId()));
    }

    @GetMapping("/{settingKey}/history")
    public ResponseEntity<List<SystemSettingHistoryResponse>> getHistory(@PathVariable String settingKey) {
        return ResponseEntity.ok(systemSettingService.getHistory(settingKey));
    }
}
