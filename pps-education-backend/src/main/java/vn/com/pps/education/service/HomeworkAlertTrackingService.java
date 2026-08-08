package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentHomeworkAlertState;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.StudentHomeworkAlertStateRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — cảnh báo
 * thiếu BTVN theo lộ trình, báo Phụ huynh. Đếm streak/tổng số buổi thiếu
 * TÁCH RIÊNG theo từng kênh (Ngữ pháp/Video — 2 chuỗi giao bài độc lập,
 * xem {@code student_homework_alert_state}); vì 2 kênh luôn cùng hạn nộp,
 * khi CẢ 2 kênh cùng chạm mốc trong 1 lượt đánh giá thì gộp thành 1 thông
 * báo duy nhất liệt kê từng kênh (đã xác nhận với người dùng).
 *
 * Thang cảnh báo kiểu 1 (thiếu LIÊN TỤC, theo streak):
 * streak=2 → Nhắc nhở; streak=3 → Cảnh báo học tập; streak=4 → Thư mời
 * phụ huynh tới làm việc rồi reset streak về 0 ngay.
 * Kiểu 2 (thiếu KHÔNG liên tục — tổng ≥3 buổi trong 1 học kỳ, không cần
 * liên tiếp): Nhắc nhở, gửi đúng 1 lần/kỳ. Nếu 1 buổi vừa chạm mốc kiểu 1
 * vừa đủ điều kiện kiểu 2 cùng lúc, CHỈ gửi kiểu 1 (nặng/cụ thể hơn).
 */
@Service
public class HomeworkAlertTrackingService {

    public record ChannelMissResult(StudentHomeworkAlertState.Channel channel, boolean passed) {}

    private record TriggeredAlert(String line, int rank, Notification.NotificationType type, Notification.Priority priority) {}

    private final StudentHomeworkAlertStateRepository stateRepository;
    private final AcademicTermRepository academicTermRepository;
    private final NotificationService notificationService;
    private final ParentStudentRepository parentStudentRepository;

    public HomeworkAlertTrackingService(StudentHomeworkAlertStateRepository stateRepository,
                                         AcademicTermRepository academicTermRepository,
                                         NotificationService notificationService,
                                         ParentStudentRepository parentStudentRepository) {
        this.stateRepository = stateRepository;
        this.academicTermRepository = academicTermRepository;
        this.notificationService = notificationService;
        this.parentStudentRepository = parentStudentRepository;
    }

    @Transactional
    public void evaluateAndNotify(Student student, SchoolClass schoolClass, List<ChannelMissResult> results) {
        if (results.isEmpty()) {
            return;
        }
        AcademicTerm term = resolveCurrentTerm(schoolClass);
        List<TriggeredAlert> triggered = new ArrayList<>();

        for (ChannelMissResult result : results) {
            StudentHomeworkAlertState state = findOrCreate(student, schoolClass, result.channel(), term);
            if (result.passed()) {
                state.setConsecutiveMissCount(0);
                stateRepository.save(state);
                continue;
            }
            triggered.addAll(applyMiss(state, result.channel()));
            stateRepository.save(state);
        }

        if (triggered.isEmpty()) {
            return;
        }
        sendCombinedNotification(student, schoolClass, triggered);
    }

    /** Cập nhật streak/tổng cho 1 kênh không đạt, trả về 0-1 cảnh báo vừa chạm mốc (kiểu 1 ưu tiên hơn kiểu 2 — giả định 5 đã xác nhận). */
    private List<TriggeredAlert> applyMiss(StudentHomeworkAlertState state, StudentHomeworkAlertState.Channel channel) {
        String channelLabel = channel == StudentHomeworkAlertState.Channel.GRAMMAR ? "BTVN Ngữ pháp" : "BTVN Video";

        int streak = state.getConsecutiveMissCount() + 1;
        state.setConsecutiveMissCount(streak);
        int total = state.getTotalMissCountInTerm() + 1;
        state.setTotalMissCountInTerm(total);

        TriggeredAlert type1 = switch (streak) {
            case 2 -> new TriggeredAlert(channelLabel + ": thiếu liên tục 2 buổi (Nhắc nhở).",
                    0, Notification.NotificationType.HOMEWORK_MISS_REMINDER, Notification.Priority.NORMAL);
            case 3 -> new TriggeredAlert(channelLabel + ": thiếu liên tục 3 buổi (Cảnh báo học tập).",
                    1, Notification.NotificationType.HOMEWORK_MISS_WARNING, Notification.Priority.HIGH);
            case 4 -> {
                state.setConsecutiveMissCount(0); // reset ngay sau khi chạm mốc mời làm việc (đã xác nhận với người dùng)
                yield new TriggeredAlert(channelLabel + ": thiếu liên tục 4 buổi (Thư mời phụ huynh tới làm việc).",
                        2, Notification.NotificationType.HOMEWORK_MISS_PARENT_MEETING_INVITE, Notification.Priority.URGENT);
            }
            default -> null;
        };
        if (type1 != null) {
            return List.of(type1);
        }

        if (!state.isType2AlertSent() && total >= 3) {
            state.setType2AlertSent(true);
            return List.of(new TriggeredAlert(channelLabel + ": thiếu " + total + " buổi không liên tục trong kỳ (Nhắc nhở).",
                    0, Notification.NotificationType.HOMEWORK_MISS_REMINDER_NON_CONSECUTIVE, Notification.Priority.NORMAL));
        }
        return List.of();
    }

    private void sendCombinedNotification(Student student, SchoolClass schoolClass, List<TriggeredAlert> triggered) {
        TriggeredAlert heaviest = triggered.stream().max(java.util.Comparator.comparingInt(TriggeredAlert::rank)).orElseThrow();
        String title = "Cảnh báo BTVN — học sinh " + student.getUser().getFullName();
        String content = triggered.stream().map(TriggeredAlert::line).reduce((a, b) -> a + "\n" + b).orElse("");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentName", student.getUser().getFullName());
        metadata.put("className", schoolClass.getName());

        for (ParentStudent link : parentStudentRepository.findByStudentId(student.getId())) {
            notificationService.notify(link.getParent().getUser().getId(), heaviest.type(), title, content,
                    metadata, "STUDENT", student.getId(), heaviest.priority(), null);
        }
    }

    private StudentHomeworkAlertState findOrCreate(Student student, SchoolClass schoolClass,
                                                     StudentHomeworkAlertState.Channel channel, AcademicTerm term) {
        var existing = term == null
                ? stateRepository.findByStudentIdAndSchoolClassIdAndChannelAndAcademicTermIdIsNull(student.getId(), schoolClass.getId(), channel)
                : stateRepository.findByStudentIdAndSchoolClassIdAndChannelAndAcademicTermId(student.getId(), schoolClass.getId(), channel, term.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        StudentHomeworkAlertState state = new StudentHomeworkAlertState();
        state.setStudent(student);
        state.setSchoolClass(schoolClass);
        state.setChannel(channel);
        state.setAcademicTerm(term);
        return state;
    }

    private AcademicTerm resolveCurrentTerm(SchoolClass schoolClass) {
        LocalDate today = LocalDate.now();
        return academicTermRepository.findFirstBySiteIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                schoolClass.getSite().getId(), today, today).orElse(null);
    }
}
