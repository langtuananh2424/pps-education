package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.StudentHomeworkAlertState;

import java.util.Optional;

public interface StudentHomeworkAlertStateRepository extends JpaRepository<StudentHomeworkAlertState, Long> {
    Optional<StudentHomeworkAlertState> findByStudentIdAndSchoolClassIdAndChannelAndAcademicTermId(
            Long studentId, Long schoolClassId, StudentHomeworkAlertState.Channel channel, Long academicTermId);

    Optional<StudentHomeworkAlertState> findByStudentIdAndSchoolClassIdAndChannelAndAcademicTermIdIsNull(
            Long studentId, Long schoolClassId, StudentHomeworkAlertState.Channel channel);
}
