package vn.com.pps.education.common;

import java.util.Arrays;
import java.util.Optional;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — đo tiếng nói TRỰC TIẾP từ file WAV
 * (PCM 16-bit), không phụ thuộc AI; mirror {@code audio.js} trong {@code ma-nguon-tham-chieu/} do người
 * training bàn giao. Dùng để (1) chặn transcript bịa (số từ vượt tốc độ nói tối đa so với thời gian nói
 * thật), (2) cấp số giây nói thật và khoảng dừng dài nhất GIỮA hai đoạn có tiếng nói (quy tắc chung §A.7b
 * — im lặng đầu/cuối bài không bao giờ là khoảng dừng), thay số ước lượng của AI vốn gộp cả im lặng cuối bài.
 *
 * Chỉ đọc được WAV PCM16 — bản ghi trình duyệt (webm/opus, mp4) phải được chuyển sang WAV trước (xem
 * {@code AudioTranscoder}); không chuyển được thì {@link #measure} trả rỗng và caller PHẢI coi các bảo vệ
 * dựa trên số đo này là chưa chạy (KHÔNG phải "đạt").
 */
public final class SpeechMeter {

    private SpeechMeter() {
    }

    /** Khung phân tích 20 ms. */
    private static final double FRAME_SEC = 0.02;
    /** Khoảng nghỉ ≥ 1,5 giây không tính vào thời gian nói (spanSec). */
    private static final int GAP_FRAMES = (int) Math.round(1.5 / FRAME_SEC);

    public record Measurement(double durationSec, double speechSec, double spanSec, double longestPauseSec,
                              double peak, double noiseFloor, boolean hasSpeech) {
    }

    private record Wav(int channels, int sampleRate, int bits, byte[] data, int offset, int length) {
    }

    private static long u32(byte[] b, int p) {
        return (b[p] & 0xFFL) | ((b[p + 1] & 0xFFL) << 8) | ((b[p + 2] & 0xFFL) << 16) | ((b[p + 3] & 0xFFL) << 24);
    }

    private static int u16(byte[] b, int p) {
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8);
    }

    private static Wav parseWav(byte[] buf) {
        if (buf == null || buf.length < 44 || !ascii(buf, 0, "RIFF") || !ascii(buf, 8, "WAVE")) {
            return null;
        }
        int pos = 12;
        int channels = 0;
        int sampleRate = 0;
        int bits = 0;
        boolean haveFmt = false;
        while (pos + 8 <= buf.length) {
            String id = new String(buf, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long size = u32(buf, pos + 4);
            int body = pos + 8;
            if (id.equals("fmt ") && body + 16 <= buf.length) {
                channels = u16(buf, body + 2);
                sampleRate = (int) u32(buf, body + 4);
                bits = u16(buf, body + 14);
                haveFmt = true;
            } else if (id.equals("data") && haveFmt) {
                long end = Math.min(body + size, buf.length);
                return new Wav(channels, sampleRate, bits, buf, body, (int) (end - body));
            }
            long next = body + size + (size % 2);
            if (next > Integer.MAX_VALUE) {
                return null;
            }
            pos = (int) next;
        }
        return null;
    }

    private static boolean ascii(byte[] b, int p, String s) {
        for (int i = 0; i < s.length(); i++) {
            if (b[p + i] != (byte) s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** @return số đo, hoặc rỗng nếu không phải WAV PCM16 hợp lệ / quá ngắn để đo. */
    public static Optional<Measurement> measure(byte[] wavBytes) {
        Wav wav = parseWav(wavBytes);
        if (wav == null || wav.bits() != 16 || wav.channels() < 1 || wav.sampleRate() < 1) {
            return Optional.empty();
        }
        int frameSamples = Math.max(1, (int) Math.round(wav.sampleRate() * FRAME_SEC)) * wav.channels();
        int frameBytes = frameSamples * 2;
        int n = wav.length() / frameBytes;
        if (n < 1) {
            return Optional.empty();
        }
        double[] rms = new double[n];
        for (int f = 0; f < n; f++) {
            double sum = 0;
            int base = wav.offset() + f * frameBytes;
            for (int j = 0; j < frameSamples; j++) {
                int p = base + j * 2;
                short s = (short) ((wav.data()[p] & 0xFF) | (wav.data()[p + 1] << 8));
                double v = s / 32768.0;
                sum += v * v;
            }
            rms[f] = Math.sqrt(sum / frameSamples);
        }
        double[] sorted = rms.clone();
        Arrays.sort(sorted);
        double noise = sorted[(int) Math.floor(n * 0.2)];
        double peak = sorted[(int) Math.floor(n * 0.98)];
        double threshold = Math.max(Math.max(noise * 3, peak * 0.12), 0.008);

        int[] voicedIdx = new int[n];
        int voiced = 0;
        for (int i = 0; i < n; i++) {
            if (rms[i] >= threshold) {
                voicedIdx[voiced++] = i;
            }
        }

        double spanSec = 0;
        double longestPauseSec = 0;
        if (voiced > 1) {
            int gaps = 0;
            for (int i = 1; i < voiced; i++) {
                int d = voicedIdx[i] - voicedIdx[i - 1];
                if (d >= GAP_FRAMES) {
                    gaps += d;
                }
                // Khoảng dừng dài nhất chỉ tính GIỮA hai đoạn có tiếng nói: im lặng trước từ đầu tiên và
                // sau từ cuối cùng nằm ngoài vòng lặp này nên không bao giờ lọt vào.
                if (d * FRAME_SEC > longestPauseSec) {
                    longestPauseSec = d * FRAME_SEC;
                }
            }
            spanSec = (voicedIdx[voiced - 1] - voicedIdx[0] - gaps) * FRAME_SEC;
        }
        double durationSec = (double) (wav.length() / 2 / wav.channels()) / wav.sampleRate();
        return Optional.of(new Measurement(durationSec, voiced * FRAME_SEC, spanSec, longestPauseSec, peak, noise,
                voiced * FRAME_SEC >= 0.6 && peak >= 0.01));
    }
}
