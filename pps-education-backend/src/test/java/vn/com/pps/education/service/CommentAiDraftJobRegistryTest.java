package vn.com.pps.education.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.dto.CommentAiDraftJobResponse;
import vn.com.pps.education.dto.CommentAiDraftResult;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.ResourceNotFoundException;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-74 bước 2 + A5 — job chạy nền: DONE/FAILED, và chỉ người tạo xem được kết quả. */
class CommentAiDraftJobRegistryTest {

    private final CommentAiDraftJobRegistry registry = new CommentAiDraftJobRegistry(1, 30, Clock.systemUTC());

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    private CommentAiDraftJobResponse awaitFinished(String jobId, Long owner) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            CommentAiDraftJobResponse job = registry.get(jobId, owner);
            if (!"RUNNING".equals(job.status())) {
                return job;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("job không kết thúc");
    }

    @Test
    void submit_UC74_MainFlow_jobCompletesWithResult() throws InterruptedException {
        CommentAiDraftResult result = new CommentAiDraftResult("t", "ok", null, List.of(), List.of(), List.of());

        CommentAiDraftJobResponse started = registry.submit(1L, () -> result);
        CommentAiDraftJobResponse finished = awaitFinished(started.jobId(), 1L);

        assertThat(finished.status()).isEqualTo("DONE");
        assertThat(finished.result()).isEqualTo(result);
    }

    @Test
    void submit_UC74_A5_failureBecomesFailedWithMessage() throws InterruptedException {
        CommentAiDraftJobResponse started = registry.submit(1L, () -> {
            throw new CommentAiDraftFailedException("AI lỗi");
        });

        CommentAiDraftJobResponse finished = awaitFinished(started.jobId(), 1L);

        assertThat(finished.status()).isEqualTo("FAILED");
        assertThat(finished.errorMessage()).isEqualTo("AI lỗi");
        assertThat(finished.result()).isNull();
    }

    @Test
    void get_UC74_otherUserCannotSeeJob() {
        CommentAiDraftJobResponse started = registry.submit(1L, () -> null);

        assertThatThrownBy(() -> registry.get(started.jobId(), 2L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
