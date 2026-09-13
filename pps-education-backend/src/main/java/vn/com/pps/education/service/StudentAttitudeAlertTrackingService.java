package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentAttitudeAlertState;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.StudentAttitudeAlertStateRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12 — cảnh báo
 * thái độ học tập Yếu/Trung bình trong nhận xét hàng ngày (UC-21/22).
 * Mirror {@code HomeworkAlertTrackingService} nhưng bỏ khái niệm "channel"
 * (attitude chỉ có đúng 1 luồng cảnh báo).
 *
 * 2 mức, KHÔNG loại trừ nhau (khác thang cảnh báo BTVN vốn chỉ gửi mức
 * nặng nhất):
 * 1) MỖI buổi thái độ Yếu/Trung bình đều báo Phụ huynh ngay (không cần
 *    chờ streak).
 * 2) Yếu/Trung bình LIÊN TỤC 3 buổi (tính trên nhận xét đã APPROVED —
 *    nhận xét PENDING/REJECTED không tính) → thêm 1 cảnh báo escalation,
 *    nhưng phải qua Quản lý điểm trường duyệt trước khi gửi (xem
 *    {@link StudentAttitudeEscalationService}) — reset streak về 0 ngay
 *    sau khi tạo yêu cầu duyệt (mirror mốc-4 của Homework Alert).
 *
 * Chỉ gọi cho comment ĐÃ APPROVED (từ {@code StudentCommentService.decideComments}) —
 * nhận xét chưa duyệt/bị từ chối không tính vào streak (đã xác nhận với người dùng).
 */
@Service
public class StudentAttitudeAlertTrackingService {

    private final StudentAttitudeAlertStateRepository stateRepository;
    private final AcademicTermRepository academicTermRepository;
    private final NotificationService notificationService;
    private final ParentStudentRepository parentStudentRepository;
    private final StudentAttitudeEscalationService escalationService;

    private static final int ESCALATION_THRESHOLD = 3;

    public StudentAttitudeAlertTrackingService(StudentAttitudeAlertStateRepository stateRepository,
                                                AcademicTermRepository academicTermRepository,
                                                NotificationService notificationService,
                                                ParentStudentRepository parentStudentRepository,
                                                StudentAttitudeEscalationService escalationService) {
        this.stateRepository = stateRepository;
        this.academicTermRepository = academicTermRepository;
        this.notificationService = notificationService;
        this.parentStudentRepository = parentStudentRepository;
        this.escalationService = escalationService;
    }

    @Transactional
    public void evaluateAndNotify(StudentComment comment) {
        StudentComment.Attitude attitude = comment.getAttitude();
        if (attitude == null) {
            return;
        }
        Student student = comment.getStudent();
        SchoolClass schoolClass = comment.getSchoolClass();
        AcademicTerm term = resolveCurrentTerm(schoolClass);
        StudentAttitudeAlertState state = findOrCreate(student, schoolClass, term);

        if (attitude != StudentComment.Attitude.WEAK && attitude != StudentComment.Attitude.AVERAGE) {
            state.setConsecutiveLowCount(0);
            stateRepository.save(state);
            return;
        }

        notifySingleDayAlert(comment, attitude);

        int streak = state.getConsecutiveLowCount() + 1;
        if (streak >= ESCALATION_THRESHOLD) {
            state.setConsecutiveLowCount(0); // reset ngay sau khi chạm mốc escalation (mirror mốc-4 Homework Alert)
            stateRepository.save(state);
            escalationService.submitForApproval(student, schoolClass, ESCALATION_THRESHOLD);
        } else {
            state.setConsecutiveLowCount(streak);
            stateRepository.save(state);
        }
    }

    private void notifySingleDayAlert(StudentComment comment, StudentComment.Attitude attitude) {
        Student student = comment.getStudent();
        SchoolClass schoolClass = comment.getSchoolClass();
        String studentName = student.getUser().getFullName();
        String attitudeLabel = attitudeLabel(attitude);
        String title = studentName + ": thái độ học tập cần lưu ý";
        String content = "Buổi " + comment.getCommentDate() + ", lớp " + schoolClass.getName() + ": " + studentName
                + " có thái độ học tập " + attitudeLabel + ".";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("studentName", studentName);
        metadata.put("className", schoolClass.getName());
        metadata.put("commentDate", comment.getCommentDate());
        metadata.put("attitudeLabel", attitudeLabel);

        for (ParentStudent link : parentStudentRepository.findByStudentId(student.getId())) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.STUDENT_ATTITUDE_ALERT,
                    title, content, metadata, "STUDENT_COMMENT", comment.getId(), Notification.Priority.NORMAL, null);
        }
    }

    private String attitudeLabel(StudentComment.Attitude attitude) {
        return switch (attitude) {
            case WEAK -> "Yếu";
            case AVERAGE -> "Trung bình";
            case FAIR -> "Khá";
            case GOOD -> "Tốt";
            case EXCELLENT -> "Xuất sắc";
        };
    }

    private StudentAttitudeAlertState findOrCreate(Student student, SchoolClass schoolClass, AcademicTerm term) {
        var existing = term == null
                ? stateRepository.findByStudentIdAndSchoolClassIdAndAcademicTermIdIsNull(student.getId(), schoolClass.getId())
                : stateRepository.findByStudentIdAndSchoolClassIdAndAcademicTermId(student.getId(), schoolClass.getId(), term.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        StudentAttitudeAlertState state = new StudentAttitudeAlertState();
        state.setStudent(student);
        state.setSchoolClass(schoolClass);
        state.setAcademicTerm(term);
        return state;
    }

    private AcademicTerm resolveCurrentTerm(SchoolClass schoolClass) {
        LocalDate today = LocalDate.now();
        return academicTermRepository.findFirstBySiteIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                schoolClass.getSite().getId(), today, today).orElse(null);
    }
}
