package vn.com.pps.education.common;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechMeterTest {

    private static final int RATE = 16000;

    /** segments: dương = giây tiếng nói (sóng 440 Hz), âm = giây im lặng. */
    private static byte[] wav(double... segments) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (double seg : segments) {
            int n = (int) Math.round(Math.abs(seg) * RATE);
            for (int i = 0; i < n; i++) {
                short v = seg > 0 ? (short) (8000 * Math.sin(2 * Math.PI * 440 * i / RATE)) : 0;
                pcm.write(v & 0xFF);
                pcm.write((v >> 8) & 0xFF);
            }
        }
        byte[] data = pcm.toByteArray();
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36 + data.length).put("WAVE".getBytes());
        h.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2)
                .putShort((short) 2).putShort((short) 16);
        h.put("data".getBytes()).putInt(data.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(h.array(), 0, 44);
        out.write(data, 0, data.length);
        return out.toByteArray();
    }

    @Test
    void measuresSpeechAndIgnoresLeadingAndTrailingSilenceForPauses() {
        // 1s im lặng | 1s nói | 2s im lặng | 1s nói | 1s im lặng
        SpeechMeter.Measurement m = SpeechMeter.measure(wav(-1, 1, -2, 1, -1)).orElseThrow();
        assertThat(m.hasSpeech()).isTrue();
        assertThat(m.speechSec()).isBetween(1.8, 2.2);
        assertThat(m.durationSec()).isBetween(5.9, 6.1);
        // khoảng dừng dài nhất chỉ là quãng GIỮA 2 đoạn (~2s), không phải im lặng đầu (1s) hay cuối bài
        assertThat(m.longestPauseSec()).isBetween(1.9, 2.2);
    }

    @Test
    void wavWithExtraChunkBeforeData_asFfmpegWrites_isParsed() {
        byte[] plain = wav(-0.5, 1.5, -0.5);
        // ffmpeg chèn khối "LIST" (ở đây 4 byte "INFO") giữa "fmt " (kết thúc ở byte 36) và "data"
        ByteBuffer list = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        list.put("LIST".getBytes()).putInt(4).put("INFO".getBytes());
        byte[] withList = new byte[plain.length + 12];
        System.arraycopy(plain, 0, withList, 0, 36);
        System.arraycopy(list.array(), 0, withList, 36, 12);
        System.arraycopy(plain, 36, withList, 48, plain.length - 36);
        SpeechMeter.Measurement m = SpeechMeter.measure(withList).orElseThrow();
        assertThat(m.hasSpeech()).isTrue();
        assertThat(m.speechSec()).isBetween(1.3, 1.7);
    }

    @Test
    void silenceOnlyHasNoSpeech() {
        SpeechMeter.Measurement m = SpeechMeter.measure(wav(-3)).orElseThrow();
        assertThat(m.hasSpeech()).isFalse();
        assertThat(m.speechSec()).isZero();
    }

    @Test
    void nonWavBytesAreNotMeasured() {
        assertThat(SpeechMeter.measure(new byte[]{1, 2, 3})).isEqualTo(Optional.empty());
        assertThat(SpeechMeter.measure(null)).isEqualTo(Optional.empty());
        assertThat(SpeechMeter.measure(new byte[100])).isEqualTo(Optional.empty());
    }

    /**
     * V204 — bản ghi ồn suốt từ đầu tới cuối (không có quãng im lặng tương phản) có tín/tạp gần 1 (toàn dải
     * cùng biên độ) → trước bản 30/9, ngưỡng {@code noise*3} tự vượt qua đỉnh, không khung nào qua được →
     * hasSpeech SAI thành false dù biên độ rõ ràng là tiếng nói, không phải im lặng. Mirror ca thật người
     * training đo được (tín/tạp 7,5 dB, 2/2481 khung "voiced").
     */
    @Test
    void continuousLoudRecordingWithoutSilenceContrast_stillReportsHasSpeech() {
        // 1 giây duy nhất, không có đoạn im lặng nào để đối chiếu — tín/tạp giữa các khung gần 1.
        SpeechMeter.Measurement m = SpeechMeter.measure(wav(1)).orElseThrow();
        assertThat(m.hasSpeech()).isTrue();
    }

    /** segments như {@link #wav}, nhưng biên độ sóng tuỳ chỉnh — dùng để dựng tín hiệu đỉnh thấp (mic yếu). */
    private static byte[] wavAmplitude(short amplitude, double... segments) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (double seg : segments) {
            int n = (int) Math.round(Math.abs(seg) * RATE);
            for (int i = 0; i < n; i++) {
                short v = seg > 0 ? (short) (amplitude * Math.sin(2 * Math.PI * 440 * i / RATE)) : 0;
                pcm.write(v & 0xFF);
                pcm.write((v >> 8) & 0xFF);
            }
        }
        byte[] data = pcm.toByteArray();
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36 + data.length).put("WAVE".getBytes());
        h.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2)
                .putShort((short) 2).putShort((short) 16);
        h.put("data".getBytes()).putInt(data.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(h.array(), 0, 44);
        out.write(data, 0, data.length);
        return out.toByteArray();
    }

    /**
     * 23/10/2026 (bản bàn giao Gemini 3.8) — hạ sàn đỉnh {@code MIN_SPEECH_PEAK} 0,01 → 0,004: mic yếu trên
     * LMS (Khối 8) ghi lời nói thật ở đỉnh 0,006–0,008, bị kết "không có tiếng nói" oan. Biên độ 280/32768
     * cho RMS đỉnh ≈ 0,006 — nằm GIỮA 0,004 và 0,01 (mô phỏng đúng ca thật).
     */
    @Test
    void weakMicSignal_peakBetween004And01_nowReportsHasSpeech() {
        SpeechMeter.Measurement m = SpeechMeter.measure(wavAmplitude((short) 280, -1, 2, -1)).orElseThrow();
        assertThat(m.peak()).isBetween(0.004, 0.01);
        assertThat(m.hasSpeech()).isTrue();
    }

    /**
     * 22/10/2026 (bản bàn giao Gemini 3.8) — {@code trimEdgeBlips}: tiếng bấm dừng ghi âm ngắn (≤3 khung) ở
     * CUỐI bài, cách đoạn nói thật bằng một quãng im lặng dài, không còn bị tính là "tiếng nói tiếp theo" —
     * trước đây quãng im lặng đó (8 giây) bị tính nhầm thành khoảng dừng GIỮA bài, kéo trần Trôi chảy xuống.
     */
    @Test
    void trailingClickAfterLongSilence_isTrimmed_doesNotInflateLongestPause() {
        // 1s nói thật | 8s im lặng | tiếng click 2 khung (~0,04s) ở cuối bài.
        SpeechMeter.Measurement m = SpeechMeter.measure(wav(1, -8, 0.04)).orElseThrow();
        assertThat(m.longestPauseSec()).isLessThan(1.0);
    }

    @Test
    void quietHissBelowSpeechThreshold_correctlyReportsNoSpeech() {
        // Biên độ rất nhỏ (peak < 0.01) nhưng vẫn liên tục/đồng nhất — không phải tiếng nói, không được báo có.
        short[] samples = new short[RATE];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (50 * Math.sin(2 * Math.PI * 300 * i / RATE));
        }
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (short v : samples) {
            pcm.write(v & 0xFF);
            pcm.write((v >> 8) & 0xFF);
        }
        byte[] data = pcm.toByteArray();
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36 + data.length).put("WAVE".getBytes());
        h.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2)
                .putShort((short) 2).putShort((short) 16);
        h.put("data".getBytes()).putInt(data.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(h.array(), 0, 44);
        out.write(data, 0, data.length);
        SpeechMeter.Measurement m = SpeechMeter.measure(out.toByteArray()).orElseThrow();
        assertThat(m.hasSpeech()).isFalse();
    }
}
