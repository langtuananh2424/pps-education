package vn.com.pps.education.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AssignedExerciseResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CurriculumDocumentResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradePeriodResultResponse;
import vn.com.pps.education.dto.ListeningPracticeItemResponse;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.UpdateOwnStudentProfileRequest;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ClassSessionService;
import vn.com.pps.education.service.CurriculumDocumentService;
import vn.com.pps.education.service.ExerciseAttemptService;
import vn.com.pps.education.service.GradeService;
import vn.com.pps.education.service.ListeningPracticeService;
import vn.com.pps.education.service.StudentService;

import java.time.LocalDate;
import java.util.List;

/**
 * Gộp các endpoint tự-xem của Học sinh (UC-59, UC-60, UC-26, bổ sung
 * giao bài) — self-service, không cần permission đặc biệt, chỉ cần đăng
 * nhập + có hồ sơ Student (giống pattern TeacherScheduleController —
 * UC-58). Controller mỏng, mỗi method chỉ gọi 1 Service tương ứng.
 */
@RestController
@RequestMapping("/api/students/me")
public class StudentPortalController {

    private final ClassSessionService classSessionService;
    private final ExerciseAttemptService exerciseAttemptService;
    private final CurriculumDocumentService curriculumDocumentService;
    private final ListeningPracticeService listeningPracticeService;
    private final GradeService gradeService;
    private final StudentService studentService;

    public StudentPortalController(ClassSessionService classSessionService,
                                    ExerciseAttemptService exerciseAttemptService,
                                    CurriculumDocumentService curriculumDocumentService,
                                    ListeningPracticeService listeningPracticeService,
                                    GradeService gradeService,
                                    StudentService studentService) {
        this.classSessionService = classSessionService;
        this.exerciseAttemptService = exerciseAttemptService;
        this.curriculumDocumentService = curriculumDocumentService;
        this.listeningPracticeService = listeningPracticeService;
        this.gradeService = gradeService;
        this.studentService = studentService;
    }

    /** UC-63: học sinh tự xem hồ sơ của chính mình (FR-USR-07). */
    @GetMapping
    public ResponseEntity<StudentResponse> getMine(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(studentService.getMyStudentProfile(actor.userId()));
    }

    /** UC-63: học sinh tự cập nhật ảnh đại diện của chính mình (FR-USR-07). */
    @PutMapping
    public ResponseEntity<StudentResponse> updateMine(@AuthenticationPrincipal AuthenticatedUser actor,
                                                        @Valid @RequestBody UpdateOwnStudentProfileRequest request) {
        return ResponseEntity.ok(studentService.updateMyStudentProfile(actor.userId(), request));
    }

    /** UC-59: lịch học của tôi. */
    @GetMapping("/sessions")
    public ResponseEntity<List<ClassSessionResponse>> listMySessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(classSessionService.listMySessionsForStudent(actor.userId(), fromDate, toDate, classId));
    }

    /** Bổ sung: đề đã được giao cho (các) lớp tôi đang học. */
    @GetMapping("/exercises")
    public ResponseEntity<List<AssignedExerciseResponse>> listMyAssignedExercises(
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(exerciseAttemptService.listMyAssignedExercises(actor.userId(), classId));
    }

    /** UC-60: kho tài liệu tham khảo theo curriculum của (các) lớp tôi đang học. */
    @GetMapping("/documents")
    public ResponseEntity<List<CurriculumDocumentResponse>> listMyDocuments(
            @RequestParam(required = false) Long curriculumId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(curriculumDocumentService.listMyDocuments(actor.userId(), curriculumId));
    }

    /** UC-26: bài luyện Nghe/Chép chính tả/Nói theo curriculum của (các) lớp tôi đang học. */
    @GetMapping("/listening-practice")
    public ResponseEntity<List<ListeningPracticeItemResponse>> listMyListeningPractice(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Long curriculumId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(listeningPracticeService.listMyPracticeItems(actor.userId(), mode, curriculumId));
    }

    /** UC-61: bảng điểm đã công bố (PUBLISHED) của (các) lớp tôi đang học. */
    @GetMapping("/grades")
    public ResponseEntity<List<GradeEntryResponse>> listMyGrades(
            @RequestParam(required = false) Long classId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.listMyGrades(actor.userId(), classId));
    }

    /** UC-61: Overall/Level đã công bố (PUBLISHED) của 1 kỳ đánh giá. */
    @GetMapping("/classes/{classId}/periods/{gradePeriodId}/result")
    public ResponseEntity<GradePeriodResultResponse> getMyPeriodResult(
            @PathVariable Long classId, @PathVariable Long gradePeriodId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(gradeService.getMyPeriodResult(actor.userId(), classId, gradePeriodId));
    }
}
