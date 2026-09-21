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
}
