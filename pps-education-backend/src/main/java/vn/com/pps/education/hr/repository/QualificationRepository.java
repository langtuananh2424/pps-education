package vn.com.pps.education.hr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.hr.domain.Qualification;

import java.util.List;

public interface QualificationRepository extends JpaRepository<Qualification, Long> {
    List<Qualification> findByEmployeeId(Long employeeId);
}
