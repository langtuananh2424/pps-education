package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chế độ shadow: khi điểm Phát âm vẫn lấy từ Azure, chạy thêm GOPT ở nền trên cùng audio và ghi MỘT dòng log so hai bên để hiệu chỉnh
 * trước khi bật {@code PRONUNCIATION_PROVIDER=gopt}. Không ảnh hưởng điểm của học viên.
 *
 * <p>Tìm dòng log: {@code docker logs pps-staging-backend-1 | grep PRONUNCIATION_SHADOW}; phần sau tiền tố là JSON một dòng.
 */
final class PronunciationShadow {

    static final String PREFIX = "PRONUNCIATION_SHADOW ";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PronunciationShadow() {
    }

    /** Dòng log (không gồm tiền tố) so Azure với GOPT; GOPT lỗi thì ghi lý do thay vì điểm. */
    static String compare(AzurePronunciationClient.Result azure, AzurePronunciationClient.Result gopt, String transcript) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("words", transcript == null || transcript.isBlank() ? 0 : transcript.trim().split("\\s+").length);
        out.put("azure", scores(azure));
        if (gopt != null && gopt.hasScores()) {
            out.put("gopt", scores(gopt));
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("accuracy", diff(gopt.accuracyScore(), azure.accuracyScore()));
            delta.put("fluency", diff(gopt.fluencyScore(), azure.fluencyScore()));
            delta.put("completeness", diff(gopt.completenessScore(), azure.completenessScore()));
            delta.put("prosody", diff(gopt.prosodyScore(), azure.prosodyScore()));
            delta.put("overall", diff(gopt.overallScore(), azure.overallScore()));
            out.put("delta_gopt_minus_azure", delta);
        } else {
            out.put("gopt", Map.of("error", gopt == null ? "null" : String.valueOf(gopt.rawJson())));
        }
        try {
            return MAPPER.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"không ghi được JSON so sánh\"}";
        }
    }

    private static Map<String, Object> scores(AzurePronunciationClient.Result r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accuracy", r.accuracyScore());
        m.put("fluency", r.fluencyScore());
        m.put("completeness", r.completenessScore());
        m.put("prosody", r.prosodyScore());
        m.put("overall", r.overallScore());
        return m;
    }

    private static Integer diff(Integer a, Integer b) {
        return a == null || b == null ? null : a - b;
    }
}
