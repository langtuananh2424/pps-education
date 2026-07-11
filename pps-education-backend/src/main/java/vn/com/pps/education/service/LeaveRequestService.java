package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.LeaveRequest;
import vn.com.pps.education.domain.LeaveRequestApproval;
import vn.com.pps.education.domain.LeaveRequestHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateLeaveRequestRequest;
import vn.com.pps.education.dto.DecideLeaveRequestRequest;
import vn.com.pps.education.dto.LeaveRequestResponse;
import vn.com.pps.education.exception.ExecutiveExemptFromLeaveRequestException;
import vn.com.pps.education.exception.LeaveRequestAlreadyFinalizedException;
import vn.com.pps.education.exception.NotCurrentApproverException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.LeaveRequestApprovalRepository;
import vn.com.pps.education.repository.LeaveRequestHistoryRepository;
import vn.com.pps.education.repository.LeaveRequestRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-10: Nộp đơn từ + UC-11: Duyệt đơn từ (FR-HRM-03).
 * Xem docs/uc/phan-he-04-nhan-su.md và
 * docs/diagrams/activity/ActivityDiagram-DuyetDonTu.mmd — 2 UC cùng 1
 * workflow trạng thái, gộp 1 Service (giống AuthService gộp UC-01 login/
 * refresh/logout).
 *
 * total_days: SDD ghi "Tính bởi service" không kèm công thức, đã xác nhận
 * với PM — đếm ngày lịch (bao gồm cuối tuần) cho loại nghỉ cả ngày, cố định
 * 0.5 ngày cho LATE/EARLY_LEAVE.
 */
@Service
public class LeaveRequestService {

    private static final Set<String> MANAGEMENT_ROLE_CODES =
            Set.of("SITE_MANAGER", "HEAD_ACADEMIC", "OPS_MANAGER", "HR_MANAGER");

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestApprovalRepository leaveRequestApprovalRepository;
    private final LeaveRequestHistoryRepository leaveRequestHistoryRepository;

    public LeaveRequestService(EmployeeRepository employeeRepository,
                                UserRepository userRepository,
                                UserRoleRepository userRoleRepository,
                                LeaveRequestRepository leaveRequestRepository,
                                LeaveRequestApprovalRepository leaveRequestApprovalRepository,
                                LeaveRequestHistoryRepository leaveRequestHistoryRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveRequestApprovalRepository = leaveRequestApprovalRepository;
        this.leaveRequestHistoryRepository = leaveRequestHistoryRepository;
    }

