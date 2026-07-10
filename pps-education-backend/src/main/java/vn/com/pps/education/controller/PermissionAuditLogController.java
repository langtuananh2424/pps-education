package vn.com.pps.education.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.PermissionAuditLogResponse;
import vn.com.pps.education.dto.PermissionAuditLogSearchRequest;
import vn.com.pps.education.service.PermissionAuditLogService;

import java.time.OffsetDateTime;

/** UC-05: Xem nhật ký thay đổi quyền (FR-PER-04). */
@RestController
@RequestMapping("/api/permission-audit-logs")
@PreAuthorize("hasPermission(null, 'permission.audit.view')")
public class PermissionAuditLogController {

    private final PermissionAuditLogService permissionAuditLogService;

    public PermissionAuditLogController(PermissionAuditLogService permissionAuditLogService) {
        this.permissionAuditLogService = permissionAuditLogService;
    }

    @GetMapping
    public ResponseEntity<Page<PermissionAuditLogResponse>> search(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @PageableDefault(size = 20) Pageable pageable) {
        var filter = new PermissionAuditLogSearchRequest(actorUserId, targetUserId, action, fromDate, toDate);
        return ResponseEntity.ok(permissionAuditLogService.search(filter, pageable));
    }
}
