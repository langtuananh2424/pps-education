package vn.com.pps.education.dto;

import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — hồ sơ học tập tổng
 * hợp của 1 học sinh (FR-REP-04): lớp đã học, điểm tổng kết + điểm từng kỹ
 * năng qua mọi lớp/kỳ, nhận xét, điểm danh. Gộp 1 lần gọi thay cho việc FE
 * tự fan-out hàng trăm request nhỏ (xem StudentProfileService).
 */
public record StudentProfileResponse(
        StudentProfileStudentResponse student,
        List<StudentProfileEnrollmentResponse> enrollments,
        List<StudentProfileGradeResultResponse> gradeResults,
        List<StudentProfileSkillScoreResponse> skillScores,
        List<StudentProfileCommentResponse> comments,
        List<StudentProfileAttendanceResponse> attendance
) {
}
