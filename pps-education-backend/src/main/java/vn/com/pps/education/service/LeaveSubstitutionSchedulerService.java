package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.LeaveRequest;
import vn.com.pps.education.repository.LeaveSubstitutionRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * UC-11 Mở rộng: tự động thu hồi giáo viên dạy thay sau end_date + 2 ngày
 * (FR-HRM-03, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-05). Chỉ áp dụng đơn Đã duyệt — đơn Từ chối đã được thu hồi
 * ngay lập tức (UC-11 A2, xem LeaveRequestService.decide).
 */
@Service
public class LeaveSubstitutionSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(LeaveSubstitutionSchedulerService.class);
    private static final int AUTO_REVOKE_AFTER_DAYS = 2;

    private final LeaveSubstitutionRepository leaveSubstitutionRepository;
    private final LeaveSubstitutionService leaveSubstitutionService;

    public LeaveSubstitutionSchedulerService(LeaveSubstitutionRepository leaveSubstitutionRepository,
                                              LeaveSubstitutionService leaveSubstitutionService) {
        this.leaveSubstitutionRepository = leaveSubstitutionRepository;
        this.leaveSubstitutionService = leaveSubstitutionService;
    }

    @Scheduled(cron = "0 15 1 * * *")
    @Transactional
    public void autoRevokeExpiredSubstitutions() {
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate cutoffEndDate = now.toLocalDate().minusDays(AUTO_REVOKE_AFTER_DAYS);
        var due = leaveSubstitutionRepository.findDueForAutoRevoke(LeaveRequest.Status.APPROVED, cutoffEndDate);
        due.forEach(ls -> leaveSubstitutionService.revoke(ls, now));
        if (!due.isEmpty()) {
            log.info("Tự động thu hồi {} lượt dạy thay quá hạn (end_date + {} ngày).", due.size(), AUTO_REVOKE_AFTER_DAYS);
        }
    }
}
