package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ClassSessionHistory;
import vn.com.pps.education.domain.Room;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SessionPeriod;
import vn.com.pps.education.domain.SessionPeriodHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.SessionPeriodResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RoomConflictException;
import vn.com.pps.education.repository.ClassSessionHistoryRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.RoomRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SessionPeriodHistoryRepository;
import vn.com.pps.education.repository.SessionPeriodRepository;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Xếp lịch buổi học (class_sessions/session_periods) — nền tảng bắt buộc
 * cho UC-15 (Điểm danh học sinh, docs/uc/phan-he-05-hoc-sinh.md).
 *
 * KHÔNG có UC nào trong docs/uc/ mô tả Main Flow cho việc này — UC-15 và
 * UC-37 (Phân hệ 10) đều trỏ về "UC-18" nhưng UC-18 thực tế (đã code,
 * docs/uc/phan-he-06-hoc-thuat.md) chỉ tạo `classes`/`class_teachers`/
 * `class_enrollments`, không liên quan xếp lịch buổi học — đã xác nhận
 * với user đây là lỗi tham chiếu lặp lại trong tài liệu gốc. Coi là phần
 * mở rộng ngầm của Phân hệ 6 (cùng actor STAFF/HEAD_ACADEMIC như UC-18),
 * bám đúng schema SDD + FR-FAC-03 (kiểm tra trùng phòng, chỉ áp dụng cho
 * phòng is_flexible=FALSE).
 *
 * session_periods tự sinh theo system_settings.academic.default_periods_per_session
 * (key mới, xác nhận với user — SDD chỉ nói "mặc định 2 tiết/buổi theo
 * system_settings" không nêu setting_key cụ thể).
 *
 * Authorization qua @PreAuthorize("hasPermission(null,'academic.class.manage')")
 * ở ClassSessionController (Hybrid PBAC — V28, dùng chung permission với
 * UC-18 vì cùng tập role HEAD_ACADEMIC/STAFF).
 */
@Service
public class ClassSessionService {

    private static final String DEFAULT_PERIODS_SETTING_KEY = "academic.default_periods_per_session";

    private final ClassSessionRepository classSessionRepository;
    private final SessionPeriodRepository sessionPeriodRepository;
    private final ClassSessionHistoryRepository classSessionHistoryRepository;
    private final SessionPeriodHistoryRepository sessionPeriodHistoryRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final RoomRepository roomRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;

    public ClassSessionService(ClassSessionRepository classSessionRepository,
                                SessionPeriodRepository sessionPeriodRepository,
                                ClassSessionHistoryRepository classSessionHistoryRepository,
                                SessionPeriodHistoryRepository sessionPeriodHistoryRepository,
                                SchoolClassRepository schoolClassRepository,
                                RoomRepository roomRepository,
                                SystemSettingRepository systemSettingRepository,
                                UserRepository userRepository) {
        this.classSessionRepository = classSessionRepository;
        this.sessionPeriodRepository = sessionPeriodRepository;
        this.classSessionHistoryRepository = classSessionHistoryRepository;
        this.sessionPeriodHistoryRepository = sessionPeriodHistoryRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.roomRepository = roomRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listSessions(Long classId) {
        return classSessionRepository.findBySchoolClassIdOrderBySessionDateAsc(classId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SessionPeriodResponse> listPeriods(Long classSessionId) {
        return sessionPeriodRepository.findByClassSessionIdOrderByPeriodNumber(classSessionId).stream()
                .map(this::toResponse).toList();
    }

    /** Tạo 1 buổi học + tự sinh session_periods. FR-FAC-03: kiểm tra trùng phòng (chỉ phòng is_flexible=FALSE). */
    @Transactional
    public ClassSessionResponse createSession(Long classId, CreateClassSessionRequest request, Long actorUserId) {
        SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("endTime phải sau startTime.");
        }
        User teacher = userRepository.findById(request.primaryTeacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + request.primaryTeacherId()));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        Room room = null;
        if (request.roomId() != null) {
            room = roomRepository.findById(request.roomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng học id=" + request.roomId()));
            if (!room.isFlexible()) {
                List<ClassSession> overlapping = classSessionRepository.findOverlappingInRoom(
                        room.getId(), request.sessionDate(), request.startTime(), request.endTime(), null,
                        List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED));
                if (!overlapping.isEmpty()) {
                    throw new RoomConflictException(
                            "Phòng id=" + room.getId() + " đã có buổi học khác trùng khung giờ ngày " + request.sessionDate() + ".");
                }
            }
        }

        ClassSession session = new ClassSession();
        session.setSchoolClass(schoolClass);
        session.setSessionDate(request.sessionDate());
        session.setStartTime(request.startTime());
        session.setEndTime(request.endTime());
        session.setRoom(room);
        session.setPrimaryTeacher(teacher);
        session.setSessionType(ClassSession.SessionType.valueOf(request.sessionType()));
        session.setCreatedBy(actor);
        session = classSessionRepository.save(session);

        writeClassSessionHistory(session, actor, ClassSessionHistory.Action.CREATED);
        generateDefaultPeriods(session, actor);

        return toResponse(session);
    }

    private void generateDefaultPeriods(ClassSession session, User actor) {
        int count = systemSettingRepository.findBySettingKey(DEFAULT_PERIODS_SETTING_KEY)
                .map(s -> s.getSettingValue().asInt())
                .orElseThrow(() -> new ResourceNotFoundException("Thiếu cấu hình system_settings: " + DEFAULT_PERIODS_SETTING_KEY));

        long totalMinutes = ChronoUnit.MINUTES.between(session.getStartTime(), session.getEndTime());
        long minutesPerPeriod = totalMinutes / count;
        LocalTime cursor = session.getStartTime();
        for (int i = 1; i <= count; i++) {
            LocalTime periodEnd = (i == count) ? session.getEndTime() : cursor.plusMinutes(minutesPerPeriod);
            SessionPeriod period = new SessionPeriod();
            period.setClassSession(session);
            period.setPeriodNumber(i);
            period.setStartTime(cursor);
            period.setEndTime(periodEnd);
            period = sessionPeriodRepository.save(period);
            writeSessionPeriodHistory(period, actor, SessionPeriodHistory.Action.CREATED);
            cursor = periodEnd;
        }
    }

    private void writeClassSessionHistory(ClassSession session, User actor, ClassSessionHistory.Action action) {
        ClassSessionHistory history = new ClassSessionHistory();
        history.setClassSession(session);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sessionDate", String.valueOf(session.getSessionDate()));
        snapshot.put("startTime", String.valueOf(session.getStartTime()));
        snapshot.put("endTime", String.valueOf(session.getEndTime()));
        snapshot.put("status", session.getStatus().name());
        history.setDetails(snapshot);
        classSessionHistoryRepository.save(history);
    }

    private void writeSessionPeriodHistory(SessionPeriod period, User actor, SessionPeriodHistory.Action action) {
        SessionPeriodHistory history = new SessionPeriodHistory();
        history.setSessionPeriod(period);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("periodNumber", period.getPeriodNumber());
        snapshot.put("startTime", String.valueOf(period.getStartTime()));
        snapshot.put("endTime", String.valueOf(period.getEndTime()));
        history.setDetails(snapshot);
        sessionPeriodHistoryRepository.save(history);
    }

    private ClassSessionResponse toResponse(ClassSession s) {
        return new ClassSessionResponse(
                s.getId(), s.getSchoolClass().getId(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                s.getRoom() == null ? null : s.getRoom().getId(), s.getRoom() == null ? null : s.getRoom().getName(),
                s.getPrimaryTeacher().getId(), s.getPrimaryTeacher().getFullName(),
                s.getSessionType().name(), s.getStatus().name());
    }

    private SessionPeriodResponse toResponse(SessionPeriod p) {
        return new SessionPeriodResponse(
                p.getId(), p.getClassSession().getId(), p.getPeriodNumber(), p.getStartTime(), p.getEndTime(),
                p.getTeacher() == null ? null : p.getTeacher().getId(), p.getSubject() == null ? null : p.getSubject().getId(),
                p.getContentNote());
    }
}
