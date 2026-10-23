package vn.com.pps.education.lms.service;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AzureAudioChunkerTest {

    private static byte[] wav(double seconds) {
        int length = (int) (seconds * AzureAudioChunker.BYTES_PER_SECOND);
        ByteBuffer b = ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + length).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(length);
        for (int i = 0; i < length; i++) {
            b.put((byte) (i % 100));
        }
        return b.array();
    }

    @Test
    void parseFindsDataChunkAndDuration() {
        AzureAudioChunker.Pcm pcm = AzureAudioChunker.parse(wav(120)).orElseThrow();
        assertThat(pcm.dataOffset()).isEqualTo(44);
        assertThat(pcm.durationSec()).isEqualTo(120.0);
        assertThat(AzureAudioChunker.parse(new byte[]{1, 2, 3})).isEmpty();
    }

    @Test
    void sliceProducesValidShortWav() {
        byte[] full = wav(120);
        AzureAudioChunker.Pcm pcm = AzureAudioChunker.parse(full).orElseThrow();
        byte[] part = AzureAudioChunker.slice(full, pcm, 10, 40);
        AzureAudioChunker.Pcm partPcm = AzureAudioChunker.parse(part).orElseThrow();
        assertThat(partPcm.durationSec()).isEqualTo(30.0);
        assertThat(part.length).isEqualTo(44 + 30 * AzureAudioChunker.BYTES_PER_SECOND);
        // dữ liệu đúng vị trí: byte đầu của đoạn = byte tại offset 10s của bản gốc
        assertThat(part[44]).isEqualTo(full[44 + 10 * AzureAudioChunker.BYTES_PER_SECOND]);
    }

    @Test
    void planGroupsSentencesIntoChunksUnderMaxAndKeepsText() {
        List<AzureAudioChunker.Segment> segs = List.of(
                new AzureAudioChunker.Segment(0, 20, "one"), new AzureAudioChunker.Segment(20, 40, "two"),
                new AzureAudioChunker.Segment(40, 60, "three"), new AzureAudioChunker.Segment(60, 80, "four"),
                new AzureAudioChunker.Segment(80, 100, "five"), new AzureAudioChunker.Segment(100, 118, "six"));
        List<AzureAudioChunker.Chunk> chunks = AzureAudioChunker.plan(segs, 120, 50).orElseThrow();
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).text()).isEqualTo("one two");
        assertThat(chunks.get(1).text()).isEqualTo("three four");
        assertThat(chunks.get(2).text()).isEqualTo("five six");
        assertThat(chunks).allSatisfy(c -> assertThat(c.durationSec()).isLessThanOrEqualTo(51));
        assertThat(chunks.get(2).endSec()).isLessThanOrEqualTo(120);
    }

    @Test
    void planRefusesWhenNoSegmentsOrSingleSentenceTooLong() {
        assertThat(AzureAudioChunker.plan(List.of(), 120, 50)).isEmpty();
        assertThat(AzureAudioChunker.plan(List.of(new AzureAudioChunker.Segment(0, 90, "x")), 120, 50)).isEmpty();
    }

    @Test
    void mergeAveragesByDuration() {
        AzurePronunciationClient.Result a = new AzurePronunciationClient.Result(80, 80, 100, 70, 80, List.of(), "{}");
        AzurePronunciationClient.Result b = new AzurePronunciationClient.Result(60, 60, 100, 50, 60, List.of(), "{}");
        List<AzureAudioChunker.Chunk> chunks = List.of(new AzureAudioChunker.Chunk(0, 30, "a"), new AzureAudioChunker.Chunk(30, 40, "b"));
        AzurePronunciationClient.Result m = AzurePronunciationClient.merge(List.of(a, b), chunks);
        assertThat(m.overallScore()).isEqualTo(75); // (80*30 + 60*10) / 40
        assertThat(m.chunkCount()).isEqualTo(2);
        assertThat(m.hasScores()).isTrue();
    }
}
