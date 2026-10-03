package vn.com.pps.education.lms.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.lms.dto.CaptureReflexPictureRequest;
import vn.com.pps.education.lms.dto.DraftReflexPictureBriefRequest;
import vn.com.pps.education.lms.dto.ReflexPictureBriefResponse;
import vn.com.pps.education.lms.dto.ReflexPictureCaptureResponse;
import vn.com.pps.education.lms.dto.ReflexQuestionFormatOptionResponse;
import vn.com.pps.education.lms.service.ReflexPictureService;
import vn.com.pps.education.lms.service.ReflexQuestionFormatService;

import java.util.List;

/**
 * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b: dạng đề của câu hỏi Video phản xạ và
 * hỗ trợ soạn mô tả tranh (dạng tả tranh). Cùng quyền với thêm/sửa câu hỏi ({@code lms.review-video.update}).
 */
@RestController
public class ReflexQuestionFormatController {

    private final ReflexQuestionFormatService reflexQuestionFormatService;
    private final ReflexPictureService reflexPictureService;

    public ReflexQuestionFormatController(ReflexQuestionFormatService reflexQuestionFormatService,
                                          ReflexPictureService reflexPictureService) {
        this.reflexQuestionFormatService = reflexQuestionFormatService;
        this.reflexPictureService = reflexPictureService;
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @GetMapping("/api/reflex-question-formats")
    public ResponseEntity<List<ReflexQuestionFormatOptionResponse>> formats(@RequestParam Long curriculumId) {
        return ResponseEntity.ok(reflexQuestionFormatService.optionsForCurriculum(curriculumId));
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PostMapping("/api/reflex-picture/capture")
    public ResponseEntity<ReflexPictureCaptureResponse> capture(@Valid @RequestBody CaptureReflexPictureRequest request) {
        return ResponseEntity.ok(reflexPictureService.captureFrame(request.videoUrl(), request.timestampSeconds()));
    }

    @PreAuthorize("hasPermission(null, 'lms.review-video.update')")
    @PostMapping("/api/reflex-picture/brief")
    public ResponseEntity<ReflexPictureBriefResponse> draftBrief(@Valid @RequestBody DraftReflexPictureBriefRequest request) {
        return ResponseEntity.ok(reflexPictureService.draftBrief(request.imageUrl()));
    }
}
