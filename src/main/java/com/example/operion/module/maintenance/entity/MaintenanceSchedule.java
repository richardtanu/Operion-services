package com.example.operion.module.maintenance.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceSchedule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id")
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "airsoft_unit_id")
  private AirsoftUnit airsoftUnit;

  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  private Integer intervalDays;

  private LocalDate lastMaintenanceDate;

  private LocalDate nextDueDate;

  private Boolean autoGenerateWorkOrder;

  private Boolean active;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  @PrePersist
  public void prePersist() {

    LocalDateTime now = LocalDateTime.now();

    createdAt = now;
    updatedAt = now;

    if (active == null) {
      active = true;
    }

    if (autoGenerateWorkOrder == null) {
      autoGenerateWorkOrder = true;
    }
  }

  @PreUpdate
  public void preUpdate() {

    updatedAt = LocalDateTime.now();
  }
}