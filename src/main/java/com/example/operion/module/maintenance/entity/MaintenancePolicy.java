package com.example.operion.module.maintenance.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "maintenance_rule")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenancePolicy {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, unique = true)
  private Tenant tenant;

  @Column(name = "health_threshold", nullable = false)
  @Builder.Default
  private Integer healthThreshold = 50;

  @Column(name = "max_worn_parts", nullable = false)
  @Builder.Default
  private Integer maxWornParts = 2;

  @Column(name = "max_refurbished_parts", nullable = false)
  @Builder.Default
  private Integer maxRefurbishedParts = 3;

  @Column(name = "max_removed_parts", nullable = false)
  @Builder.Default
  private Integer maxRemovedParts = 5;

  @Column(name = "maintenance_interval_days", nullable = false)
  @Builder.Default
  private Integer maintenanceIntervalDays = 90;

  @Column(name = "low_stock_multiplier", nullable = false, precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal lowStockMultiplier = new BigDecimal("1.00");

  @Column(name = "part_end_of_life_warning_days", nullable = false)
  @Builder.Default
  private Integer partEndOfLifeWarningDays = 30;

  @Column(name = "minimum_health_for_operation", nullable = false)
  @Builder.Default
  private Integer minimumHealthForOperation = 60;

  @Column(name = "auto_create_work_order", nullable = false)
  @Builder.Default
  private Boolean autoCreateWorkOrder = true;

  /*
   * =====================================================
   * Burn rate (operion-burn-rate-spec.md §4.2) — nullable,
   * unlike the fields above. This table already has a row per
   * tenant; a NOT NULL column added via ddl-auto=update fails
   * on a populated table (same class of issue as the Supplier.
   * tenant migration on 25 Jul). BurnRateService falls back to
   * the spec's defaults (30/21/10/14) when null.
   * =====================================================
   */

  @Column(name = "burn_rate_window_days")
  private Integer burnRateWindowDays;

  @Column(name = "burn_rate_min_observation_days")
  private Integer burnRateMinObservationDays;

  @Column(name = "burn_rate_min_events")
  private Integer burnRateMinEvents;

  @Column(name = "days_of_cover_target")
  private Integer daysOfCoverTarget;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  public void prePersist() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
