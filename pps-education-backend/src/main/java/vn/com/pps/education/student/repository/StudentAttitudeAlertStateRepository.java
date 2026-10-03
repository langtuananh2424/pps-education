package vn.com.pps.education.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.student.domain.StudentAttitudeAlertState;

import java.util.Optional;

public interface StudentAttitudeAlertStateRepository extends JpaRepository<StudentAttitudeAlertState, Long> {
    Optional<StudentAttitudeAlertState> findByStudentIdAndSchoolClassIdAndAcademicTermId(
            Long studentId, Long schoolClassId, Long academicTermId);

    Optional<StudentAttitudeAlertState> findByStudentIdAndSchoolClassIdAndAcademicTermIdIsNull(
            Long studentId, Long schoolClassId);
}
