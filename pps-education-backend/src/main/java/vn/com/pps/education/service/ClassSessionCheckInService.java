package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ClassSessionCheckIn;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.dto.ClassSessionCheckInRequest;
import vn.com.pps.education.dto.ClassSessionCheckInResponse;
import vn.com.pps.education.dto.ClassSessionCheckInStatusResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.exception.AlreadyCheckedInException;
import vn.com.pps.education.exception.ClassSessionNotCheckableException;
import vn.com.pps.education.exception.NotAssignedTeacherForSessionException;
import vn.com.pps.education.exception.OutsideAttendanceWindowException;
import vn.com.pps.education.exception.OutsideGpsRadiusException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassSessionCheckInRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.service.attendance.AttendanceSettings;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UC-71: Nhận lớp (bổ sung ngoài SDD/SRS gốc, đã xác nhận với người dùng
 * 2026-08-18). Giáo viên xác nhận có mặt để dạy TỪNG buổi học
 * (class_sessions), độc lập với chấm công ca hành chính (UC-09) — không
 * check-out. Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Service
public class ClassSessionCheckInService {

    /** Main Flow — cửa sổ nhận lớp mở trước giờ học 15 phút. */
    private static final int OPEN_BEFORE_MINUTES = 15;

    private final ClassSessionRepository classSessionRepository;
    private final ClassSessionCheckInRepository classSessionCheckInRepository;
    private final SiteRepository siteRepository;
    private final AttendanceSettings attendanceSettings;

    public ClassSessionCheckInService(ClassSessionRepository classSessionRepository,
                                       ClassSessionCheckInRepository classSessionCheckInRepository,
                                       SiteRepository siteRepository,
                                       AttendanceSettings attendanceSettings) {
        this.classSessionRepository = classSessionRepository;
        this.classSessionCheckInRepository = classSessionCheckInRepository;
        this.siteRepository = siteRepository;
        this.attendanceSettings = attendanceSettings;
    }

