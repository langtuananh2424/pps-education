package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Duyệt/từ chối theo lô (truyền nhiều id cùng lúc) — mirror DecideHomeworkParentMeetingInvitesRequest. */
public record DecideStudentAttitudeEscalationsRequest(
        @NotEmpty List<Long> escalationIds,
        @NotBlank String decision,
        String comment
) {}
