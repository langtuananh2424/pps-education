package vn.com.pps.education.dto;

import java.util.List;

/** Chi tiết 1 giáo viên trong trang "Hồ sơ giáo viên" (V203) — xem TeacherProfileSummaryResponse. */
public record TeacherProfileDetailResponse(
        TeacherProfileSummaryResponse profile,
        List<QualificationResponse> qualifications,
        List<TeacherCommendationItem> commendations,
        List<TeacherClassAssignmentItem> classes
) {}
