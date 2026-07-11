package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Shift;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
}
