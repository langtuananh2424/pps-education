package vn.com.pps.education.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.crm.domain.PartnerSchoolInfoHistory;

public interface PartnerSchoolInfoHistoryRepository extends JpaRepository<PartnerSchoolInfoHistory, Long> {

    void deleteByPartnerSchoolInfoId(Long partnerSchoolInfoId);
}
