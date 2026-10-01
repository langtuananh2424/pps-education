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
 *
 * V204 (bản bàn giao 30/9, mirror bản sửa cùng ngày của {@code audio.js}) — bản ghi ồn (tín/tạp dưới ~9,5
 * dB) khiến ngưỡng "có tiếng nói" ({@code noise * 3}) tự vượt qua cả biên độ đỉnh, nên KHÔNG khung nào qua
 * được ngưỡng và {@code hasSpeech} báo sai thành {@code false} dù bản ghi nghe rõ tiếng nói — học sinh bị
 * bắt ghi âm lại oan, transcript hợp lệ bị xoá trắng. Sửa bằng 2 lớp: (1) thử lọc còn dải giọng nói
 * (250–3500 Hz) khi toàn dải không tách nổi, vì ồn phòng học thường dồn năng lượng dưới 250 Hz; (2) khi
 * vẫn không tách nổi, không còn tin được "số khung vượt ngưỡng" nữa — chỉ còn dám khẳng định có/không
 * tiếng nói qua biên độ đỉnh đơn thuần, nhường quyền quyết cho lượt phiên âm (nghe được thì chấm, không
 * thì transcript rỗng — không tự kết luận "im lặng" khi thực ra không đo được).
 */
public final class SpeechMeter {

    private SpeechMeter() {
    }

    /** Khung phân tích 20 ms. */
    private static final double FRAME_SEC = 0.02;
    /** Khoảng nghỉ ≥ 1,5 giây không tính vào thời gian nói (spanSec). */
    private static final int GAP_FRAMES = (int) Math.round(1.5 / FRAME_SEC);
    /** Tín/tạp (tỉ số peak/noise, tuyến tính) dưới mốc này (~9,5 dB) là "không tách nổi tiếng nói khỏi nền". */
    private static final double LOW_SNR_RATIO = 3.0;
    /** Lọc còn dải giọng nói (one-pole 250 Hz cao / 3500 Hz thấp) — dự phòng khi toàn dải không tách nổi. */
    private static final double HIGHPASS_HZ = 250;
    private static final double LOWPASS_HZ = 3500;

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

    private record Stats(double noise, double peak, double snr) {
    }

    private static Stats stats(double[] rms) {
        double[] sorted = rms.clone();
        Arrays.sort(sorted);
        double noise = sorted[(int) Math.floor(sorted.length * 0.2)];
        double peak = sorted[(int) Math.floor(sorted.length * 0.98)];
        return new Stats(noise, peak, noise > 0 ? peak / noise : Double.POSITIVE_INFINITY);
    }

    /**
     * Năng lượng RMS từng khung 20ms. {@code band=true} lọc còn dải giọng nói (một cặp bộ lọc một cực,
     * trạng thái lọc chạy LIÊN TỤC qua mọi khung — không reset theo từng khung) thay vì toàn dải.
     */
    private static double[] frameRms(byte[] data, int offset, int frameSamples, int n, int sampleRate, boolean band) {
        double[] out = new double[n];
        double dt = 1.0 / sampleRate;
        double rcHi = 1.0 / (2 * Math.PI * HIGHPASS_HZ);
        double aHi = rcHi / (rcHi + dt);
        double rcLo = 1.0 / (2 * Math.PI * LOWPASS_HZ);
        double aLo = dt / (rcLo + dt);
        double prevIn = 0;
        double prevHi = 0;
        double lo = 0;
        for (int f = 0; f < n; f++) {
            double sum = 0;
            int base = offset + f * frameSamples * 2;
            for (int j = 0; j < frameSamples; j++) {
                int p = base + j * 2;
                short raw = (short) ((data[p] & 0xFF) | (data[p + 1] << 8));
                double s = raw / 32768.0;
                double v = s;
                if (band) {
                    double hi = aHi * (prevHi + s - prevIn);
                    prevIn = s;
                    prevHi = hi;
                    lo += aLo * (hi - lo);
                    v = lo;
                }
                sum += v * v;
            }
            out[f] = Math.sqrt(sum / frameSamples);
        }
        return out;
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
        double[] rms = frameRms(wav.data(), wav.offset(), frameSamples, n, wav.sampleRate(), false);
        Stats st = stats(rms);
        // 30/9/2026 — DỰ PHÒNG CHO BẢN GHI ỒN, chỉ chạy khi cách đo toàn dải đã thất bại (tín/tạp < 9,5 dB).
        // Ồn phòng học (quạt, điều hoà, xe) dồn năng lượng xuống dưới 250 Hz, thổi nền nhiễu lên và làm sập
        // phép tách tiếng nói; lọc còn dải giọng nói thì tín/tạp khá lên. Bản ghi sạch đi đúng đường cũ, số
        // đo không đổi một li nào (chỉ đổi nhánh khi điều kiện lowSnr xảy ra).
        if (st.snr() < LOW_SNR_RATIO) {
            double[] banded = frameRms(wav.data(), wav.offset(), frameSamples, n, wav.sampleRate(), true);
            Stats bandedSt = stats(banded);
            if (bandedSt.snr() > st.snr()) {
                rms = banded;
                st = bandedSt;
            }
        }
        double noise = st.noise();
        double peak = st.peak();
        // 30/9/2026 — SỬA LỖI CHẶN NHẦM BÀI CÓ TIẾNG NÓI: `noise * 3` vượt qua `peak` bất cứ khi nào tín/tạp
        // < 9,5 dB, và khi đó KHÔNG khung nào có thể vượt ngưỡng — trả hasSpeech=false oan cho bản ghi có
        // tiếng nói rõ ràng (đo trên bản ghi thật: tín/tạp 7,5 dB → ngưỡng cũ 0,2331 > đỉnh 0,1847 → 2/2481
        // khung "voiced"). Trần theo đỉnh (peak*0.6) để ngưỡng không bao giờ cao quá mức có thể đạt tới.
        boolean lowSnr = st.snr() < LOW_SNR_RATIO;
        // peak == 0 (im lặng số tuyệt đối, chỉ xảy ra với PCM toàn 0 — không phải audio thật) phải KHÔNG áp
        // trần: peak*0.6 = 0 sẽ kéo threshold xuống 0 và biến mọi khung im lặng thành "voiced" (0 >= 0).
        double threshold = Math.min(Math.max(Math.max(noise * 3, peak * 0.12), 0.008), peak > 0 ? peak * 0.6 : Double.MAX_VALUE);

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
        // Tín/tạp quá thấp thì phép đo KHÔNG phân biệt được "im lặng" với "không tách nổi" — gộp hai thứ đó
        // làm một là chặn nhầm học sinh, nên khi đó chỉ còn dám khẳng định có/không tiếng nói qua biên độ
        // đỉnh, nhường quyền quyết "có nói gì không" cho lượt phiên âm (nó nghe được thì chấm, không thì rỗng).
        boolean hasSpeech = lowSnr ? peak >= 0.01 : (voiced * FRAME_SEC >= 0.6 && peak >= 0.01);
        return Optional.of(new Measurement(durationSec, voiced * FRAME_SEC, spanSec, longestPauseSec, peak, noise, hasSpeech));
    }
}
