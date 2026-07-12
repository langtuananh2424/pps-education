package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateEquipmentRequest;
import vn.com.pps.education.dto.EquipmentResponse;
import vn.com.pps.education.dto.UpdateEquipmentStatusRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.RoomService;

import java.util.List;

/** UC-37: Quản lý thiết bị dạy học (FR-FAC-02) — xem Javadoc RoomService. */
@RestController
public class EquipmentController {

    private final RoomService roomService;

    public EquipmentController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/api/equipment")
    public ResponseEntity<EquipmentResponse> createEquipment(@Valid @RequestBody CreateEquipmentRequest request,
                                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(roomService.createEquipment(request, actor.userId()));
    }

    /** A1: Thiết bị hỏng/bảo trì. */
    @PutMapping("/api/equipment/{id}/status")
    public ResponseEntity<EquipmentResponse> updateStatus(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateEquipmentStatusRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(roomService.updateEquipmentStatus(id, request, actor.userId()));
    }

    @GetMapping("/api/rooms/{roomId}/equipment")
    public ResponseEntity<List<EquipmentResponse>> listByRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.listByRoom(roomId));
    }

    @GetMapping("/api/sites/{siteId}/equipment")
    public ResponseEntity<List<EquipmentResponse>> listBySite(@PathVariable Long siteId) {
        return ResponseEntity.ok(roomService.listEquipmentBySite(siteId));
    }
}
