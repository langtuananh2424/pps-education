package vn.com.pps.education.lms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.lms.domain.ReflexGradingJob;
import vn.com.pps.education.lms.domain.ReflexGradingJob.Lane;
import vn.com.pps.education.lms.domain.ReflexGradingJob.Status;
import vn.com.pps.education.lms.repository.ReflexGradingJobRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Cài đặt {@link GradingJobQueue} trên bảng reflex_grading_job (V222). Chỉ quản lý vòng đời job, KHÔNG biết gì về
 * chấm điểm — phần chấm nằm ở {@link ReflexSequentialGradingService#processQueuedJob}.
 */
@Service
public class ReflexGradingQueueService implements GradingJobQueue {

    private static final Logger log = LoggerFactory.getLogger(ReflexGradingQueueService.class);

    private final ReflexGradingJobRepository jobRepository;
    private final ReflexQueueSettings settings;

    public ReflexGradingQueueService(ReflexGradingJobRepository jobRepository, ReflexQueueSettings settings) {
        this.jobRepository = jobRepository;
        this.settings = settings;
    }

    @Override
    @Transactional
    public ReflexGradingJob enqueue(Long progressId, Lane lane, int attemptNumber, Boolean recordingFilter) {
        ReflexGradingJob job = new ReflexGradingJob();
        job.setProgressId(progressId);
        job.setLane(lane);
        job.setAttemptNumber(attemptNumber);
        job.setRecordingFilter(recordingFilter);
        job.setNextRunAt(OffsetDateTime.now());
        return jobRepository.saveAndFlush(job);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReflexGradingJob> find(Long jobId) {
        return jobRepository.findById(jobId);
    }

    @Override
    @Transactional
    public Optional<ReflexGradingJob> claimNext(Lane lane, String workerId) {
        return jobRepository.lockNextQueuedId(lane.name()).flatMap(jobRepository::findById).map(job -> {
            job.setStatus(Status.RUNNING);
            job.setTryCount(job.getTryCount() + 1);
            job.setLockedAt(OffsetDateTime.now());
            job.setLockedBy(workerId);
            return job;
        });
    }

    @Override
    @Transactional
    public void close(Long jobId, Status status, String note) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setLockedAt(null);
            job.setLockedBy(null);
            if (note != null) {
                job.setLastError(note);
            }
        });
    }

    @Override
    @Transactional
    public void scheduleRetry(Long jobId, String error) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(Status.QUEUED);
            job.setLockedAt(null);
            job.setLockedBy(null);
            job.setLastError(error);
            job.setNextRunAt(OffsetDateTime.now().plusSeconds(settings.backoffSeconds(job.getLane(), job.getTryCount())));
        });
    }

    @Override
    @Transactional
    public int requeueStale() {
        OffsetDateTime now = OffsetDateTime.now();
        int n = jobRepository.requeueStale(now.minusSeconds(settings.staleAfterSeconds()), now);
        if (n > 0) {
            log.warn("ReflexGradingQueueService: đưa {} job RUNNING quá hạn về QUEUED", n);
        }
        return n;
    }
}
