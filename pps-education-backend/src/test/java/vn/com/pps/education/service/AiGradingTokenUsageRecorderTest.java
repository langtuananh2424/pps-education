package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.domain.AiGradingTokenUsage;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.repository.AiGradingTokenUsageRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V192 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — ghi chi phí token AI cho trang
 * Quản trị hệ thống. Test thuần Service logic, mock repository/stream (xem .claude/rules/testing.md).
 */
class AiGradingTokenUsageRecorderTest {

    private final AiGradingTokenUsageRepository repository = mock(AiGradingTokenUsageRepository.class);
    private final AiTokenUsageStream stream = mock(AiTokenUsageStream.class);
    private final AiGradingTokenUsageRecorder recorder = new AiGradingTokenUsageRecorder(repository, stream);

    private final AiTokenUsage usage = new AiTokenUsage("gemini-3.6-flash-medium", true, 7120, 0, 1840, 1100, 8450);

    @Test
    void ghiDuMoiLoaiTokenKemNguCanh() {
        Student student = mock(Student.class);
        recorder.record(AiGradingTokenUsage.Step.SPEAKING, "chatWithAudioJson", "ag/gemini-3.6-flash-medium",
                usage, student, null, null);

        ArgumentCaptor<AiGradingTokenUsage> saved = ArgumentCaptor.forClass(AiGradingTokenUsage.class);
        verify(repository).save(saved.capture());
        AiGradingTokenUsage row = saved.getValue();
        assertThat(row.getStep()).isEqualTo(AiGradingTokenUsage.Step.SPEAKING);
        assertThat(row.getPromptTokens()).isEqualTo(7120);
        assertThat(row.getCompletionTokens()).isEqualTo(1840);
        // reasoningTokens phải lưu RIÊNG, không cộng gộp vào completionTokens — 2 loại này khác nhau về
        // cách provider báo và là căn cứ chính để biết nên cắt input hay cắt thinking.
        assertThat(row.getReasoningTokens()).isEqualTo(1100);
        assertThat(row.getServedModel()).isEqualTo("gemini-3.6-flash-medium");
        assertThat(row.isAudioAttached()).isTrue();
        assertThat(row.isAccepted()).isTrue();
        assertThat(row.getStudent()).isSameAs(student);
    }

    @Test
    void luotBiLoaiVanDuocGhiVoiAcceptedFalse() {
        // 9Router trả sai model / nội dung rỗng -> service chấm nhận null, nhưng token ĐÃ tốn tiền thật.
        // Bỏ qua ở đây sẽ làm tổng trên trang thấp hơn hoá đơn mà không ai biết vì sao.
        recorder.recordRejected("chatJson", "ag/gemini-3.6-flash-medium", usage);

        ArgumentCaptor<AiGradingTokenUsage> saved = ArgumentCaptor.forClass(AiGradingTokenUsage.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isAccepted()).isFalse();
        assertThat(saved.getValue().getPromptTokens()).isEqualTo(7120);
        assertThat(saved.getValue().getStudent()).isNull();
    }

    @Test
    void usageNullThiKhongGhiDongRong() {
        // AI lỗi trước khi kịp gọi -> không có gì để đo; ghi dòng 0 token sẽ làm nhiễu số đếm lượt gọi.
        recorder.record(AiGradingTokenUsage.Step.WRITING, "chatJson", null, null, null, null, null);
        recorder.recordRejected("chatJson", null, null);
        verify(repository, never()).save(any());
    }

    @Test
    void loiGhiChiPhiKhongDuocLamHongLuongChamBai() {
        // Ưu tiên tuyệt đối: học sinh vẫn phải nhận được kết quả chấm kể cả khi DB đo đạc trục trặc.
        when(repository.save(any())).thenThrow(new RuntimeException("DB tạm thời không ghi được"));
        assertThatCode(() -> recorder.record(AiGradingTokenUsage.Step.WRITING, "chatJson", null, usage, null, null, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> recorder.recordRejected("chatJson", null, usage)).doesNotThrowAnyException();
    }

    @Test
    void khongDayLenSseKhiGhiDbThatBai() {
        // Đẩy SSE sau khi lưu: nếu lưu hỏng mà vẫn đẩy thì trang hiện 1 lượt không hề tồn tại trong DB,
        // tải lại trang là biến mất — số liệu tự mâu thuẫn với chính nó.
        when(repository.save(any())).thenThrow(new RuntimeException("DB lỗi"));
        recorder.record(AiGradingTokenUsage.Step.WRITING, "chatJson", null, usage, null, null, null);
        verify(stream, never()).publish(any(), any(), any());
    }
}
