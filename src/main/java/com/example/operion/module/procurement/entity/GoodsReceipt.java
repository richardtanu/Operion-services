package com.example.operion.module.procurement.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

/**
 * Receives against exactly one of {@code purchaseOrder} (outlet&rarr;center
 * leg) or {@code realisasi} (direct marketplace/supplier purchase). See the
 * scope note on {@link PurchaseOrder} — as of 28 Jul 2026 the two FKs are not
 * just an implementation split, they represent genuinely different kinds of
 * purchase.
 */
@Entity
@Table(name = "goods_receipts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Outlet&rarr;center leg only — see {@link PurchaseOrder}'s scope note. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id")
    private PurchaseOrder purchaseOrder;

    /** Direct marketplace/supplier purchase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realisasi_id")
    private Realisasi realisasi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    private LocalDateTime receivedDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    public void prePersist() {

        if (receivedDate == null) {
            receivedDate = LocalDateTime.now();
        }
    }
}
