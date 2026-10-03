package vn.com.pps.education.hr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.hr.domain.Position;

import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByCode(String code);
}
