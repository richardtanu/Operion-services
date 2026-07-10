package com.example.operion.module.part.entity;

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