    /**
     * Main Flow 1-8: xác định buổi học, kiểm tra đúng GV được phân công, cửa
     * sổ giờ, chưa nhận trùng, trong bán kính điểm trường — rồi ghi nhận.
     * A1/A2 (ngoài cửa sổ), A3 (đã nhận), A4 (ngoài bán kính) đều reject,
     * không tạo bản ghi.
     */
    @Transactional
    public ClassSessionCheckInResponse checkIn(Long classSessionId, Long actorUserId, ClassSessionCheckInRequest request) {
        ClassSession session = classSessionRepository.findById(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.classSessionCheckIn.classSessionNotFound", new Object[]{classSessionId}, "Không tìm thấy buổi học id=" + classSessionId));

        if (session.getStatus() == ClassSession.Status.CANCELLED || session.getStatus() == ClassSession.Status.RESCHEDULED) {
            throw new ClassSessionNotCheckableException("Buổi học đã bị hủy/dời lịch, không thể nhận lớp.");
        }
        if (!session.getPrimaryTeacher().getId().equals(actorUserId)) {
            throw new NotAssignedTeacherForSessionException("Bạn không được phân công dạy buổi học này.");
        }
        if (classSessionCheckInRepository.existsByClassSessionId(classSessionId)) {
            throw new AlreadyCheckedInException("Buổi học này đã được nhận lớp.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sessionStart = toOffsetDateTime(session, session.getStartTime());
        OffsetDateTime sessionEnd = toOffsetDateTime(session, session.getEndTime());
        OffsetDateTime windowOpensAt = sessionStart.minusMinutes(OPEN_BEFORE_MINUTES);

        if (now.isBefore(windowOpensAt)) {
            throw new OutsideAttendanceWindowException(
                    "Chưa tới giờ nhận lớp — chỉ có thể nhận lớp từ " + OPEN_BEFORE_MINUTES + " phút trước giờ học.");
        }
        if (now.isAfter(sessionEnd)) {
            throw new OutsideAttendanceWindowException(
                    "Đã hết giờ nhận lớp cho buổi học này — buổi học được coi là vắng.");
        }

        Site site = session.getSchoolClass().getSite();
        boolean withinRadius = Boolean.TRUE.equals(siteRepository.isWithinRadius(
                site.getId(), request.latitude(), request.longitude(), attendanceSettings.gpsRadiusMeters()));
        if (!withinRadius) {
            throw new OutsideGpsRadiusException(
                    "Vị trí GPS hiện tại không nằm trong bán kính cho phép của điểm trường \"" + site.getName() + "\".");
        }

        ClassSessionCheckIn.Status status = now.isBefore(sessionStart)
                ? ClassSessionCheckIn.Status.ON_TIME : ClassSessionCheckIn.Status.LATE;

        ClassSessionCheckIn checkIn = new ClassSessionCheckIn();
        checkIn.setClassSession(session);
        checkIn.setTeacher(session.getPrimaryTeacher());
        checkIn.setCheckInTime(now);
        checkIn.setStatus(status);
        checkIn.setLatitude(request.latitude());
        checkIn.setLongitude(request.longitude());
        checkIn.setSite(site);
        checkIn = classSessionCheckInRepository.save(checkIn);

        return toResponse(checkIn);
    }

    /**
     * Trạng thái nhận lớp TÍNH RA (không lưu DB) cho danh sách buổi học đã
     * cho trước — dùng chung cho roster admin (EmployeeScheduleService) và
     * trang GV tự xem, tránh lặp logic cửa sổ ở 2 nơi. Bỏ qua buổi
     * CANCELLED/RESCHEDULED (không cần nhận lớp).
     */
    @Transactional(readOnly = true)
    public List<ClassSessionCheckInStatusResponse> listEffectiveStatus(List<ClassSessionResponse> sessions) {
        List<ClassSessionResponse> checkableSessions = sessions.stream()
                .filter(s -> !"CANCELLED".equals(s.status()) && !"RESCHEDULED".equals(s.status()))
                .toList();
        if (checkableSessions.isEmpty()) {
            return List.of();
        }
        List<Long> sessionIds = checkableSessions.stream().map(ClassSessionResponse::id).toList();
        Map<Long, ClassSessionCheckIn> checkInsBySessionId = classSessionCheckInRepository.findByClassSessionIdIn(sessionIds).stream()
                .collect(Collectors.toMap(c -> c.getClassSession().getId(), c -> c));
        OffsetDateTime now = OffsetDateTime.now();
        return checkableSessions.stream().map(s -> toStatusResponse(s, checkInsBySessionId.get(s.id()), now)).toList();
    }

    private ClassSessionCheckInStatusResponse toStatusResponse(ClassSessionResponse session, ClassSessionCheckIn checkIn, OffsetDateTime now) {
        if (checkIn != null) {
            return new ClassSessionCheckInStatusResponse(session.id(), checkIn.getStatus().name(), checkIn.getCheckInTime());
        }
        OffsetDateTime sessionStart = session.sessionDate().atTime(session.startTime()).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime sessionEnd = session.sessionDate().atTime(session.endTime()).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime windowOpensAt = sessionStart.minusMinutes(OPEN_BEFORE_MINUTES);

        String effectiveStatus;
        if (now.isBefore(windowOpensAt)) {
            effectiveStatus = "NOT_YET_OPEN";
        } else if (now.isAfter(sessionEnd)) {
            effectiveStatus = "ABSENT";
        } else {
            effectiveStatus = "PENDING";
        }
        return new ClassSessionCheckInStatusResponse(session.id(), effectiveStatus, null);
    }

    private OffsetDateTime toOffsetDateTime(ClassSession session, java.time.LocalTime time) {
        return session.getSessionDate().atTime(time).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private ClassSessionCheckInResponse toResponse(ClassSessionCheckIn c) {
        return new ClassSessionCheckInResponse(
                c.getId(), c.getClassSession().getId(), c.getTeacher().getId(), c.getTeacher().getFullName(),
                c.getCheckInTime(), c.getStatus().name());
    }
}
