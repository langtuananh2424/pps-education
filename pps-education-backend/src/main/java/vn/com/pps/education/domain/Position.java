package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.util.UUID;

/**
 * Bảng positions (chức vụ) — bổ sung ngoài SDD gốc (employees.position_title
 * vốn là text tự do). FR-HRM-06/UC-52: 1 chức vụ ánh xạ N role mặc định
 * (position_default_roles), tự động gán/đồng bộ khi tạo/sửa hồ sơ nhân sự
 * (UC-08 A5). Xem docs/uc/phan-he-04-nhan-su.md.
 */
@Getter
@Setter
@Entity
@Table(name = "positions")
public class Position extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;
}
