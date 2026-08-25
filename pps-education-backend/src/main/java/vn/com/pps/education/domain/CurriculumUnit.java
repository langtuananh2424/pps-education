package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng units (V144, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — Unit trong sách giáo
 * trình (VD "UNIT 1: MY NEW SCHOOL"), thuộc 1 {@link Book} (V148 — trước đó gắn thẳng {@link Curriculum},
 * đã đổi vì "Khung chương trình" chỉ là khung, không phải nơi tạo Unit trực tiếp). Mirror đúng pattern
 * {@link CurriculumSubject} — mô tả con trong 1 khung, không có uuid/code/workflow duyệt riêng.
 */
@Getter
@Setter
@Entity
@Table(name = "units")
public class CurriculumUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
