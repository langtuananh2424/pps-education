package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Commendation;

import java.util.List;

public interface CommendationRepository extends JpaRepository<Commendation, Long> {
    List<Commendation> findByEmployeeId(Long employeeId);
}
