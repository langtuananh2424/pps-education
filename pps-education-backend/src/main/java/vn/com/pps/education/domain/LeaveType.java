package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import vn.com.pps.education.common.BaseAuditEntity;

@Entity
@Table(name = "leave_types")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class LeaveType extends BaseAuditEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "code", unique = true, nullable = false, length = 50)
  private String code;

  @Column(name = "label", nullable = false, length = 100)
  private String label;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;
}
