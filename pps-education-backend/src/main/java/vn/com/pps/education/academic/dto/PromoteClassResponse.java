package vn.com.pps.education.academic.dto;

import vn.com.pps.education.lms.dto.ClassResponse;

import java.util.List;

/** Kết quả chuyển lớp hàng loạt — xem ClassService#promoteClass. */
public record PromoteClassResponse(
        ClassResponse newClass,
        Long oldClassId,
        int movedStudentCount,
        int skippedStudentCount,
        List<SkippedStudentInfo> skippedStudents
) {
    public record SkippedStudentInfo(
            Long studentId,
            String studentCode,
            String studentFullName,
            String reason
    ) {}
}
