package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.ListeningPracticeAttempt;
import vn.com.pps.education.domain.ListeningPracticeItem;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateListeningPracticeItemRequest;
import vn.com.pps.education.dto.ListeningPracticeAttemptResponse;
import vn.com.pps.education.dto.ListeningPracticeItemResponse;
import vn.com.pps.education.dto.SubmitListeningPracticeAttemptRequest;
import vn.com.pps.education.dto.UpdateListeningPracticeItemRequest;
import vn.com.pps.education.exception.AttemptNotEditableException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.ListeningPracticeAttemptRepository;
import vn.com.pps.education.repository.ListeningPracticeItemRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * UC-26: Luyện Nghe – Nói (FR-LMS-04 — domain riêng biệt, bổ sung ngoài
 * SDD gốc, đã xác nhận với người dùng). Xem docs/uc/phan-he-07-lms-portal.md.
 *
 * 3 chế độ (mode của {@link ListeningPracticeItem}): LISTENING (chỉ đánh
 * dấu hoàn thành, không có gì để chấm), DICTATION (so khớp tự động với
 * script_text, chấm ngay), SPEAKING (không tự chấm — luôn chuyển hàng
 * chờ chấm thủ công riêng, xem {@link ListeningPracticeGradingService},
 * KHÔNG tích hợp ASR/so khớp phát âm bên thứ 3 — khác câu chữ gốc UC-26
 * "sơ bộ tự động", đã xác nhận với người dùng).
 *
 * Tự luyện (giống tinh thần UC-27) — không giới hạn số lượt, không
 * deadline, tổ chức theo curriculum (không theo lớp/giao bài như UC-24).
 */
@Service
public class ListeningPracticeService {

