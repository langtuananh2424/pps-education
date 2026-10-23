package vn.com.pps.education.lms.service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bổ sung 2026-10-23 (đã xác nhận với người dùng) — Azure Pronunciation Assessment qua REST "short audio" chỉ nhận
 * audio ngắn (khoảng 60 giây), nên bài nói dài (Khối 8-9 Part 2 tới 120s) được cắt thành các đoạn ≤ maxChunkSec tại
 * RANH GIỚI CÂU theo mốc thời gian Whisper (Groq {@code segments}); mỗi đoạn có ReferenceText là đúng phần transcript
 * của nó. Kết quả từng đoạn được ghép lại ở {@link AzurePronunciationClient#assessSegmented}.
 *
 * Chỉ xử lý WAV PCM16 mono 16 kHz (đầu ra của {@link AudioTranscoder#toWav}).
 */
final class AzureAudioChunker {

    static final int SAMPLE_RATE = 16000;
    static final int BYTES_PER_SECOND = SAMPLE_RATE * 2;
    /** Đệm quanh đoạn để không cắt cụt âm đầu/cuối từ (Whisper hay báo mốc hơi sát). */
    private static final double PAD_BEFORE_SEC = 0.15;
    private static final double PAD_AFTER_SEC = 0.35;

    record Segment(double startSec, double endSec, String text) {
    }

    record Chunk(double startSec, double endSec, String text) {
        double durationSec() {
            return endSec - startSec;
        }
    }

    record Pcm(int dataOffset, int dataLength) {
        double durationSec() {
            return dataLength / (double) BYTES_PER_SECOND;
        }
    }

    private AzureAudioChunker() {
    }

    /** Tìm khối "data" của WAV; rỗng nếu không phải WAV PCM hợp lệ. */
    static Optional<Pcm> parse(byte[] wav) {
        if (wav == null || wav.length < 44 || !ascii(wav, 0, "RIFF") || !ascii(wav, 8, "WAVE")) {
            return Optional.empty();
        }
        ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        while (pos + 8 <= wav.length) {
            int size = buf.getInt(pos + 4);
            if (ascii(wav, pos, "data")) {
                // ffmpeg ghi stream ra file có thể để size = 0xFFFFFFFF/0: lấy phần còn lại của file.
                int available = wav.length - (pos + 8);
                int length = (size <= 0 || size > available) ? available : size;
                return length > 0 ? Optional.of(new Pcm(pos + 8, length)) : Optional.empty();
            }
            if (size < 0) {
                return Optional.empty();
            }
            pos += 8 + size + (size & 1);
        }
        return Optional.empty();
    }

    /** Cắt [startSec, endSec) thành WAV mới (header chuẩn 44 byte). */
    static byte[] slice(byte[] wav, Pcm pcm, double startSec, double endSec) {
        int from = alignedOffset(startSec, pcm.dataLength());
        int to = alignedOffset(endSec, pcm.dataLength());
        int length = Math.max(0, to - from);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + length);
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + length)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(SAMPLE_RATE).putInt(BYTES_PER_SECOND)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(length);
        out.writeBytes(header.array());
        out.write(wav, pcm.dataOffset() + from, length);
        return out.toByteArray();
    }

    /**
     * Gom các câu liên tiếp thành đoạn dài tối đa {@code maxChunkSec}. Rỗng nếu không chia được (không có segments,
     * hoặc có 1 câu dài hơn mức tối đa — không có mốc để cắt mà vẫn giữ đúng ReferenceText).
     */
    static Optional<List<Chunk>> plan(List<Segment> segments, double totalSec, double maxChunkSec) {
        if (segments == null || segments.isEmpty()) {
            return Optional.empty();
        }
        List<Chunk> chunks = new ArrayList<>();
        double groupStart = -1;
        double groupEnd = 0;
        StringBuilder text = new StringBuilder();
        for (Segment s : segments) {
            if (s.endSec() - s.startSec() > maxChunkSec) {
                return Optional.empty();
            }
            if (groupStart >= 0 && s.endSec() - groupStart > maxChunkSec) {
                chunks.add(chunk(groupStart, groupEnd, text, totalSec));
                groupStart = -1;
                text.setLength(0);
            }
            if (groupStart < 0) {
                groupStart = s.startSec();
            }
            groupEnd = s.endSec();
            text.append(text.isEmpty() ? "" : " ").append(s.text().trim());
        }
        if (groupStart >= 0) {
            chunks.add(chunk(groupStart, groupEnd, text, totalSec));
        }
        return chunks.stream().anyMatch(c -> c.durationSec() > maxChunkSec + PAD_BEFORE_SEC + PAD_AFTER_SEC + 0.01)
                ? Optional.empty() : Optional.of(chunks);
    }

    private static Chunk chunk(double start, double end, StringBuilder text, double totalSec) {
        return new Chunk(Math.max(0, start - PAD_BEFORE_SEC), Math.min(totalSec, end + PAD_AFTER_SEC), text.toString());
    }

    private static int alignedOffset(double sec, int dataLength) {
        long raw = Math.round(sec * BYTES_PER_SECOND);
        int clamped = (int) Math.max(0, Math.min(raw, dataLength));
        return clamped & ~1;
    }

    private static boolean ascii(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) {
            if (b[off + i] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
