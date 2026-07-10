package com.example.operion.module.inventory.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.inventory.enums.StockMovementType;
import com.example.operion.module.inventory.enums.StockReferenceType;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id")
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "part_id")
  private Part part;

  @Enumerated(EnumType.STRING)
  private StockMovementType movementType;

  @Enumerated(EnumType.STRING)
  private StockReferenceType referenceType;

  private UUID referenceId;

  private Integer quantity;

  private Integer beforeStock;

  private Integer afterStock;

  @Column(columnDefinition = "TEXT")
  private String notes;

  private LocalDateTime createdAt;

  @PrePersist
  public void prePersist() {

    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}