    private final ListeningPracticeItemRepository practiceItemRepository;
    private final ListeningPracticeAttemptRepository practiceAttemptRepository;
    private final CurriculumRepository curriculumRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public ListeningPracticeService(ListeningPracticeItemRepository practiceItemRepository,
                                     ListeningPracticeAttemptRepository practiceAttemptRepository,
                                     CurriculumRepository curriculumRepository,
                                     ClassEnrollmentRepository classEnrollmentRepository,
                                     StudentRepository studentRepository,
                                     UserRepository userRepository) {
        this.practiceItemRepository = practiceItemRepository;
        this.practiceAttemptRepository = practiceAttemptRepository;
        this.curriculumRepository = curriculumRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ListeningPracticeItemResponse createItem(CreateListeningPracticeItemRequest request, Long actorUserId) {
        Curriculum curriculum = curriculumOrThrow(request.curriculumId());
        User actor = userOrThrow(actorUserId);

        ListeningPracticeItem item = new ListeningPracticeItem();
        item.setCurriculum(curriculum);
        item.setTitle(request.title());
        item.setMode(ListeningPracticeItem.Mode.valueOf(request.mode()));
        item.setAudioUrl(request.audioUrl());
        item.setScriptText(request.scriptText());
        if (request.difficulty() != null) {
            item.setDifficulty(ListeningPracticeItem.Difficulty.valueOf(request.difficulty()));
        }
        item.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        item.setCreatedBy(actor);
        item = practiceItemRepository.save(item);
        return toResponse(item);
    }

    @Transactional
    public ListeningPracticeItemResponse updateItem(Long id, UpdateListeningPracticeItemRequest request, Long actorUserId) {
        ListeningPracticeItem item = itemOrThrow(id);
        item.setTitle(request.title());
        item.setAudioUrl(request.audioUrl());
        item.setScriptText(request.scriptText());
        item.setDifficulty(request.difficulty() == null ? null : ListeningPracticeItem.Difficulty.valueOf(request.difficulty()));
        if (request.displayOrder() != null) {
            item.setDisplayOrder(request.displayOrder());
        }
        item.setStatus(ListeningPracticeItem.Status.valueOf(request.status()));
        item = practiceItemRepository.save(item);
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public List<ListeningPracticeItemResponse> listByCurriculum(Long curriculumId) {
        return practiceItemRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream()
                .map(this::toResponse).toList();
    }

    /** Bổ sung: Học sinh tự duyệt bài luyện theo curriculum của (các) lớp đang ghi danh ACTIVE — chỉ PUBLISHED. */
    @Transactional(readOnly = true)
    public List<ListeningPracticeItemResponse> listMyPracticeItems(Long actorUserId, String modeFilter, Long curriculumIdFilter) {
        Student student = studentOrThrow(actorUserId);
        List<Long> curriculumIds = classEnrollmentRepository.findByStudentId(student.getId()).stream()
                .filter(e -> e.getStatus() == ClassEnrollment.Status.ACTIVE)
                .map(e -> e.getSchoolClass().getCurriculum().getId())
                .distinct()
                .filter(id -> curriculumIdFilter == null || id.equals(curriculumIdFilter))
                .toList();
        if (curriculumIds.isEmpty()) {
            return List.of();
        }
        List<ListeningPracticeItem> items = modeFilter == null
                ? practiceItemRepository.findByCurriculumIdInAndStatusOrderByDisplayOrder(curriculumIds, ListeningPracticeItem.Status.PUBLISHED)
                : practiceItemRepository.findByCurriculumIdInAndStatusAndModeOrderByDisplayOrder(
                        curriculumIds, ListeningPracticeItem.Status.PUBLISHED, ListeningPracticeItem.Mode.valueOf(modeFilter));
        return items.stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 1: học sinh chọn bài luyện, bắt đầu 1 lượt mới (không giới hạn số lượt). */
    @Transactional
    public ListeningPracticeAttemptResponse startAttempt(Long practiceItemId, Long actorUserId) {
        Student student = studentOrThrow(actorUserId);
        ListeningPracticeItem item = itemOrThrow(practiceItemId);
        if (item.getStatus() != ListeningPracticeItem.Status.PUBLISHED) {
            throw new ResourceNotFoundException("error.listeningPractice.itemNotFound",
                    new Object[]{practiceItemId}, "Không tìm thấy bài luyện id=" + practiceItemId);
        }
        long previousAttempts = practiceAttemptRepository.countByPracticeItemIdAndStudentId(practiceItemId, student.getId());

        ListeningPracticeAttempt attempt = new ListeningPracticeAttempt();
        attempt.setPracticeItem(item);
        attempt.setStudent(student);
        attempt.setAttemptNumber((int) previousAttempts + 1);
        attempt = practiceAttemptRepository.save(attempt);
        return toResponse(attempt);
    }

    /** A1 — tạm dừng giữa chừng, lưu vị trí để tiếp tục sau. */
    @Transactional
    public ListeningPracticeAttemptResponse pauseAttempt(Long attemptId, Integer positionSeconds, Long actorUserId) {
        ListeningPracticeAttempt attempt = attemptOwnedByActor(attemptId, actorUserId);
        if (attempt.getStatus() != ListeningPracticeAttempt.Status.IN_PROGRESS) {
            throw new AttemptNotEditableException("error.attemptNotEditable.listeningPractice", new Object[]{}, "Lượt luyện này không còn ở trạng thái đang làm (IN_PROGRESS).");
        }
        attempt.setPausedPositionSeconds(positionSeconds);
        attempt = practiceAttemptRepository.save(attempt);
        return toResponse(attempt);
    }

    /**
     * Main Flow bước 2-5: nộp kết quả luyện tập, rẽ nhánh theo mode của
     * item — LISTENING chỉ đánh dấu hoàn thành; DICTATION so khớp tự
     * động với script_text, chấm ngay; SPEAKING lưu audio, chuyển hàng
     * chờ chấm thủ công (không tự chấm).
     */
    @Transactional
    public ListeningPracticeAttemptResponse submitAttempt(Long attemptId, SubmitListeningPracticeAttemptRequest request, Long actorUserId) {
        ListeningPracticeAttempt attempt = attemptOwnedByActor(attemptId, actorUserId);
        if (attempt.getStatus() != ListeningPracticeAttempt.Status.IN_PROGRESS) {
            throw new AttemptNotEditableException("error.attemptNotEditable.listeningPractice", new Object[]{}, "Lượt luyện này không còn ở trạng thái đang làm (IN_PROGRESS).");
        }
        ListeningPracticeItem item = attempt.getPracticeItem();
        OffsetDateTime now = OffsetDateTime.now();
        attempt.setSubmittedAt(now);

        switch (item.getMode()) {
            case LISTENING -> attempt.setStatus(ListeningPracticeAttempt.Status.GRADED);
            case DICTATION -> {
                attempt.setDictationAnswerText(request.dictationAnswerText());
                attempt.setDictationScore(computeDictationScore(request.dictationAnswerText(), item.getScriptText()));
                attempt.setStatus(ListeningPracticeAttempt.Status.GRADED);
            }
            case SPEAKING -> {
                attempt.setAudioAnswerUrl(request.audioAnswerUrl());
                attempt.setStatus(ListeningPracticeAttempt.Status.SUBMITTED);
            }
        }
        attempt = practiceAttemptRepository.save(attempt);
        return toResponse(attempt);
    }

    @Transactional(readOnly = true)
    public ListeningPracticeAttemptResponse getAttempt(Long attemptId, Long actorUserId) {
        return toResponse(attemptOwnedByActor(attemptId, actorUserId));
    }

    // ===================== Helpers =====================

    /**
     * Chấm tự động chế độ Chép chính tả: so khớp theo từ (chuẩn hoá
     * khoảng trắng/hoa-thường/dấu câu) — không có công thức cụ thể trong
     * SRS/SDD (UC-26 chỉ nói "so khớp"), dùng tỉ lệ từ khớp đúng vị trí
     * / tổng số từ trong script_text, thang điểm 0-100.
     */
    private BigDecimal computeDictationScore(String answer, String script) {
        String[] answerWords = normalize(answer).split(" ");
        String[] scriptWords = normalize(script).split(" ");
        if (scriptWords.length == 0 || scriptWords[0].isEmpty()) {
            return BigDecimal.ZERO;
        }
        int matched = 0;
        for (int i = 0; i < scriptWords.length; i++) {
            if (i < answerWords.length && answerWords[i].equals(scriptWords[i])) {
                matched++;
            }
        }
        return BigDecimal.valueOf(matched * 100.0 / scriptWords.length).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[.,!?;:\"'()]", "").trim().replaceAll("\\s+", " ");
    }

    private ListeningPracticeAttempt attemptOwnedByActor(Long attemptId, Long actorUserId) {
        Student student = studentOrThrow(actorUserId);
        ListeningPracticeAttempt attempt = practiceAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("error.listeningPractice.attemptNotFound",
                        new Object[]{attemptId}, "Không tìm thấy lượt luyện id=" + attemptId));
        if (!attempt.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("error.listeningPractice.attemptNotFound",
                    new Object[]{attemptId}, "Không tìm thấy lượt luyện id=" + attemptId);
        }
        return attempt;
    }

    private Student studentOrThrow(Long actorUserId) {
        return studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.listeningPractice.studentProfileNotFound",
                        new Object[]{actorUserId}, "Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
    }

    private Curriculum curriculumOrThrow(Long id) {
        return curriculumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.listeningPractice.curriculumNotFound",
                        new Object[]{id}, "Không tìm thấy khung chương trình id=" + id));
    }

