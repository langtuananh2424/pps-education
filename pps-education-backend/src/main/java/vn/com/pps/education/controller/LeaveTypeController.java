package vn.com.pps.education.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.LeaveTypeResponse;
import vn.com.pps.education.service.LeaveTypeService;

import java.util.List;

@RestController
@RequestMapping("/api/leave-types")
@RequiredArgsConstructor
public class LeaveTypeController {
  private final LeaveTypeService leaveTypeService;

  @GetMapping
  public ResponseEntity<List<LeaveTypeResponse>> getAllLeaveTypes() {
    return ResponseEntity.ok(leaveTypeService.getAllActiveLeaveTypes());
  }
}
