package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.LeaveSubstitutionResponse;
import vn.com.pps.education.service.LeaveSubstitutionService;

import java.util.List;

/**
 * UC-11 Mở rộng: trang lịch sử dạy thay (FR-HRM-03, bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng 2026-08-05).
 */
@RestController
@RequestMapping("/api/leave-substitutions")
public class LeaveSubstitutionController {

    private final LeaveSubstitutionService leaveSubstitutionService;

    public LeaveSubstitutionController(LeaveSubstitutionService leaveSubstitutionService) {
        this.leaveSubstitutionService = leaveSubstitutionService;
    }

    @GetMapping
    public ResponseEntity<List<LeaveSubstitutionResponse>> history() {
        return ResponseEntity.ok(leaveSubstitutionService.history());
    }
}
