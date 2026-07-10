package com.example.operion.module.unitpart.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.unitpart.enums.PartCondition;
import com.example.operion.module.unitpart.enums.UnitPartStatus;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "airsoft_unit_parts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirsoftUnitPart {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "airsoft_unit_id", nullable = false)
  private AirsoftUnit airsoftUnit;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "part_id", nullable = false)
  private Part part;

  @Enumerated(EnumType.STRING)
  private PartCondition condition;

  @Enumerated(EnumType.STRING)
  private UnitPartStatus status;

  @Column(columnDefinition = "TEXT")
  private String technicianAssessment;

  private LocalDate installedDate;

  private LocalDate removedDate;

  @Column(columnDefinition = "TEXT")
  private String notes;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  @PrePersist
  public void prePersist() {
    createdAt = LocalDateTime.now();
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}