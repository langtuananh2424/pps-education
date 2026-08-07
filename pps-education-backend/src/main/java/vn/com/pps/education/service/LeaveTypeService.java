package vn.com.pps.education.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.LeaveType;
import vn.com.pps.education.dto.LeaveTypeResponse;
import vn.com.pps.education.repository.LeaveTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {
  private final LeaveTypeRepository leaveTypeRepository;

  public List<LeaveTypeResponse> getAllActiveLeaveTypes() {
    return leaveTypeRepository.findByIsActiveTrueOrderBySortOrder()
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private LeaveTypeResponse toResponse(LeaveType leaveType) {
    return new LeaveTypeResponse(
        leaveType.getCode(),
        leaveType.getLabel(),
        leaveType.getSortOrder()
    );
  }
}
