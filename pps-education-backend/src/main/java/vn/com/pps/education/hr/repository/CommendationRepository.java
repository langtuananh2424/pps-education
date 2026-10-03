package vn.com.pps.education.hr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.hr.domain.Commendation;

import java.util.List;

public interface CommendationRepository extends JpaRepository<Commendation, Long> {
    List<Commendation> findByEmployeeId(Long employeeId);
}
