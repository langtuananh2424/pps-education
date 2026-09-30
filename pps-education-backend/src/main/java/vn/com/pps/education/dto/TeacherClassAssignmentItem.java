package vn.com.pps.education.dto;

import java.time.LocalDate;

/** Lớp giáo viên đang phụ trách — tab "Lớp phụ trách" trong Hồ sơ giáo viên (V203). */
public record TeacherClassAssignmentItem(
        Long classId,
        String classCode,
        String className,
        String siteName,
        String classStatus,
        String teacherRole,
        String teacherType,
        LocalDate assignedFrom
) {}
