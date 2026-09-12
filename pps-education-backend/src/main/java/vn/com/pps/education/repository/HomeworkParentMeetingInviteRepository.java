package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.HomeworkParentMeetingInvite;

import java.util.List;

public interface HomeworkParentMeetingInviteRepository extends JpaRepository<HomeworkParentMeetingInvite, Long> {
    List<HomeworkParentMeetingInvite> findByStatusAndSchoolClass_Site_IdOrderByCreatedAtAsc(
            HomeworkParentMeetingInvite.Status status, Long siteId);
}
