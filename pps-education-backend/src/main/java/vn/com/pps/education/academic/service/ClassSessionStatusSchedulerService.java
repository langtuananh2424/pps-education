package vn.com.pps.education.academic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.repository.ClassSessionRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * UC-48 A5: Tự chuyển trạng thái buổi học theo giờ (bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-10-01) — SCHEDULED → IN_PROGRESS khi tới giờ bắt đầu, SCHEDULED/IN_PROGRESS → COMPLETED khi qua
 * giờ kết thúc. Xem docs/uc/phan-he-06-hoc-thuat.md (UC-48, ghi chú "Vòng đời trạng thái buổi học").
 *
 * Chống trùng lặp/lỗi khi nhiều buổi (nhiều lớp) cùng chuyển trạng thái 1 lúc:
 * - Mỗi bước là 1 lệnh UPDATE hàng loạt có điều kiện trạng thái nguồn trong chính câu lệnh — chỉ dòng
 *   còn đúng trạng thái nguồn mới bị đổi, chạy lại bao nhiêu lần cũng cho cùng kết quả.
 * - Khoá advisory theo transaction: nếu có lần chạy khác đang giữ khoá (VD nhiều phiên bản backend
 *   cùng DB) thì bỏ qua lượt này, không chờ, không đổi chồng.
 * - Điều kiện là "đã qua giờ" chứ không phải "qua giờ trong 1 phút vừa rồi" — lượt lỗi/bỏ qua được
 *   lượt sau bù đủ.
 * - Hủy/dời/sửa buổi khoá dòng trước khi kiểm tra trạng thái (ClassSessionRepository#findByIdForUpdate);
 *   các luồng khác chỉ ghi cột đã đổi (ClassSession @DynamicUpdate).
 *
 * Không ghi class_sessions_history cho chuyển trạng thái tự động — suy ra được từ giờ học.
 */
@Service
public class ClassSessionStatusSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ClassSessionStatusSchedulerService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Khoá advisory riêng của job này (pg_try_advisory_xact_lock) — giá trị tuỳ ý, chỉ cần không trùng job khác. */
    static final long ADVISORY_LOCK_KEY = 4_804_001L;

    private final ClassSessionRepository classSessionRepository;
    private final Clock clock;

    public ClassSessionStatusSchedulerService(ClassSessionRepository classSessionRepository, Clock clock) {
        this.classSessionRepository = classSessionRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.class-session-status.cron:0 * * * * *}")
    @Transactional
    public void processSessionStatusTransitions() {
        if (!classSessionRepository.tryAdvisoryXactLock(ADVISORY_LOCK_KEY)) {
            log.debug("Skip class session status transitions: another run holds the lock.");
            return;
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), APP_ZONE);
        int started = classSessionRepository.markStarted(now);
        int completed = classSessionRepository.markCompleted(now);
        if (started > 0 || completed > 0) {
            log.info("Class session status transitions at {}: {} -> IN_PROGRESS, {} -> COMPLETED.", now, started, completed);
        }
    }
}
