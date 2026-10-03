package vn.com.pps.education.permission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.permission.domain.PositionDefaultRole;

import java.util.List;

public interface PositionDefaultRoleRepository extends JpaRepository<PositionDefaultRole, Long> {

    List<PositionDefaultRole> findByPositionId(Long positionId);
}
