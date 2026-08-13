package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.CreateWorkCalendarRequest;
import vn.com.pps.education.dto.WorkCalendarResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.WorkCalendarService;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-70: Lịch làm việc/nghỉ lễ (work_calendar) — xem Javadoc WorkCalendarService.
 */
@RestController
@RequestMapping("/api/work-calendar")
public class WorkCalendarController {

    private final WorkCalendarService workCalendarService;

    public WorkCalendarController(WorkCalendarService workCalendarService) {
        this.workCalendarService = workCalendarService;
    }

    @GetMapping
    public ResponseEntity<List<WorkCalendarResponse>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(workCalendarService.listByDateRange(from, to));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'hrm.work-calendar.create')")
    public ResponseEntity<WorkCalendarResponse> create(@Valid @RequestBody CreateWorkCalendarRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(workCalendarService.createOverride(request, actor.userId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'hrm.work-calendar.delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workCalendarService.deleteOverride(id);
        return ResponseEntity.noContent().build();
    }
}
