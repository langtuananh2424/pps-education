package vn.com.pps.education.hr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.hr.domain.Shift;

import java.util.List;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    boolean existsByCode(String code);

    List<Shift> findAllByOrderByNameAsc();
}
