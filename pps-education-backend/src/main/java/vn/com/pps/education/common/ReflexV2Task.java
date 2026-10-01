package vn.com.pps.education.common;

import vn.com.pps.education.domain.Curriculum;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — cấu hình từng dạng bài của bộ tiêu chí
 * Speaking v2 (Khối 6-9; dạng miêu tả ảnh PICTURE của Khối 7 Cambridge CHƯA làm ở đợt này) do người
 * training bàn giao — mirror {@code tasks.js} trong gói {@code ma-nguon-tham-chieu/}. Mỗi dạng bài chọn
 * theo (Khối, chương trình) của Curriculum, cùng nguyên tắc {@code RubricByGradeTrackLoader}: tổ hợp chưa
 * có bộ v2 (Khối 9 Cambridge, Khối 7-9 thiếu track) trả {@link Optional#empty()} — caller dùng luồng cũ.
 *
 * <b>Dạng đề SHORT / PART2 (Khối 8-9 IELTS):</b> hai dạng dùng CHUNG một cặp file rubric, chỉ khác cột
 * ngưỡng, và người training yêu cầu BẮT BUỘC truyền cột đúng — thiếu thì AI mặc định cột SHORT và chấm sai
 * toàn bộ checkpoint dạng đếm của bài 90 giây ({@code HANDOFF-grade8.md} §1). Hệ thống chưa có trường
 * "dạng đề" trên câu hỏi, nên SUY RA từ thời lượng ghi âm tối đa của câu hỏi — người training quy định cố
 * định SHORT = 30 giây, PART2 = 90 giây (khối 9 từ v3: 120): từ {@link #PART2_MIN_SECONDS} giây trở lên là PART2. Giả định này
 * cần người dùng xác nhận; nếu sau này thêm cột "dạng đề" thì thay đúng chỗ {@link #forGradeTrack}.
 *
 * @param criteria        các tiêu chí của bài NÓI, theo thứ tự (gồm cả tiêu chí Ngữ pháp).
 * @param writingCriteria các tiêu chí chấm ở Bước 1 (bài viết).
 * @param grammarCode     tiêu chí Ngữ pháp — bài nói giữ điểm Bước 1 khi nói giống bài viết, nói khác thì chấm
 *                        lại từ transcript (cách B, xem {@link ReflexV2Scoring#sameAsWritten}).
 * @param seconds         thời lượng ghi âm tối đa mà mọi ngưỡng của rubric được hiệu chuẩn theo.
 * @param minWords        ngưỡng số từ của cổng "quá ngắn/không đủ dữ liệu" (dưới ngưỡng → trần 40%).
 * @param gateScheme      ý nghĩa của mã cổng C1/C2/C3 — xem {@link GateScheme}.
 * @param rubricFormat    cột ngưỡng bắt buộc truyền vào prompt: {@code "SHORT"}/{@code "PART2"} (Khối 8-9 IELTS,
 *                        Khối 7 từ v3), {@code "PET4"} (Khối 8 Cambridge từ v3); null nếu rubric không có cột theo dạng đề.
 * @param rubricVersion   {@link #RUBRIC_V2}/{@link #RUBRIC_V3} — quyết định thư mục rubric ({@link #rubricDir()}).
 */
public record ReflexV2Task(String id, List<String> criteria, List<String> writingCriteria, String grammarCode,
                           int seconds, int minWords, GateScheme gateScheme, String rubricFormat,
                           String levelLabel, String formatLabel, String writingRubricFile, String speakingRubricFile,
                           String rubricVersion) {

    /**
     * STANDARD (Khối 6, 7, 8 Cambridge): C1 = lạc đề, C2 = không nghe ra (chặn Phát âm), C3 = quá ngắn.
     * V3 (Khối 8-9 IELTS): C1 = không đủ dữ liệu (quá ngắn), C2 = lạc đề — KHÔNG có cổng "không nghe ra".
     * Cùng mã C1 nhưng hai nghĩa khác nhau, nên lời giải thích cổng chặn phải theo kiểu này.
     */
    public enum GateScheme { STANDARD, V3 }

    /**
     * Bộ rubric 21/9–26/9 (thư mục {@code rubrics-v2/}). Chỉ còn dùng để chấm NỐT bước nói của câu đã chấm viết
     * bằng v2 trước khi lên v3 — không dùng cho bài nộp mới.
     */
    public static final String RUBRIC_V2 = "v2";
    /**
     * Bộ rubric bàn giao 29/9 (thư mục {@code rubrics-v3/}, §C.2 của quy tắc chung đã sửa theo người dùng
     * 2026-09-29). Khác v2 ở cấu hình: rubric khối 7 (IELTS SHORT/PART2, Cambridge SHORT/PICTURE) và khối 8
     * Cambridge (PET4/PICTURE) đều có hai cột ngưỡng nên phải khai cột ({@code "SHORT"}/{@code "PET4"}); khối 9
     * Part 2 tối đa 120 giây (v2: 90).
     */
    public static final String RUBRIC_V3 = "v3";
    /** Version áp cho bài viết nộp mới; bước nói luôn dùng version đã lưu ở dòng tiến trình. */
    public static final String CURRENT_RUBRIC_VERSION = RUBRIC_V3;

    /** Ghi âm tối đa từ ngưỡng này trở lên coi là PART2 (SHORT = 30 giây, PART2 = 90/120 giây theo người training). */
    public static final int PART2_MIN_SECONDS = 60;

    /** Tiêu chí P (Phát âm) luôn là tiêu chí chấm ở Bước 2 — chưa được kiểm chứng đủ, xem {@code ReflexV2AiGradingService}. */
    public static final String PRONUNCIATION_CODE = "P";

    private static final List<String> IELTS_SPEAKING = List.of("FC", "LR", "GRA", "P");
    private static final List<String> IELTS_WRITING = List.of("LR", "GRA");
    private static final List<String> CAMBRIDGE_SPEAKING = List.of("GV", "DM", "P");
    private static final List<String> CAMBRIDGE_WRITING = List.of("GV");

    private static final ReflexV2Task GRADE_6 = new ReflexV2Task(
            "g6-short", List.of("GV", "P"), CAMBRIDGE_WRITING, "GV", 20, 8, GateScheme.STANDARD, null,
            "CEFR A1–A2, học sinh 11–12 tuổi", "Short question",
            "rubric-grade6-writing.md", "rubric-grade6-speaking.md", RUBRIC_V3);

    private static final ReflexV2Task GRADE_7_IELTS = new ReflexV2Task(
            "g7-ielts-short", IELTS_SPEAKING, IELTS_WRITING, "GRA", 25, 15, GateScheme.STANDARD, "SHORT",
            "CEFR A2–B1 (IELTS 3.5–4.0), học sinh 12–13 tuổi", "Short question",
            "rubric-grade7-ielts-writing.md", "rubric-grade7-ielts-speaking.md", RUBRIC_V3);

    private static final ReflexV2Task GRADE_7_CAMBRIDGE = new ReflexV2Task(
            "g7-cam-short", CAMBRIDGE_SPEAKING, CAMBRIDGE_WRITING, "GV", 25, 15, GateScheme.STANDARD, "SHORT",
            "CEFR A2–B1, học sinh 12–13 tuổi", "Short question",
            "rubric-grade7-cambridge-writing.md", "rubric-grade7-cambridge-speaking.md", RUBRIC_V3);

    private static final ReflexV2Task GRADE_8_IELTS_SHORT = new ReflexV2Task(
            "g8-ielts-short", IELTS_SPEAKING, IELTS_WRITING, "GRA", 30, 15, GateScheme.V3, "SHORT",
            "IELTS 4.0 foundation, học sinh 13–14 tuổi", "Short question",
            "rubric-grade8-ielts-writing.md", "rubric-grade8-ielts-speaking.md", RUBRIC_V3);

    private static final ReflexV2Task GRADE_8_IELTS_PART2 = new ReflexV2Task(
            "g8-ielts-part2", IELTS_SPEAKING, IELTS_WRITING, "GRA", 90, 30, GateScheme.V3, "PART2",
            "IELTS 4.0 foundation, học sinh 13–14 tuổi", "IELTS Speaking Part 2",
            "rubric-grade8-ielts-writing.md", "rubric-grade8-ielts-speaking.md", RUBRIC_V3);

    private static final ReflexV2Task GRADE_8_CAMBRIDGE = new ReflexV2Task(
            "g8-cam-pet4", CAMBRIDGE_SPEAKING, CAMBRIDGE_WRITING, "GV", 60, 30, GateScheme.STANDARD, "PET4",
            "IELTS 4.0 (≈ CEFR A2+/B1), học sinh 13–14 tuổi", "PET Speaking Part 4",
            "rubric-grade8-cambridge-writing.md", "rubric-grade8-cambridge-speaking.md", RUBRIC_V3);

    private static final ReflexV2Task GRADE_9_IELTS_SHORT = new ReflexV2Task(
            "g9-ielts-short", IELTS_SPEAKING, IELTS_WRITING, "GRA", 30, 18, GateScheme.V3, "SHORT",
            "IELTS 4.0–5.0 foundation, học sinh 14–15 tuổi", "Short question",
            "rubric-grade9-ielts-writing.md", "rubric-grade9-ielts-speaking.md", RUBRIC_V3);

    private static final ReflexV2Task GRADE_9_IELTS_PART2 = new ReflexV2Task(
            "g9-ielts-part2", IELTS_SPEAKING, IELTS_WRITING, "GRA", 120, 35, GateScheme.V3, "PART2",
            "IELTS 4.0–5.0 foundation, học sinh 14–15 tuổi", "IELTS Speaking Part 2",
            "rubric-grade9-ielts-writing.md", "rubric-grade9-ielts-speaking.md", RUBRIC_V3);

    // ---- V200: dạng đề chỉ có từ v3 (chọn tường minh qua ReflexQuestionFormat, không suy được từ thời lượng) ----

    /** IELTS Part 2 Khối 7: chuẩn AREA/AEE — bốn câu ngắn đúng, đủ ý là đạt tối đa (rubric v3, 28/9). C3 PART2: <30 từ. */
    private static final ReflexV2Task GRADE_7_IELTS_PART2 = new ReflexV2Task(
            "g7-ielts-part2", IELTS_SPEAKING, IELTS_WRITING, "GRA", 60, 30, GateScheme.STANDARD, "PART2",
            "CEFR A2–B1 (IELTS 3.5–4.0), học sinh 12–13 tuổi", "IELTS Speaking Part 2",
            "rubric-grade7-ielts-writing.md", "rubric-grade7-ielts-speaking.md", RUBRIC_V3);

    /** Tả tranh Khối 7 Cambridge (PET Speaking Task 2). C3 PICTURE: <18 từ. */
    private static final ReflexV2Task GRADE_7_CAMBRIDGE_PICTURE = new ReflexV2Task(
            "g7-cam-pet2", CAMBRIDGE_SPEAKING, CAMBRIDGE_WRITING, "GV", 60, 18, GateScheme.STANDARD, "PICTURE",
            "CEFR A2–B1, học sinh 12–13 tuổi", "PET Speaking Task 2 (miêu tả ảnh)",
            "rubric-grade7-cambridge-writing.md", "rubric-grade7-cambridge-speaking.md", RUBRIC_V3);

    /** Tả tranh Khối 8 Cambridge — cột PICTURE bằng đúng Khối 7 (cùng khung AREA/AEE, rubric v3 29/9). C3 PICTURE: <18 từ. */
    private static final ReflexV2Task GRADE_8_CAMBRIDGE_PICTURE = new ReflexV2Task(
            "g8-cam-pet2", CAMBRIDGE_SPEAKING, CAMBRIDGE_WRITING, "GV", 60, 18, GateScheme.STANDARD, "PICTURE",
            "IELTS 4.0 (≈ CEFR A2+/B1), học sinh 13–14 tuổi", "PET Speaking Task 2 (miêu tả ảnh)",
            "rubric-grade8-cambridge-writing.md", "rubric-grade8-cambridge-speaking.md", RUBRIC_V3);

    /**
     * V200 — các dạng đề giáo viên được chọn cho khối/tuyến này, kèm dạng bài tương ứng (thứ tự = thứ tự hiện trên
     * dropdown, dạng đầu tiên là mặc định). Rỗng = chương trình chưa có bộ tiêu chí (Khối 9 Cambridge, thiếu
     * khối/tuyến) → câu hỏi chấm bằng luồng cũ, không chọn dạng đề.
     */
    public static Map<ReflexQuestionFormat, ReflexV2Task> allowedFormats(Curriculum.GradeLevel gradeLevel, Curriculum.Track track) {
        Map<ReflexQuestionFormat, ReflexV2Task> m = new LinkedHashMap<>();
        if (gradeLevel == null) {
            return m;
        }
        switch (gradeLevel) {
            case GRADE_6 -> m.put(ReflexQuestionFormat.SHORT, GRADE_6);
            case GRADE_7 -> {
                if (track == Curriculum.Track.IELTS) {
                    m.put(ReflexQuestionFormat.SHORT, GRADE_7_IELTS);
                    m.put(ReflexQuestionFormat.PART2, GRADE_7_IELTS_PART2);
                } else if (track == Curriculum.Track.CAMBRIDGE) {
                    m.put(ReflexQuestionFormat.SHORT, GRADE_7_CAMBRIDGE);
                    m.put(ReflexQuestionFormat.PICTURE, GRADE_7_CAMBRIDGE_PICTURE);
                }
            }
            case GRADE_8 -> {
                if (track == Curriculum.Track.IELTS) {
                    m.put(ReflexQuestionFormat.SHORT, GRADE_8_IELTS_SHORT);
                    m.put(ReflexQuestionFormat.PART2, GRADE_8_IELTS_PART2);
                } else if (track == Curriculum.Track.CAMBRIDGE) {
                    m.put(ReflexQuestionFormat.PET4, GRADE_8_CAMBRIDGE);
                    m.put(ReflexQuestionFormat.PICTURE, GRADE_8_CAMBRIDGE_PICTURE);
                }
            }
            case GRADE_9 -> {
                if (track == Curriculum.Track.IELTS) {
                    m.put(ReflexQuestionFormat.SHORT, GRADE_9_IELTS_SHORT);
                    m.put(ReflexQuestionFormat.PART2, GRADE_9_IELTS_PART2);
                }
            }
            default -> {
            }
        }
        return m;
    }

    /**
     * V200 — dạng bài của 1 câu hỏi. Có dạng đề (giáo viên chọn, hợp lệ với khối/tuyến) và version v3 → lấy đúng
     * dạng đó. Không có dạng đề (câu hỏi cũ), dạng đề không hợp lệ, hoặc câu dở dang bằng v2 (bộ v2 không có dạng
     * đề mới) → suy theo thời lượng như trước.
     */
    public static Optional<ReflexV2Task> forQuestion(Curriculum.GradeLevel gradeLevel, Curriculum.Track track,
                                                     int maxRecordingSeconds, ReflexQuestionFormat format, String rubricVersion) {
        if (format != null && RUBRIC_V3.equals(rubricVersion)) {
            ReflexV2Task chosen = allowedFormats(gradeLevel, track).get(format);
            if (chosen != null) {
                return Optional.of(chosen);
            }
        }
        return forGradeTrack(gradeLevel, track, maxRecordingSeconds, rubricVersion);
    }

    public static boolean isSupportedVersion(String rubricVersion) {
        return RUBRIC_V2.equals(rubricVersion) || RUBRIC_V3.equals(rubricVersion);
    }

    /** Khối (6-9), đọc từ mã dạng bài ({@code "g8-cam-pet4"} → 8). */
    public int grade() {
        return Character.getNumericValue(id.charAt(1));
    }

    /** Thư mục classpath chứa bộ rubric của dạng bài này. */
    public String rubricDir() {
        return "rubrics-" + rubricVersion + "/";
    }

    /** Dạng bài theo version hiện hành ({@link #CURRENT_RUBRIC_VERSION}) — dùng cho bài viết nộp mới. */
    public static Optional<ReflexV2Task> forGradeTrack(Curriculum.GradeLevel gradeLevel, Curriculum.Track track,
                                                       int maxRecordingSeconds) {
        return forGradeTrack(gradeLevel, track, maxRecordingSeconds, CURRENT_RUBRIC_VERSION);
    }

    /**
     * @param maxRecordingSeconds thời lượng ghi âm tối đa của câu hỏi — CHỈ dùng để phân biệt SHORT/PART2 ở
     *                            Khối 8-9 IELTS (xem Javadoc lớp); các dạng còn lại bỏ qua.
     * @param rubricVersion       {@link #RUBRIC_V2} hoặc {@link #RUBRIC_V3}; version khác → {@link Optional#empty()}.
     */
    public static Optional<ReflexV2Task> forGradeTrack(Curriculum.GradeLevel gradeLevel, Curriculum.Track track,
                                                       int maxRecordingSeconds, String rubricVersion) {
        if (!isSupportedVersion(rubricVersion)) {
            return Optional.empty();
        }
        return forGradeTrackV3(gradeLevel, track, maxRecordingSeconds)
                .map(t -> RUBRIC_V2.equals(rubricVersion) ? t.asV2() : t);
    }

    /** Cấu hình v2 = v3 trừ các chỗ khác nêu ở {@link #RUBRIC_V3}. */
    private ReflexV2Task asV2() {
        String format = id.equals("g8-cam-pet4") || id.startsWith("g7-") ? null : rubricFormat;
        int maxSeconds = id.equals("g9-ielts-part2") ? 90 : seconds;
        return new ReflexV2Task(id, criteria, writingCriteria, grammarCode, maxSeconds, minWords, gateScheme, format,
                levelLabel, formatLabel, writingRubricFile, speakingRubricFile, RUBRIC_V2);
    }

    private static Optional<ReflexV2Task> forGradeTrackV3(Curriculum.GradeLevel gradeLevel, Curriculum.Track track,
                                                          int maxRecordingSeconds) {
        if (gradeLevel == null) {
            return Optional.empty();
        }
        boolean part2 = maxRecordingSeconds >= PART2_MIN_SECONDS;
        switch (gradeLevel) {
            case GRADE_6:
                return Optional.of(GRADE_6);
            case GRADE_7:
                if (track == Curriculum.Track.IELTS) {
                    return Optional.of(GRADE_7_IELTS);
                }
                return track == Curriculum.Track.CAMBRIDGE ? Optional.of(GRADE_7_CAMBRIDGE) : Optional.empty();
            case GRADE_8:
                if (track == Curriculum.Track.IELTS) {
                    return Optional.of(part2 ? GRADE_8_IELTS_PART2 : GRADE_8_IELTS_SHORT);
                }
                return track == Curriculum.Track.CAMBRIDGE ? Optional.of(GRADE_8_CAMBRIDGE) : Optional.empty();
            case GRADE_9:
                // Khối 9 CAMBRIDGE: người training chưa bàn giao bộ tiêu chí → luồng cũ.
                return track == Curriculum.Track.IELTS
                        ? Optional.of(part2 ? GRADE_9_IELTS_PART2 : GRADE_9_IELTS_SHORT) : Optional.empty();
            default:
                return Optional.empty();
        }
    }
}
