package vn.com.pps.education.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.student.domain.StudentAttitudeEscalation;

import java.util.List;

public interface StudentAttitudeEscalationRepository extends JpaRepository<StudentAttitudeEscalation, Long> {
    List<StudentAttitudeEscalation> findByStatusAndSchoolClass_Site_IdOrderByCreatedAtAsc(
            StudentAttitudeEscalation.Status status, Long siteId);
}