    private ListeningPracticeItem itemOrThrow(Long id) {
        return practiceItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.listeningPractice.itemNotFound",
                        new Object[]{id}, "Không tìm thấy bài luyện id=" + id));
    }

    private User userOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.listeningPractice.userNotFound",
                        new Object[]{id}, "Không tìm thấy tài khoản id=" + id));
    }

    private ListeningPracticeItemResponse toResponse(ListeningPracticeItem i) {
        return new ListeningPracticeItemResponse(
                i.getId(), i.getCurriculum().getId(), i.getTitle(), i.getMode().name(), i.getAudioUrl(),
                i.getScriptText(), i.getDifficulty() == null ? null : i.getDifficulty().name(),
                i.getDisplayOrder(), i.getStatus().name(), i.getCreatedBy().getId());
    }

    private ListeningPracticeAttemptResponse toResponse(ListeningPracticeAttempt a) {
        return new ListeningPracticeAttemptResponse(
                a.getId(), a.getPracticeItem().getId(), a.getStudent().getId(), a.getAttemptNumber(),
                a.getStartedAt(), a.getSubmittedAt(), a.getPausedPositionSeconds(), a.getDictationAnswerText(),
                a.getDictationScore(), a.getAudioAnswerUrl(), a.getStatus().name());
    }
}
