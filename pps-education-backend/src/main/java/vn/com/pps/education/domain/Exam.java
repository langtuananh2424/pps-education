package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.util.UUID;

/**
 * Bảng exams (UC-40, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-30) — "Đề" (VD: IELTS Grade 6), cấp cha của nhiều "Bài"
 * ({@link Exercise}, VD: Unit 1). curriculum CHỈ dùng để lọc/tìm kiếm
 * trong Kho đề — không phải điều kiện hiển thị cho lớp (xem
 * {@link ExamClassAssignment}, điều kiện hiển thị DUY NHẤT).
 */
@Getter
@Setter
@Entity
@Table(name = "exams")
public class Exam extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 500)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
