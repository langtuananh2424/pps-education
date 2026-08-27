package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.Curriculum;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — rubric chấm AI (Speaking/Writing) do
 * giáo viên cung cấp khác nhau theo TỪNG Khối (6/7/8/9) VÀ chương trình (IELTS/CAMBRIDGE, xem
 * {@link Curriculum.GradeLevel}/{@link Curriculum.Track}) — mỗi tổ hợp là 1 file riêng dưới
 * {@code resources/rubrics/} (đặt tên {@code <filePrefix>-grade<6|7|8|9>-<ielts|cambridge|shared>.md}),
 * KHÔNG gộp thành 1 file lớn rồi bắt AI tự chọn đúng bảng (rủi ro chọn nhầm, tốn token). Dùng chung cho
 * cả Mục 1 (Video phản xạ: {@link ReflexWritingGrammarAiGradingService}/
 * {@link ReflexSpeakingContentAiGradingService}) và Mục 2 ({@link WritingAiGradingService}) — cùng 1
 * quy tắc chọn file, khác `filePrefix` truyền vào.
 *
 * Khối 6: 1 rubric DÙNG CHUNG cho cả 2 track (giáo viên xác nhận, file hậu tố "grade6-shared") — track
 * của Curriculum không cần set. Khối 7/8/9: BẮT BUỘC biết track mới chọn đúng file — trả về {@code null}
 * nếu thiếu gradeLevel, hoặc thiếu track ở Khối khác 6, hoặc không tìm thấy file tương ứng (VD Khối 9
 * CAMBRIDGE — giáo viên chưa cung cấp bảng này). Caller (3 service chấm AI) coi {@code null} là "chưa
 * chấm được", rơi lại hàng chờ chấm tay — KHÔNG tự đoán bừa dùng rubric của Khối/track khác (business
 * fidelity — xem .claude/rules/business-fidelity.md).
 */
@Component
public class RubricByGradeTrackLoader {

    private static final Logger log = LoggerFactory.getLogger(RubricByGradeTrackLoader.class);

    /** Cache theo đường dẫn classpath — chỉ đọc file mỗi tổ hợp 1 lần trong suốt vòng đời ứng dụng. */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * @param filePrefix VD "writing-rubric"/"speaking-rubric" — khớp tên file thật trong resources/rubrics/.
     * @return nội dung rubric của ĐÚNG 1 bảng Khối/track, hoặc {@code null} nếu chưa đủ dữ liệu để chọn
     *         (gradeLevel/track chưa được cấu hình ở Curriculum) hoặc không có file tương ứng.
     */
    public String load(String filePrefix, Curriculum.GradeLevel gradeLevel, Curriculum.Track track) {
        if (gradeLevel == null) {
            log.warn("RubricByGradeTrackLoader: curriculum chưa cấu hình gradeLevel — bỏ qua chấm AI.");
            return null;
        }
        String suffix;
        if (gradeLevel == Curriculum.GradeLevel.GRADE_6) {
            // Khối 6 dùng chung 1 rubric cho cả IELTS/CAMBRIDGE (giáo viên xác nhận) — track có thể null.
            suffix = "grade6-shared";
        } else {
            if (track == null) {
                log.warn("RubricByGradeTrackLoader: curriculum Khối {} chưa cấu hình track (IELTS/CAMBRIDGE) — bỏ qua chấm AI.", gradeLevel);
                return null;
            }
            String gradeNumber = gradeLevel.name().replace("GRADE_", "");
            suffix = "grade" + gradeNumber + "-" + track.name().toLowerCase(Locale.ROOT);
        }
        String classpath = "rubrics/" + filePrefix + "-" + suffix + ".md";
        // computeIfAbsent không cho lưu null trực tiếp (ném NPE) — cache "" khi đọc lỗi/không thấy file,
        // rồi chuẩn hoá "" -> null ở đây để caller CHỈ cần kiểm tra 1 điều kiện (== null) cho mọi lý do
        // "chưa chấm được" (thiếu phân loại HOẶC thiếu file), không phải phân biệt 2 trường hợp.
        String content = cache.computeIfAbsent(classpath, this::readClasspathFile);
        return content.isBlank() ? null : content;
    }

    private String readClasspathFile(String classpath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                log.warn("RubricByGradeTrackLoader: không tìm thấy {} trên classpath — giáo viên có thể chưa cung cấp bảng này.", classpath);
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("RubricByGradeTrackLoader: đọc {} thất bại. {}", classpath, e.getMessage());
            return "";
        }
    }
}
