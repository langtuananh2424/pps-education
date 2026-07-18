package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.LeadSource;

import java.util.Optional;

public interface LeadSourceRepository extends JpaRepository<LeadSource, Long> {
    Optional<LeadSource> findByCode(String code);
}
