package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Lead;

import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByIdAndDeletedAtIsNull(Long id);

    Optional<Lead> findByPhoneAndDeletedAtIsNull(String phone);

    long countByLeadCodeStartingWith(String prefix);

    List<Lead> findByAssignedToIdAndDeletedAtIsNull(Long assignedToId);

    List<Lead> findByStatusInAndDeletedAtIsNull(List<Lead.Status> statuses);
}