    /** UC-10 Main Flow bước 1-4, A1 (Ban giám đốc), A2 (phòng ban không có trưởng phòng). */
    @Transactional
    public LeaveRequestResponse submit(Long actorUserId, CreateLeaveRequestRequest request) {
        User actor = getUserOrThrow(actorUserId);
        Employee employee = employeeRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa có hồ sơ nhân sự."));
        Set<String> roleCodes = roleCodesOf(actorUserId);

        // Main Flow bước 1 / A1 -- Ban giám đốc miễn trừ hoàn toàn.
        if (roleCodes.contains("EXECUTIVE")) {
            throw new ExecutiveExemptFromLeaveRequestException("Ban giám đốc được miễn trừ, không thể nộp đơn từ.");
        }

        LeaveRequest.LeaveType leaveType = LeaveRequest.LeaveType.valueOf(request.leaveType());
        boolean partialDay = leaveType == LeaveRequest.LeaveType.LATE || leaveType == LeaveRequest.LeaveType.EARLY_LEAVE;
        if (partialDay) {
            if (request.startTime() == null || request.endTime() == null || !request.endTime().isAfter(request.startTime())) {
                throw new IllegalArgumentException("LATE/EARLY_LEAVE cần startTime/endTime hợp lệ (endTime sau startTime).");
            }
            if (!request.startDate().equals(request.endDate())) {
                throw new IllegalArgumentException("LATE/EARLY_LEAVE chỉ áp dụng trong 1 ngày (startDate = endDate).");
            }
        } else if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate không được trước startDate.");
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(employee);
        lr.setLeaveType(leaveType);
        lr.setStartDate(request.startDate());
        lr.setEndDate(request.endDate());
        lr.setStartTime(partialDay ? request.startTime() : null);
        lr.setEndTime(partialDay ? request.endTime() : null);
        lr.setTotalDays(computeTotalDays(leaveType, request.startDate(), request.endDate()));
        lr.setReason(request.reason());
        lr.setAttachmentUrl(request.attachmentUrl());
        lr.setStatus(LeaveRequest.Status.PENDING);
        lr.setCurrentStep(1);
        lr.setSubmittedAt(OffsetDateTime.now());

        // Main Flow bước 3 -- xác định workflow duyệt.
        List<LeaveRequestApproval.ApproverRole> steps;
        Department department = actor.getDepartment();
        if (!MANAGEMENT_ROLE_CODES.isEmpty() && roleCodes.stream().anyMatch(MANAGEMENT_ROLE_CODES::contains)) {
            steps = List.of(LeaveRequestApproval.ApproverRole.EXECUTIVE);
        } else if (department != null && department.getHeadUser() != null) {
            steps = List.of(LeaveRequestApproval.ApproverRole.DEPARTMENT_HEAD,
                    LeaveRequestApproval.ApproverRole.OPERATIONS_MANAGER);
            lr.setCurrentApprover(department.getHeadUser());
        } else {
            // A2 -- phòng ban không có trưởng phòng, bỏ qua bước DEPARTMENT_HEAD.
            steps = List.of(LeaveRequestApproval.ApproverRole.OPERATIONS_MANAGER);
        }

        lr = leaveRequestRepository.save(lr);
        for (int i = 0; i < steps.size(); i++) {
            LeaveRequestApproval approval = new LeaveRequestApproval();
            approval.setLeaveRequest(lr);
            approval.setStepOrder(i + 1);
            approval.setApproverRole(steps.get(i));
            leaveRequestApprovalRepository.save(approval);
        }

        writeHistory(lr, actor, LeaveRequestHistory.Action.CREATED);
        // TODO(module Notification, Backend Phase B): thông báo người duyệt bước đầu tiên (Postcondition).
        return toResponse(lr);
    }

    /** UC-11 Main Flow bước 1: danh sách đơn chờ duyệt thuộc thẩm quyền người gọi. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listPendingForApprover(Long actorUserId) {
        Set<String> roleCodes = roleCodesOf(actorUserId);
        List<LeaveRequest> direct = leaveRequestRepository
                .findByStatusAndCurrentApproverId(LeaveRequest.Status.PENDING, actorUserId);
        List<LeaveRequest> roleBased = leaveRequestRepository
                .findByStatusAndCurrentApproverIsNull(LeaveRequest.Status.PENDING).stream()
                .filter(lr -> currentApproverRoleMatches(lr, roleCodes))
                .toList();
        return java.util.stream.Stream.concat(direct.stream(), roleBased.stream())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(Long id) {
        return toResponse(getLeaveRequestOrThrow(id));
    }

    /**
     * UC-11 Main Flow bước 3-6, A1 (từ chối giữa chừng).
     */
    @Transactional
    public LeaveRequestResponse decide(Long actorUserId, Long leaveRequestId, DecideLeaveRequestRequest request) {
        User actor = getUserOrThrow(actorUserId);
        LeaveRequest lr = getLeaveRequestOrThrow(leaveRequestId);
        if (lr.getStatus() != LeaveRequest.Status.PENDING) {
            throw new LeaveRequestAlreadyFinalizedException("Đơn từ id=" + leaveRequestId + " đã ở trạng thái cuối.");
        }
        LeaveRequestApproval currentApproval = leaveRequestApprovalRepository
                .findByLeaveRequestIdAndStepOrder(lr.getId(), lr.getCurrentStep())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bước duyệt hiện tại cho đơn id=" + leaveRequestId));

        Set<String> roleCodes = roleCodesOf(actorUserId);
        boolean authorized = lr.getCurrentApprover() != null
                ? lr.getCurrentApprover().getId().equals(actorUserId)
                : approverRoleMatchesRoleCodes(currentApproval.getApproverRole(), roleCodes);
        if (!authorized) {
            throw new NotCurrentApproverException("Bạn không có thẩm quyền duyệt bước hiện tại của đơn id=" + leaveRequestId);
        }

        LeaveRequestApproval.Decision decision = LeaveRequestApproval.Decision.valueOf(request.decision());
        currentApproval.setApproverUser(actor);
        currentApproval.setDecision(decision);
        currentApproval.setComment(request.comment());
        currentApproval.setDecidedAt(OffsetDateTime.now());
        leaveRequestApprovalRepository.save(currentApproval);

        if (decision == LeaveRequestApproval.Decision.REJECTED) {
            // A1 -- từ chối giữa chừng, kết thúc ngay, không đi tiếp các bước còn lại.
            lr.setStatus(LeaveRequest.Status.REJECTED);
            lr.setFinalizedAt(OffsetDateTime.now());
            lr.setCurrentApprover(null);
        } else {
            Optional<LeaveRequestApproval> nextStep = leaveRequestApprovalRepository
                    .findByLeaveRequestIdAndStepOrder(lr.getId(), lr.getCurrentStep() + 1);
            if (nextStep.isPresent()) {
                lr.setCurrentStep(lr.getCurrentStep() + 1);
                lr.setCurrentApprover(null); // bước kế tiếp luôn role-based (OPERATIONS_MANAGER/EXECUTIVE)
            } else {
                lr.setStatus(LeaveRequest.Status.APPROVED);
                lr.setFinalizedAt(OffsetDateTime.now());
                lr.setCurrentApprover(null);
            }
        }
        lr = leaveRequestRepository.save(lr);

        writeHistory(lr, actor, LeaveRequestHistory.Action.UPDATED);
        // TODO(module Notification, Backend Phase B): thông báo người duyệt bước kế/người nộp đơn (Postcondition).
        return toResponse(lr);
    }

