package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng parents (SDD > Học sinh & Phụ huynh > b). Mở rộng 1-1 từ users cho
 * phụ huynh (UC-13 Main Flow bước 2). Không soft-delete — gỡ liên kết qua
 * parent_student thay vì xóa hồ sơ (theo SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "parents")
public class Parent extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 200)
    private String occupation;

    @Column(length = 500)
    private String workplace;

    @Column(length = 500)
    private String address;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
