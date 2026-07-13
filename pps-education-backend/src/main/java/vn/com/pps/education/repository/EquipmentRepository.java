package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Equipment;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByRoomId(Long roomId);

    boolean existsByCode(String code);

    @org.springframework.data.jpa.repository.Query("""
            SELECT e FROM Equipment e JOIN e.room r WHERE r.site.id = :siteId
            """)
    List<Equipment> findBySiteId(@org.springframework.data.repository.query.Param("siteId") Long siteId);
}
