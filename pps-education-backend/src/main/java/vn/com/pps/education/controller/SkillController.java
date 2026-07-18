package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateSkillRequest;
import vn.com.pps.education.dto.SkillResponse;
import vn.com.pps.education.dto.UpdateSkillRequest;
import vn.com.pps.education.service.SkillService;

import java.util.List;

/** UC-54: Quản lý danh mục kỹ năng (FR-ACA-06) — xem Javadoc SkillService. */
@RestController
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping("/api/skills")
    public ResponseEntity<List<SkillResponse>> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(skillService.list(includeInactive));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.manage')")
    @PostMapping("/api/skills")
    public ResponseEntity<SkillResponse> create(@Valid @RequestBody CreateSkillRequest request) {
        return ResponseEntity.ok(skillService.create(request));
    }

    @PreAuthorize("hasPermission(null, 'academic.grade.manage')")
    @PutMapping("/api/skills/{id}")
    public ResponseEntity<SkillResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateSkillRequest request) {
        return ResponseEntity.ok(skillService.update(id, request));
    }
}
