package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.AttendanceSession;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.HomeworkParentMeetingInvite;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentComment;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.HomeworkParentMeetingInviteRepository;
import vn.com.pps.education.repository.StudentCommentRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** UC-74 (bổ sung 2026-09-30) — tín hiệu điểm danh/chuyên cần, mời họp phụ huynh, thông tin học sinh, nhận xét buổi GVNN. */
class StudentSignalServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 30);

    private final ClassSessionRepository classSessionRepository = mock(ClassSessionRepository.class);
    private final AttendanceMarkRepository attendanceMarkRepository = mock(AttendanceMarkRepository.class);
    private final HomeworkParentMeetingInviteRepository inviteRepository = mock(HomeworkParentMeetingInviteRepository.class);
    private final StudentCommentRepository studentCommentRepository = mock(StudentCommentRepository.class);

    private final StudentSignalService service = new StudentSignalService(classSessionRepository, attendanceMarkRepository,
            inviteRepository, studentCommentRepository);

    private final Student student = mock(Student.class);
    private final SchoolClass schoolClass = mock(SchoolClass.class);

    private ClassSession session(long id, LocalDate date) {
        ClassSession session = mock(ClassSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getSchoolClass()).thenReturn(schoolClass);
        when(session.getSessionDate()).thenReturn(date);
        when(session.getTeacherType()).thenReturn(ClassSession.TeacherType.VIETNAMESE);
        return session;
    }

    private AttendanceMark mark(ClassSession session, AttendanceMark.Status status, Integer minutesLate) {
        AttendanceMark mark = mock(AttendanceMark.class);
        AttendanceSession attendanceSession = mock(AttendanceSession.class);
        when(attendanceSession.getClassSession()).thenReturn(session);
        when(mark.getAttendanceSession()).thenReturn(attendanceSession);
        when(mark.getStudent()).thenReturn(student);
        when(mark.getStatus()).thenReturn(status);
        when(mark.getMinutesLate()).thenReturn(minutesLate);
        return mark;
    }

    @Test
    void describe_UC74_buildsAttendanceToneStudentInfoAndOtherTeacherComment() {
        when(student.getId()).thenReturn(1L);
        when(student.getDateOfBirth()).thenReturn(DATE.minusYears(8));
        when(schoolClass.getId()).thenReturn(5L);
        ClassEnrollment enrollment = mock(ClassEnrollment.class);
        when(enrollment.getStudent()).thenReturn(student);
        when(enrollment.getEnrolledDate()).thenReturn(DATE.minusDays(10));
        ClassSession current = session(100L, DATE);
        List<ClassSession> before = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            before.add(session(100L - i, DATE.minusDays(i)));
        }
        when(classSessionRepository.findSessionsBeforeOrderedDesc(eq(5L), eq(DATE), eq(100L), anyList())).thenReturn(before);
        // 3 buổi đi muộn trong 8 buổi → nhắc "hay đến lớp muộn"; hôm nay muộn 15 phút → nhắc "hôm nay đến lớp muộn".
        AttendanceMark today = mark(current, AttendanceMark.Status.LATE, 15);
        List<AttendanceMark> history = List.of(mark(before.get(0), AttendanceMark.Status.LATE, null),
                mark(before.get(1), AttendanceMark.Status.LATE, 5), mark(before.get(2), AttendanceMark.Status.PRESENT, null));
        when(attendanceMarkRepository.findByClassSessionIdInAndStudentIdIn(any(), any())).thenReturn(history);
        HomeworkParentMeetingInvite invite = mock(HomeworkParentMeetingInvite.class);
        when(invite.getStudent()).thenReturn(student);
        when(invite.getChannelLabel()).thenReturn("Ngữ pháp");
        when(inviteRepository.findByStudentIdInAndSchoolClassIdAndStatusInAndCreatedAtGreaterThanEqual(any(), eq(5L), any(), any()))
                .thenReturn(List.of(invite));
        ClassSession foreignSession = session(90L, DATE);
        when(classSessionRepository.findSessionsBeforeWithTeacherTypeOrderedDesc(eq(5L), eq(DATE), eq(100L),
                eq(ClassSession.TeacherType.FOREIGN), anyList())).thenReturn(List.of(foreignSession));
        StudentComment foreignComment = mock(StudentComment.class);
        when(foreignComment.getStudent()).thenReturn(student);
        when(foreignComment.getContent()).thenReturn(" Con phản xạ nhanh. ");
        when(foreignComment.getStatus()).thenReturn(StudentComment.Status.APPROVED);
        when(studentCommentRepository.findByClassSessionIdAndStudentIdIn(90L, List.of(1L))).thenReturn(List.of(foreignComment));

        StudentSignalService.Signals signals = service.describe(current, Map.of(1L, enrollment), Map.of(1L, today)).get(1L);

        assertThat(signals.attendance()).containsExactly("hôm nay đến lớp muộn (nhắc nhẹ đến lớp đúng giờ)",
                "hay đến lớp muộn trong các buổi gần đây (nhắc nhẹ đến lớp đúng giờ)");
        assertThat(signals.toneHints()).hasSize(2);
        assertThat(signals.toneHints().get(0)).contains("Ngữ pháp").contains("KHÔNG nhắc chuyện mời họp");
        assertThat(signals.toneHints().get(1)).startsWith("học sinh nhỏ tuổi");
        assertThat(signals.studentInfo()).containsExactly("mới vào lớp gần đây (theo dữ liệu hệ thống)");
        assertThat(signals.otherTeacherComment()).isEqualTo(new StudentSignalService.OtherTeacherComment(DATE, "Con phản xạ nhanh."));
    }

    @Test
    void describe_UC74_studentWithoutSignalsIsOmitted() {
        when(student.getId()).thenReturn(1L);
        when(schoolClass.getId()).thenReturn(5L);
        ClassEnrollment enrollment = mock(ClassEnrollment.class);
        when(enrollment.getStudent()).thenReturn(student);
        ClassSession current = session(100L, DATE);
        when(classSessionRepository.findSessionsBeforeOrderedDesc(any(), any(), any(), anyList())).thenReturn(List.of());
        when(classSessionRepository.findSessionsBeforeWithTeacherTypeOrderedDesc(any(), any(), any(), any(), anyList())).thenReturn(List.of());

        assertThat(service.describe(current, Map.of(1L, enrollment), Map.of())).isEmpty();
    }
}
