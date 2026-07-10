package com.example.operion.module.workorder.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.workorder.enums.WorkOrderPriority;
import com.example.operion.module.workorder.enums.WorkOrderStatus;
import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.maintenance.entity.MaintenanceSchedule;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.maintenance.entity.MaintenanceSchedule;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "work_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id")
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "airsoft_unit_id")
  private AirsoftUnit airsoftUnit;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "airsoft_unit_part_id")
  private AirsoftUnitPart airsoftUnitPart;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_technician_id")
  private User assignedTechnician;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "maintenance_schedule_id")
  private MaintenanceSchedule maintenanceSchedule;

  @Enumerated(EnumType.STRING)
  private WorkOrderPriority priority;

  @Enumerated(EnumType.STRING)
  private WorkOrderStatus status = WorkOrderStatus.OPEN;

  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  private LocalDate targetDate;

  private LocalDateTime assignedAt;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  private LocalDateTime startedAt;

  private LocalDateTime completedAt;

  private LocalDate completedDate;

  @PrePersist
  public void prePersist() {

    LocalDateTime now = LocalDateTime.now();

    if (createdAt == null) {
      createdAt = now;
    }

    updatedAt = now;

    if (status == null) {
      status = WorkOrderStatus.OPEN;
    }

    if (assignedTechnician != null
        && assignedAt == null) {

      assignedAt = now;
    }
  }

  @PreUpdate
  public void preUpdate() {

    updatedAt = LocalDateTime.now();
  }
}