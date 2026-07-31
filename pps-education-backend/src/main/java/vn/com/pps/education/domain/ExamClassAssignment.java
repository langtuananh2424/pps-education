package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng exam_class_assignments (UC-40, bổ sung ngoài SDD gốc, đã xác nhận
 * với người dùng 2026-07-30) — gán 1 {@link Exam} ("Đề") cho 1 lớp, nhiều-
 * nhiều. Là điều kiện hiển thị DUY NHẤT cho học sinh của lớp đó xem/làm
 * được các Bài ({@link Exercise}) thuộc Đề — khung chương trình trên Đề
 * chỉ dùng lọc/tìm kiếm, không phải điều kiện thứ 2 (khác lớp khung
 * chương trình vẫn xem được nếu Đề đã gán). Join thuần (mirror
 * {@link ClassTeacher}/exercise_questions) — không phải "bản giao" cần
 * lưu lịch sử như {@link ExerciseAssignment}, nên KHÔNG có uuid/status; gỡ
 * lớp = xóa cứng dòng này.
 */
@Getter
@Setter
@Entity
@Table(name = "exam_class_assignments")
public class ExamClassAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();
}
