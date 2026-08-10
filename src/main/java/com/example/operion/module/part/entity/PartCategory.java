package com.example.operion.module.part.entity;

import java.util.UUID;

import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "part_categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartCategory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(nullable = false)
  private String name;

  @Column
  private String description;

  /**
   * Stable discriminator for "this category's parts are fungible
   * consumables" — set once at category creation/edit, not re-derived from
   * {@code name} on every read. Renaming or localizing the category (or a
   * tenant creating their own "Consumables" taxonomy) must not silently
   * change this. Nullable on purpose: existing rows predate this column and
   * are backfilled explicitly rather than defaulted by ddl-auto.
   */
  @Column(name = "is_consumable")
  @Builder.Default
  private Boolean consumable = false;
}
