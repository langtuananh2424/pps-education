package vn.com.pps.education.system.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.system.dto.PpsScoringConfigHistoryResponse;
import vn.com.pps.education.system.dto.PpsScoringConfigResponse;
import vn.com.pps.education.system.dto.PpsScoringConfigUpdateRequest;
import vn.com.pps.education.system.service.PpsScoringConfigService;

import java.util.List;
import java.util.Map;

/**
 * Cấu hình bộ chấm phát âm PPS Hybrid v2 (trang Quản trị hệ thống). Xem được: system.settings.view; sửa, lưu, khôi phục mặc định:
 * system.settings.manage (chỉ Quản trị viên hệ thống, như Cài đặt hệ thống).
 */
@RestController
@RequestMapping("/api/pps-scoring-config")
public class PpsScoringConfigController {

    private final PpsScoringConfigService service;

    public PpsScoringConfigController(PpsScoringConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'system.settings.view')")
    public ResponseEntity<PpsScoringConfigResponse> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    @PreAuthorize("hasPermission(null, 'system.settings.manage')")
    public ResponseEntity<PpsScoringConfigResponse> save(@Valid @RequestBody PpsScoringConfigUpdateRequest request,
                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(service.save(request.config(), request.note(), actor.userId()));
    }

    @PostMapping("/reset")
    @PreAuthorize("hasPermission(null, 'system.settings.manage')")
    public ResponseEntity<PpsScoringConfigResponse> reset(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(service.reset(null, actor.userId()));
    }

    @GetMapping("/export")
    @PreAuthorize("hasPermission(null, 'system.settings.view')")
    public ResponseEntity<Map<String, Object>> export() {
        return ResponseEntity.ok(service.export());
    }

    @GetMapping("/history")
    @PreAuthorize("hasPermission(null, 'system.settings.view')")
    public ResponseEntity<List<PpsScoringConfigHistoryResponse>> history() {
        return ResponseEntity.ok(service.history());
    }
}
