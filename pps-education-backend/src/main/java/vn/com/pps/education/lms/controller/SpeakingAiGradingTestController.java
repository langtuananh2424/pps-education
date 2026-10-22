package vn.com.pps.education.lms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.lms.dto.SpeakingAiGradingTestResponse;
import vn.com.pps.education.lms.dto.WritingAiGradingTestResponse;
import vn.com.pps.education.lms.dto.WritingRealRubricTestResponse;
import vn.com.pps.education.lms.dto.WritingReflexRealRubricTestResponse;
import vn.com.pps.education.lms.service.SpeakingAiGradingTestService;

/**
 * SPIKE/TEST riêng (2026-08-22, đã xác nhận với người dùng) — trang test đứng độc lập để đánh giá
 * khả thi kỹ thuật/chi phí hướng "AI chấm Speaking" (Video phản xạ V2), KHÔNG phải API chính thức
 * của 1 UC.
 *
 * Rà soát bảo mật 2026-09-28 (đã xác nhận với người dùng): trước đây chỉ yêu cầu đã đăng nhập — MỌI
 * tài khoản (kể cả Học sinh/Phụ huynh) gọi được, mỗi lần tốn chi phí gọi AI + lưu file lên storage.
 * Giới hạn cho quyền quản trị hệ thống (system.settings.manage, như AiTokenUsageController).
 */
@RestController
@PreAuthorize("hasPermission(null, 'system.settings.manage')")
public class SpeakingAiGradingTestController {

    private final SpeakingAiGradingTestService speakingAiGradingTestService;

    public SpeakingAiGradingTestController(SpeakingAiGradingTestService speakingAiGradingTestService) {
        this.speakingAiGradingTestService = speakingAiGradingTestService;
    }

    @PostMapping(value = "/api/dev-tools/speaking-grading-test", consumes = "multipart/form-data")
    public ResponseEntity<SpeakingAiGradingTestResponse> grade(@RequestParam("audio") MultipartFile audio,
                                                                 @RequestParam(value = "writingText", required = false) String writingText,
                                                                 @RequestParam(value = "provider", required = false) String provider,
                                                                 @RequestParam(value = "gradeLevel", required = false) String gradeLevel,
                                                                 @RequestParam(value = "track", required = false) String track,
                                                                 @RequestParam(value = "maxRecordingSeconds", required = false, defaultValue = "30") int maxRecordingSeconds,
                                                                 @RequestParam(value = "question", required = false) String question,
                                                                 @RequestParam(value = "step1GrammarPercent", required = false, defaultValue = "70") int step1GrammarPercent) {
        return ResponseEntity.ok(speakingAiGradingTestService.grade(audio, writingText, provider,
                gradeLevel, track, maxRecordingSeconds, question, step1GrammarPercent));
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — chấm Writing ĐỘC LẬP, không cần audio, xem Javadoc
     * {@code SpeakingAiGradingTestService#gradeWritingOnly}.
     */
    @PostMapping(value = "/api/dev-tools/writing-grading-test")
    public ResponseEntity<WritingAiGradingTestResponse> gradeWriting(@RequestParam(value = "essayPrompt", required = false) String essayPrompt,
                                                                       @RequestParam("writingText") String writingText,
                                                                       @RequestParam(value = "model", required = false) String model,
                                                                       @RequestParam(value = "gradeTrack", required = false) String gradeTrack) {
        return ResponseEntity.ok(speakingAiGradingTestService.gradeWritingOnly(essayPrompt, writingText, model, gradeTrack));
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — chấm bằng ĐÚNG rubric v3 thật (không phải
     * placeholder), xem Javadoc {@code SpeakingAiGradingTestService#gradeWritingRealRubric}.
     */
    @PostMapping(value = "/api/dev-tools/writing-grading-test-real-rubric")
    public ResponseEntity<WritingRealRubricTestResponse> gradeWritingRealRubric(@RequestParam(value = "essayPrompt", required = false) String essayPrompt,
                                                                                  @RequestParam("writingText") String writingText,
                                                                                  @RequestParam("gradeLevel") String gradeLevel,
                                                                                  @RequestParam(value = "track", required = false) String track,
                                                                                  @RequestParam(value = "model", required = false) String model) {
        return ResponseEntity.ok(speakingAiGradingTestService.gradeWritingRealRubric(essayPrompt, writingText, gradeLevel, track, model));
    }

    /**
     * Bổ sung 2026-10-22 (đã xác nhận với người dùng) — chấm bước VIẾT CÂU NGẮN bằng rubric checkpoint thật
     * UC-23b (ĐÚNG luồng Viết→Nói, KHÔNG phải Essay UC-40/41), xem Javadoc
     * {@code SpeakingAiGradingTestService#gradeWritingReflexRealRubric}.
     */
    @PostMapping(value = "/api/dev-tools/writing-grading-test-reflex-rubric")
    public ResponseEntity<WritingReflexRealRubricTestResponse> gradeWritingReflexRealRubric(
            @RequestParam(value = "question", required = false) String question,
            @RequestParam("writingText") String writingText,
            @RequestParam("gradeLevel") String gradeLevel,
            @RequestParam(value = "track", required = false) String track,
            @RequestParam(value = "maxRecordingSeconds", required = false, defaultValue = "30") int maxRecordingSeconds,
            @RequestParam(value = "model", required = false) String model) {
        return ResponseEntity.ok(speakingAiGradingTestService.gradeWritingReflexRealRubric(
                question, writingText, gradeLevel, track, maxRecordingSeconds, model));
    }
}
