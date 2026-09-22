package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

/**
 * Thông báo in-app trả cho quả chuông (Portal Học sinh/Phụ huynh + app admin).
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 ("Plan: Link hoá
 * thông báo") — 4 field cuối là toạ độ điều hướng để FE mở ĐÚNG con/lớp/bài
 * khi bấm vào thông báo, thay vì chỉ đổi tab chung chung. Cố ý KHÔNG trả
 * nguyên map metadata (hình dạng tuỳ tiện theo từng loại, khó kiểm soát):
 * NotificationService.toResponse() tự chọn field nào có nghĩa theo từng
 * entityType/notificationType; loại nào không liên quan thì để null.
 */
public record NotificationResponse(
        Long id,
        String notificationType,
        String title,
        String content,
        String entityType,
        Long entityId,
        String priority,
        OffsetDateTime createdAt,
        OffsetDateTime readAt,
        /** Học sinh liên quan — Phụ huynh nhiều con dùng để đổi đúng con trước khi hiển thị. */
        Long studentId,
        /** Lớp liên quan — Portal chọn đúng lớp (học sinh học nhiều lớp) trước khi đổi tab. */
        Long classId,
        /** Bản giao Bài tập Ngữ pháp liên quan (exercise_assignments.id) — mở/cuộn tới đúng thẻ BTVN. */
        Long exerciseAssignmentId,
        /** Bản giao Video Ôn tập liên quan (review_video_assignments.id) — mirror exerciseAssignmentId. */
        Long reviewVideoAssignmentId
) {}
