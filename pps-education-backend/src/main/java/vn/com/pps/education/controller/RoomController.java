package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateRoomRequest;
import vn.com.pps.education.dto.RoomResponse;
import vn.com.pps.education.dto.UpdateRoomRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.RoomService;

import java.util.List;

/** UC-37: Quản lý phòng học (FR-FAC-04) — xem Javadoc RoomService. */
@RestController
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PreAuthorize("hasPermission(null, 'facility.room.manage')")
    @PostMapping("/api/rooms")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(roomService.createRoom(request, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'facility.room.manage')")
    @PutMapping("/api/rooms/{id}")
    public ResponseEntity<RoomResponse> updateRoom(@PathVariable Long id, @Valid @RequestBody UpdateRoomRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(roomService.updateRoom(id, request, actor.userId()));
    }

    @GetMapping("/api/sites/{siteId}/rooms")
    public ResponseEntity<List<RoomResponse>> listBySite(@PathVariable Long siteId) {
        return ResponseEntity.ok(roomService.listBySite(siteId));
    }
}
