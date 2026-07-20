package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.pps.education.dto.ClassScheduleImportResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ClassScheduleImportService;

/** UC-57: Nhập lịch học qua Excel (FR-ACA-05) — xem Javadoc ClassScheduleImportService. */
@RestController
public class ClassScheduleImportController {

    private final ClassScheduleImportService classScheduleImportService;

    public ClassScheduleImportController(ClassScheduleImportService classScheduleImportService) {
        this.classScheduleImportService = classScheduleImportService;
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @PostMapping(value = "/api/classes/{classId}/session-imports", consumes = "multipart/form-data")
    public ResponseEntity<ClassScheduleImportResponse> importSchedule(@PathVariable Long classId,
                                                                       @RequestParam("file") MultipartFile file,
                                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classScheduleImportService.importSchedule(classId, file, actor.userId()));
    }

    @PreAuthorize("hasPermission(null, 'academic.class.manage')")
    @GetMapping("/api/classes/{classId}/session-imports/{id}")
    public ResponseEntity<ClassScheduleImportResponse> getJob(@PathVariable Long classId, @PathVariable Long id) {
        return ResponseEntity.ok(classScheduleImportService.getJob(id));
    }
}
