package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.domain.Exercise;
import vn.com.pps.education.dto.HomeworkSkillGroupResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.HomeworkSkillBatchService;

import java.util.List;

/** V150 — "Lô giao BTVN theo kỹ năng", xem Javadoc HomeworkSkillBatchService. */
@RestController
public class HomeworkSkillBatchController {

    private final HomeworkSkillBatchService homeworkSkillBatchService;

    public HomeworkSkillBatchController(HomeworkSkillBatchService homeworkSkillBatchService) {
        this.homeworkSkillBatchService = homeworkSkillBatchService;
    }

    /** Kho đề — nguồn cho dropdown "BTVN buổi sau" theo kỹ năng ở Nhận xét học viên (mirror ExerciseController#listPublishedForClass, 1 entry/Lesson thay vì 1 entry/Bài). */
    @GetMapping("/api/classes/{classId}/homework-skill-groups")
    public ResponseEntity<List<HomeworkSkillGroupResponse>> listSkillGroupsForClass(
            @PathVariable Long classId, @RequestParam String skillCategory,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(homeworkSkillBatchService.listSkillGroupsForClass(
                classId, Exercise.SkillCategory.valueOf(skillCategory), actor.userId()));
    }
}
