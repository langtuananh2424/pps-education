package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.util.UUID;

/**
 * Bảng rooms (SDD > Cơ sở vật chất & Điểm trường > b, migration V2) —
 * phòng học. Entity tối thiểu — chỉ đủ cho quan hệ FK từ class_sessions
 * (kiểm tra trùng phòng FR-FAC-03). Chưa có Controller/Service CRUD riêng
 * cho Phân hệ 10 (Cơ sở vật chất) trong phạm vi hiện tại.
 */
@Getter
@Setter
@Entity
@Table(name = "rooms")
public class Room extends BaseAuditEntity {

    public enum RoomType { THEORY, COMPUTER, LAB, OTHER }

    public enum Status { AVAILABLE, MAINTENANCE, DISABLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 30)
    private RoomType roomType;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "is_flexible", nullable = false)
    private boolean flexible = false;

    @Column(name = "managed_by_center", nullable = false)
    private boolean managedByCenter = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.AVAILABLE;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
