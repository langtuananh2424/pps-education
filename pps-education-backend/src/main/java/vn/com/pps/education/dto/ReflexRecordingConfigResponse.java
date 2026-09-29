package vn.com.pps.education.dto;

/**
 * V199 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b: cấu hình màn ghi âm của học sinh.
 *
 * @param filterEnabled true → lọc bản ghi (90–7800 Hz + dìm tiếng nền −7 dB) rồi nộp WAV; false → nộp bản ghi thô.
 */
public record ReflexRecordingConfigResponse(boolean filterEnabled) {
}
