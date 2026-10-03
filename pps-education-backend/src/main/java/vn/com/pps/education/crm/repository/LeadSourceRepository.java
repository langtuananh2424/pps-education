package vn.com.pps.education.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.crm.domain.LeadSource;

import java.util.Optional;

public interface LeadSourceRepository extends JpaRepository<LeadSource, Long> {
    Optional<LeadSource> findByCode(String code);
}
