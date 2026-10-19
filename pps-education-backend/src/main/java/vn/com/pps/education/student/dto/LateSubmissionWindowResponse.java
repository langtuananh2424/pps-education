package vn.com.pps.education.student.dto;

import java.time.OffsetDateTime;

/**
 * Cửa nộp muộn của 1 bản giao BTVN (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-19) —
 * để Cổng phụ huynh phân biệt "quá hạn nhưng giáo viên còn cho nộp muộn" (con còn làm được) với "quá
 * hạn và đã khóa", mirror {@code ExerciseAssignment#lateSubmissionAllowed/lateSubmissionDeadline} và
 * {@code AssignedExerciseResponse} bên Portal Học sinh. {@code deadline} NULL = không giới hạn.
 */
public record LateSubmissionWindowResponse(boolean allowed, OffsetDateTime deadline) {}
