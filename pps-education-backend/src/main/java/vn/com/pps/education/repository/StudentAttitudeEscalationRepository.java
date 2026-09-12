package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.StudentAttitudeEscalation;

import java.util.List;

public interface StudentAttitudeEscalationRepository extends JpaRepository<StudentAttitudeEscalation, Long> {
    List<StudentAttitudeEscalation> findByStatusAndSchoolClass_Site_IdOrderByCreatedAtAsc(
            StudentAttitudeEscalation.Status status, Long siteId);
}
