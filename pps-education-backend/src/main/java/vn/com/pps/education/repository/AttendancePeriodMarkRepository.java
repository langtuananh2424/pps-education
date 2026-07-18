package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.AttendancePeriodMark;

import java.util.List;
import java.util.Optional;

public interface AttendancePeriodMarkRepository extends JpaRepository<AttendancePeriodMark, Long> {
    List<AttendancePeriodMark> findByAttendanceMarkId(Long attendanceMarkId);
    Optional<AttendancePeriodMark> findByAttendanceMarkIdAndSessionPeriodId(Long attendanceMarkId, Long sessionPeriodId);
}
