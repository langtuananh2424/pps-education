package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.PositionDefaultRole;

import java.util.List;

public interface PositionDefaultRoleRepository extends JpaRepository<PositionDefaultRole, Long> {

    List<PositionDefaultRole> findByPositionId(Long positionId);
}
