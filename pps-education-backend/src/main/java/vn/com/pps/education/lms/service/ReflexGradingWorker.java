package vn.com.pps.education.lms.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.com.pps.education.lms.domain.ReflexGradingJob;
import vn.com.pps.education.lms.domain.ReflexGradingJob.Lane;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Worker chấm AI Clip phản xạ (V222). Mỗi làn có nhóm luồng RIÊNG (số luồng cấu hình theo
 * {@code app.reflex-queue.*-concurrency}); mỗi luồng lặp: nhận job → chấm → nghỉ {@code poll-interval-ms} khi hàng
 * đợi trống. Chỉ tạo khi bật {@code app.reflex-queue.enabled}.
 */
@Component
@ConditionalOnProperty(name = "app.reflex-queue.enabled", havingValue = "true")
public class ReflexGradingWorker {

    private static final Logger log = LoggerFactory.getLogger(ReflexGradingWorker.class);

    private final GradingJobQueue queue;
    private final ReflexSequentialGradingService gradingService;
    private final ReflexQueueSettings settings;
    private final List<ExecutorService> pools = new ArrayList<>();
    private volatile boolean running;
    private String hostName = "worker";

    public ReflexGradingWorker(GradingJobQueue queue, ReflexSequentialGradingService gradingService,
                               ReflexQueueSettings settings) {
        this.queue = queue;
        this.gradingService = gradingService;
        this.settings = settings;
    }

    @PostConstruct
    void start() {
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            // giữ tên mặc định
        }
        running = true;
        for (Lane lane : Lane.values()) {
            int n = settings.concurrency(lane);
            ExecutorService pool = Executors.newFixedThreadPool(n, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            pools.add(pool);
            for (int i = 1; i <= n; i++) {
                String workerId = hostName + "/" + lane.name().toLowerCase() + "-" + i;
                pool.submit(() -> loop(lane, workerId));
            }
            log.info("ReflexGradingWorker: làn {} chạy {} luồng", lane, n);
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        pools.forEach(ExecutorService::shutdown);
        for (ExecutorService pool : pools) {
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Cứu job RUNNING bị bỏ dở (worker/instance chết giữa chừng). */
    @Scheduled(fixedDelayString = "${app.reflex-queue.stale-sweep-ms:60000}")
    void sweepStale() {
        try {
            queue.requeueStale();
        } catch (RuntimeException e) {
            log.warn("ReflexGradingWorker: lỗi khi cứu job quá hạn: {}", e.toString());
        }
    }

    private void loop(Lane lane, String workerId) {
        while (running) {
            boolean worked = false;
            try {
                Optional<ReflexGradingJob> job = queue.claimNext(lane, workerId);
                if (job.isPresent()) {
                    worked = true;
                    gradingService.processQueuedJob(job.get().getId());
                }
            } catch (Throwable t) {
                // Job (nếu có) vẫn RUNNING → sweepStale đưa về hàng đợi; không để luồng worker chết.
                log.error("ReflexGradingWorker[{}]: lỗi khi xử lý job: {}", workerId, t.toString(), t);
            }
            if (!worked) {
                try {
                    Thread.sleep(settings.pollIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
