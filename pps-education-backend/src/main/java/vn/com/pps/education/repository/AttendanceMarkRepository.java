package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.AttendanceMark;

import java.util.List;
import java.util.Optional;

public interface AttendanceMarkRepository extends JpaRepository<AttendanceMark, Long> {
    List<AttendanceMark> findByAttendanceSessionId(Long attendanceSessionId);
    Optional<AttendanceMark> findByAttendanceSessionIdAndStudentId(Long attendanceSessionId, Long studentId);
    List<AttendanceMark> findByStudentId(Long studentId);
}
