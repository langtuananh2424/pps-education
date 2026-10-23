package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.lms.domain.ReflexGradingJob;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface ReflexGradingJobRepository extends JpaRepository<ReflexGradingJob, Long> {

    /**
     * Khoá 1 job QUEUED đến hạn của làn; {@code SKIP LOCKED} để nhiều worker/nhiều instance không giành nhau.
     * Phải gọi trong giao dịch (khoá giữ đến khi commit).
     */
    @Query(value = """
            SELECT id FROM reflex_grading_job
            WHERE lane = :lane AND status = 'QUEUED' AND next_run_at <= now()
            ORDER BY next_run_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Long> lockNextQueuedId(@Param("lane") String lane);

    /** Đưa job RUNNING quá hạn (worker chết giữa chừng) về QUEUED. */
    @Modifying
    @Query("""
            UPDATE ReflexGradingJob j
            SET j.status = vn.com.pps.education.lms.domain.ReflexGradingJob.Status.QUEUED, j.lockedAt = NULL,
                j.lockedBy = NULL, j.updatedAt = :now
            WHERE j.status = vn.com.pps.education.lms.domain.ReflexGradingJob.Status.RUNNING AND j.lockedAt < :cutoff
            """)
    int requeueStale(@Param("cutoff") OffsetDateTime cutoff, @Param("now") OffsetDateTime now);
}
