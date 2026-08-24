package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.SpeakingAiGradingTestResponse;
import vn.com.pps.education.service.SpeakingAiGradingTestService;

/**
 * SPIKE/TEST riêng (2026-08-22, đã xác nhận với người dùng) — trang test đứng độc lập để đánh giá
 * khả thi kỹ thuật/chi phí hướng "AI chấm Speaking" (Video phản xạ V2), KHÔNG phải API chính thức
 * của 1 UC. Chỉ yêu cầu đã đăng nhập (anyRequest().authenticated() ở SecurityConfig, mirror
 * MediaController) — chưa cần gate permission riêng vì không ghi dữ liệu nghiệp vụ thật nào.
 */
@RestController
public class SpeakingAiGradingTestController {

    private final SpeakingAiGradingTestService speakingAiGradingTestService;

    public SpeakingAiGradingTestController(SpeakingAiGradingTestService speakingAiGradingTestService) {
        this.speakingAiGradingTestService = speakingAiGradingTestService;
    }

    @PostMapping(value = "/api/dev-tools/speaking-grading-test", consumes = "multipart/form-data")
    public ResponseEntity<SpeakingAiGradingTestResponse> grade(@RequestParam("audio") MultipartFile audio,
                                                                 @RequestParam(value = "writingText", required = false) String writingText,
                                                                 @RequestParam(value = "provider", required = false) String provider) {
        return ResponseEntity.ok(speakingAiGradingTestService.grade(audio, writingText, provider));
    }
}