    private boolean currentApproverRoleMatches(LeaveRequest lr, Set<String> roleCodes) {
        return leaveRequestApprovalRepository.findByLeaveRequestIdAndStepOrder(lr.getId(), lr.getCurrentStep())
                .map(a -> approverRoleMatchesRoleCodes(a.getApproverRole(), roleCodes))
                .orElse(false);
    }

    private boolean approverRoleMatchesRoleCodes(LeaveRequestApproval.ApproverRole role, Set<String> roleCodes) {
        return switch (role) {
            case OPERATIONS_MANAGER -> roleCodes.contains("OPS_MANAGER");
            case EXECUTIVE -> roleCodes.contains("EXECUTIVE");
            case DEPARTMENT_HEAD -> false; // luôn xác định qua current_approver_id, không qua role code
        };
    }

    private BigDecimal computeTotalDays(LeaveRequest.LeaveType type, LocalDate startDate, LocalDate endDate) {
        if (type == LeaveRequest.LeaveType.LATE || type == LeaveRequest.LeaveType.EARLY_LEAVE) {
            return new BigDecimal("0.50");
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return BigDecimal.valueOf(days).setScale(2);
    }

    private Set<String> roleCodesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet());
    }

    private void writeHistory(LeaveRequest lr, User actor, LeaveRequestHistory.Action action) {
        LeaveRequestHistory history = new LeaveRequestHistory();
        history.setLeaveRequest(lr);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(snapshot(lr));
        leaveRequestHistoryRepository.save(history);
    }

    private Map<String, Object> snapshot(LeaveRequest lr) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", lr.getStatus().name());
        details.put("currentStep", lr.getCurrentStep());
        details.put("totalDays", lr.getTotalDays());
        details.put("finalizedAt", lr.getFinalizedAt() == null ? null : lr.getFinalizedAt().toString());
        return details;
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
    }

    private LeaveRequest getLeaveRequestOrThrow(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn từ id=" + id));
    }

    private LeaveRequestResponse toResponse(LeaveRequest lr) {
        return new LeaveRequestResponse(
                lr.getId(),
                lr.getEmployee().getId(),
                lr.getLeaveType().name(),
                lr.getStartDate(),
                lr.getEndDate(),
                lr.getStartTime(),
                lr.getEndTime(),
                lr.getTotalDays(),
                lr.getReason(),
                lr.getAttachmentUrl(),
                lr.getStatus().name(),
                lr.getCurrentStep(),
                lr.getCurrentApprover() == null ? null : lr.getCurrentApprover().getId(),
                lr.getSubmittedAt(),
                lr.getFinalizedAt());
    }
}
