package vn.com.pps.education.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.crm.domain.PartnerSchoolInfo;

import java.util.Optional;

public interface PartnerSchoolInfoRepository extends JpaRepository<PartnerSchoolInfo, Long> {
    Optional<PartnerSchoolInfo> findBySiteId(Long siteId);
}
