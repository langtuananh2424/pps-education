package vn.com.pps.education.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.dto.CommentAiDraftResult;
import vn.com.pps.education.dto.CommentAiSuggestionResult;
import vn.com.pps.education.exception.CommentAiDraftFailedException;
import vn.com.pps.education.exception.ResourceNotFoundException;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-74/UC-75 — job chạy nền: DONE/FAILED, chỉ người tạo xem được, và chỉ qua đúng loại kết quả. */
class AiJobRegistryTest {

    private final AiJobRegistry registry = new AiJobRegistry(1, 30, Clock.systemUTC());

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    private <T> AiJobRegistry.Snapshot<T> awaitFinished(String jobId, Long owner, Class<T> type) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            AiJobRegistry.Snapshot<T> job = registry.get(jobId, owner, type);
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

        AiJobRegistry.Snapshot<CommentAiDraftResult> started = registry.submit(1L, () -> result);
        AiJobRegistry.Snapshot<CommentAiDraftResult> finished = awaitFinished(started.jobId(), 1L, CommentAiDraftResult.class);

        assertThat(finished.status()).isEqualTo("DONE");
        assertThat(finished.result()).isEqualTo(result);
    }

    @Test
    void submit_UC74_A5_failureBecomesFailedWithMessage() throws InterruptedException {
        AiJobRegistry.Snapshot<CommentAiDraftResult> started = registry.submit(1L, () -> {
            throw new CommentAiDraftFailedException("AI lỗi");
        });

        AiJobRegistry.Snapshot<CommentAiDraftResult> finished = awaitFinished(started.jobId(), 1L, CommentAiDraftResult.class);

        assertThat(finished.status()).isEqualTo("FAILED");
        assertThat(finished.errorMessage()).isEqualTo("AI lỗi");
        assertThat(finished.result()).isNull();
    }

    @Test
    void get_UC74_otherUserCannotSeeJob() {
        AiJobRegistry.Snapshot<Object> started = registry.submit(1L, () -> null);

        assertThatThrownBy(() -> registry.get(started.jobId(), 2L, Object.class)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_UC75_jobCannotBeReadAsAnotherResultType() throws InterruptedException {
        CommentAiDraftResult result = new CommentAiDraftResult("t", "ok", null, List.of(), List.of(), List.of());
        AiJobRegistry.Snapshot<CommentAiDraftResult> started = registry.submit(1L, () -> result);
        awaitFinished(started.jobId(), 1L, CommentAiDraftResult.class);

        assertThatThrownBy(() -> registry.get(started.jobId(), 1L, CommentAiSuggestionResult.class))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
