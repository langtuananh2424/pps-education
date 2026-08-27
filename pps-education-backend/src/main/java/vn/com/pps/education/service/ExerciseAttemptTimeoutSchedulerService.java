package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ExerciseAttempt;
import vn.com.pps.education.repository.ExerciseAttemptRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22:
 * enforcement Exercise.timeLimitMinutes cho các lượt làm bài IN_PROGRESS
 * bị bỏ dở (học sinh đóng tab/mất kết nối, không quay lại để saveAnswer
 * kích hoạt lazy check) — mirror cấu trúc
 * HomeworkDueSoonReminderSchedulerService (cùng cron 5 phút). Xem
 * docs/uc/phan-he-07-lms-portal.md UC-24, ExerciseAttemptService#autoFinalizeExpiredAttempt.
 */
@Service
public class ExerciseAttemptTimeoutSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseAttemptTimeoutSchedulerService.class);

    private final ExerciseAttemptRepository exerciseAttemptRepository;
    private final ExerciseAttemptService exerciseAttemptService;

    public ExerciseAttemptTimeoutSchedulerService(ExerciseAttemptRepository exerciseAttemptRepository,
                                                    ExerciseAttemptService exerciseAttemptService) {
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.exerciseAttemptService = exerciseAttemptService;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void runTimeoutScan() {
        OffsetDateTime now = OffsetDateTime.now();
        List<ExerciseAttempt> candidates = exerciseAttemptRepository
                .findByStatusAndExercise_TimeLimitMinutesIsNotNull(ExerciseAttempt.Status.IN_PROGRESS);
        int finalized = 0;
        for (ExerciseAttempt attempt : candidates) {
            OffsetDateTime deadline = attempt.getStartedAt().plusMinutes(attempt.getExercise().getTimeLimitMinutes());
            if (now.isAfter(deadline)) {
                exerciseAttemptService.autoFinalizeExpiredAttempt(attempt);
                finalized++;
            }
        }
        if (finalized > 0) {
            log.info("ExerciseAttemptTimeoutSchedulerService: tự động chốt {} lượt làm bài hết thời gian.", finalized);
        }
    }
}
