package vn.com.pps.education.common.pronunciation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Kết quả phân tích một bài nói, đầu vào của {@link PpsHybridScorer}. Tên trường JSON dạng snake_case, đúng khung kết quả của
 * lab và của {@code speaking-engine} (điểm GOPT thô, IPA nghe được, chuỗi từ sau khi ghép bài viết với lời nói).
 *
 * @param words          từ mục tiêu theo thứ tự, mỗi từ kèm điểm IPA và trạng thái so với lời STT nghe được
 * @param wordTimestamps mốc thời gian từng từ của STT
 * @param writing        thông tin ghép bài viết (null nếu không có bài viết)
 * @param gopt           kết quả Kaldi + GOPT (null nếu GOPT tắt)
 * @param noSpeech       true nếu không có tiếng nói để chấm
 * @param mode           chế độ chấm; {@link #MODE_UNSCRIPTED} là bài nói tự do
 * @param whisperTranscript transcript của STT (bài nói tự do chấm trên chữ này)
 * @param audioSeconds   độ dài file âm thanh (giây)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PronunciationInput(List<Word> words, List<Timestamp> wordTimestamps, Writing writing, Scores scores,
                                 Gopt gopt, Boolean noSpeech, String mode, String whisperTranscript, Double audioSeconds) {

    /** Chế độ của bài nói tự do (không có bài viết mẫu), xem {@link SpontaneousScorer}. */
    public static final String MODE_UNSCRIPTED = "unscripted_8_9";

    /** Bài chấm phát âm thuần, không cần transcript / thời lượng (dùng cho {@link PpsHybridScorer}). */
    public PronunciationInput(List<Word> words, List<Timestamp> wordTimestamps, Writing writing, Scores scores,
                              Gopt gopt, Boolean noSpeech) {
        this(words, wordTimestamps, writing, scores, gopt, noSpeech, null, null, null);
    }

    /** whisper: ok / diff / extra / missed / unverified; score: điểm IPA 0-100. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Word(String word, String expectedIpa, String heardIpa, List<String> issues, Integer score, String whisper) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Timestamp(String word, double start, double end) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Writing(Integer nWriting, Integer nOmitted, Integer nExpanded, Boolean fallback) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Scores(Double fluencyHeuristic) {
    }

    /** error khác null: Kaldi không gióng hàng được. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Gopt(String error, GoptUtterance utterance, List<GoptWord> words) {
        boolean isEmpty() {
            return error == null && utterance == null && words == null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GoptUtterance(double accuracy, double fluency, double prosodic) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GoptWord(Double stress, List<GoptPhone> phones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GoptPhone(String p, double s) {
    }
}
