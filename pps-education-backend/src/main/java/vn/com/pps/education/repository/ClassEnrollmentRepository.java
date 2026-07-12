package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ClassEnrollment;

import java.util.List;
import java.util.Optional;

public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, Long> {

    List<ClassEnrollment> findBySchoolClassId(Long classId);

    List<ClassEnrollment> findBySchoolClassIdAndStatus(Long classId, ClassEnrollment.Status status);

    List<ClassEnrollment> findByStudentId(Long studentId);

    Optional<ClassEnrollment> findBySchoolClassIdAndStudentIdAndStatus(
            Long classId, Long studentId, ClassEnrollment.Status status);

    long countBySchoolClassIdAndStatus(Long classId, ClassEnrollment.Status status);
}
