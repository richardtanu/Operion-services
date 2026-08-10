package com.example.operion.module.part.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "parts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    private String brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private PartCategory category;

    @Column(name = "category")
    private String legacyCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_type_id")
    private PartType partType;

    private Integer expectedLifespanDays;

    @Column(nullable = false)
    @Builder.Default
    private Integer reorderQuantity = 10;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /*
     * =====================================================
     * Retirement
     * =====================================================
     */

    @Column(nullable = false)
    @Builder.Default
    private Boolean retired = false;

    private LocalDateTime retiredAt;

    @Column(columnDefinition = "TEXT")
    private String retirementReason;

    /*
     * =====================================================
     * Inventory
     * =====================================================
     */

    @Column(name = "current_stock", nullable = false)
    @Builder.Default
    private Integer currentStock = 0;

    @Column(name = "minimum_stock", nullable = false)
    @Builder.Default
    private Integer minimumStock = 0;

    /**
     * Stock units per day, for burn-rate mode MANUAL_RATE — consumables
     * without enough take history yet. Nullable; takes precedence over
     * manualReorderPoint if both are set (operion-burn-rate-spec.md §5).
     */
    @Column(name = "manual_daily_usage", precision = 10, scale = 4)
    private BigDecimal manualDailyUsage;

    /**
     * Minimum stock level, for burn-rate mode MANUAL_LEVEL — spareparts that
     * never get a computed rate (installed on failure, not consumed daily).
     * Nullable, permanent for this item class, not a cold-start fallback.
     */
    @Column(name = "manual_reorder_point")
    private Integer manualReorderPoint;

    /*
     * =====================================================
     * Misc
     * =====================================================
     */

    @Column(columnDefinition = "TEXT")
    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /*
     * =====================================================
     * Lifecycle
     * =====================================================
     */

    /**
     * Reads {@link PartCategory#getConsumable()}, not the category name — see
     * that field's Javadoc. The single call site every service should use
     * instead of comparing {@code getCategory().getName()} to a string
     * literal.
     */
    public boolean isConsumable() {

        return category != null && Boolean.TRUE.equals(category.getConsumable());
    }

    @PrePersist
    public void prePersist() {

        if (currentStock == null) {
            currentStock = 0;
        }

        if (minimumStock == null) {
            minimumStock = 0;
        }

        if (retired == null) {
            retired = false;
        }

        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {

        if (currentStock == null) {
            currentStock = 0;
        }

        if (minimumStock == null) {
            minimumStock = 0;
        }

        if (retired == null) {
            retired = false;
        }

        updatedAt = LocalDateTime.now();
    }
}