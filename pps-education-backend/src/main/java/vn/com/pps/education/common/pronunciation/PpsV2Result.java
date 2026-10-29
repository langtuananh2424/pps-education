package vn.com.pps.education.common.pronunciation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * Điểm PPS Hybrid v2 của một bài. Thang 0-10 (âm vị 0-2). {@code status} là {@code ok} hoặc {@code no_alignment}
 * (Kaldi không gióng hàng được: mọi điểm 0.0, không phải điểm đo được).
 *
 * <p>JSON giữ đúng tên trường của lab, gồm cả các trường phẳng {@code gopt_*_calibrated} theo mục 5 của đặc tả.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PpsV2Result(String mode, String status, String reason, int grade, double endingSoundPenalty,
                          Utterance utterance, List<Word> words, CalibrationConfig calibrationConfig) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Utterance(double accuracyCalibrated, double fluencyCalibrated, double prosodicCalibrated,
                            double completenessCalibrated, double totalCalibrated, double wordMeanAccuracyCalibrated) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Word(String word, String whisperStatus, double wordAccuracyCalibrated, double wordStressCalibrated,
                       double wordTotalCalibrated, boolean endingSoundError, List<Phone> phones) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Phone(String p, double goptPhoneScoreRaw, double phoneScoreCalibrated) {
    }

    /** Ghi vào cột calibration_config của file xuất: chế độ, khối và các tham số khác mặc định (rỗng nếu dùng mặc định). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CalibrationConfig(String mode, int grade, double endingSoundPenalty, Map<String, Double> changed) {
    }

    @JsonProperty("gopt_accuracy_calibrated")
    public double goptAccuracyCalibrated() {
        return utterance.accuracyCalibrated();
    }

    @JsonProperty("gopt_fluency_calibrated")
    public double goptFluencyCalibrated() {
        return utterance.fluencyCalibrated();
    }

    @JsonProperty("gopt_prosodic_calibrated")
    public double goptProsodicCalibrated() {
        return utterance.prosodicCalibrated();
    }

    @JsonProperty("gopt_completeness_calibrated")
    public double goptCompletenessCalibrated() {
        return utterance.completenessCalibrated();
    }

    @JsonProperty("gopt_total_calibrated")
    public double goptTotalCalibrated() {
        return utterance.totalCalibrated();
    }

    @JsonProperty("gopt_word_mean_accuracy_calibrated")
    public double goptWordMeanAccuracyCalibrated() {
        return utterance.wordMeanAccuracyCalibrated();
    }
}
