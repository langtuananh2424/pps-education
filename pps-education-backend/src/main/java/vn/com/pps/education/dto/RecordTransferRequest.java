package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-13 Main Flow bước 4, A1: ghi nhận sự kiện chuyển lớp/chuyển điểm
 * trường. Khi transferType là CLASS_CHANGE/BOTH, fromClassId phải trỏ tới
 * lớp học sinh đang ghi danh ACTIVE (1 học sinh có thể có nhiều ghi danh
 * ACTIVE đồng thời ở nhiều lớp khác nhau — xem class_enrollments, Phân hệ
 * 6 — nên hệ thống không tự suy luận được, người dùng phải chỉ định rõ).
 */
public record RecordTransferRequest(
        @NotBlank String transferType,
        Long fromClassId,
        Long toClassId,
        Long toSiteId,
        @NotNull LocalDate effectiveDate,
        String reason
) {}
