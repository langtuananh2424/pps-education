package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leave_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveType {
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
