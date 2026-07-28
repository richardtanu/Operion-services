package com.example.operion.module.procurement.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.procurement.enums.PurchaseOrderStatus;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

/**
 * Scope narrowed 28 Jul 2026: this is now the outlet&rarr;center leg only
 * (a PR converted into an internal restock order against the center). Direct
 * marketplace/supplier purchasing goes through {@link PurchaseRequestAuthorization}
 * / {@link Realisasi} instead. The {@code supplier} field below predates that
 * split and is legacy — new code should not rely on a PurchaseOrder carrying
 * a real external supplier.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "po_number", unique = true)
    private String poNumber;

    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    private BigDecimal totalAmount;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {

        if (status == null) {
            status = PurchaseOrderStatus.DRAFT;
        }

        createdAt = LocalDateTime.now();
    }
}
