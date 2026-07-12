package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Bảng expense_categories (SDD > Tài chính & Học phí > Chi vận hành) — danh mục loại chi, không history. */
@Getter
@Setter
@Entity
@Table(name = "expense_categories")
public class ExpenseCategory {

    public enum Group { HR, FACILITY, TECH, MARKETING, OPERATION, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_group", nullable = false, length = 30)
    private Group categoryGroup;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
