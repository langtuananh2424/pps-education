package vn.com.pps.education.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.AssignedExerciseResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CurriculumDocumentResponse;
import vn.com.pps.education.dto.ListeningPracticeItemResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.ClassSessionService;
import vn.com.pps.education.service.CurriculumDocumentService;
import vn.com.pps.education.service.ExerciseAttemptService;
import vn.com.pps.education.service.ListeningPracticeService;

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

    public StudentPortalController(ClassSessionService classSessionService,
                                    ExerciseAttemptService exerciseAttemptService,
                                    CurriculumDocumentService curriculumDocumentService,
                                    ListeningPracticeService listeningPracticeService) {
        this.classSessionService = classSessionService;
        this.exerciseAttemptService = exerciseAttemptService;
        this.curriculumDocumentService = curriculumDocumentService;
        this.listeningPracticeService = listeningPracticeService;
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
}
