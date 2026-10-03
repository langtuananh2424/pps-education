package vn.com.pps.education.lms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.lms.service.ExerciseQuestionExportService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-03) — xuất Excel toàn bộ câu hỏi của 1
 * Bài theo đúng định dạng file mẫu import, xem Javadoc ExerciseQuestionExportService. File chứa đáp án
 * đúng nên chỉ cấp cho người có quyền sửa Bài (cùng quyền với thao tác thêm/bớt câu hỏi).
 */
@RestController
public class ExerciseExportController {

    private final ExerciseQuestionExportService exportService;

    public ExerciseExportController(ExerciseQuestionExportService exportService) {
        this.exportService = exportService;
    }

    @PreAuthorize("hasPermission(null, 'lms.exercise.update')")
    @GetMapping("/api/exercises/{id}/questions/export.xlsx")
    public ResponseEntity<byte[]> exportQuestions(@PathVariable Long id) {
        ExerciseQuestionExportService.ExportedFile file = exportService.exportExercise(id);
        String encodedFilename = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Type", file.contentType())
                .header("Content-Disposition", "attachment; filename=\"" + file.filename() + "\"; filename*=UTF-8''" + encodedFilename)
                .body(file.content());
    }
}
