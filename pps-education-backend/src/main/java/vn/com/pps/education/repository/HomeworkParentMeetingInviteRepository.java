package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.HomeworkParentMeetingInvite;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface HomeworkParentMeetingInviteRepository extends JpaRepository<HomeworkParentMeetingInvite, Long> {
    List<HomeworkParentMeetingInvite> findByStatusAndSchoolClass_Site_IdOrderByCreatedAtAsc(
            HomeworkParentMeetingInvite.Status status, Long siteId);

    /**
     * UC-74 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) — lời mời họp phụ huynh vì thiếu BTVN
     * còn hiệu lực của nhiều học sinh trong 1 lớp, để trợ lý nhận xét chỉnh giọng văn (không viết chuyện mời họp).
     */
    List<HomeworkParentMeetingInvite> findByStudentIdInAndSchoolClassIdAndStatusInAndCreatedAtGreaterThanEqual(
            Collection<Long> studentIds, Long schoolClassId, Collection<HomeworkParentMeetingInvite.Status> statuses,
            OffsetDateTime createdFrom);
}
