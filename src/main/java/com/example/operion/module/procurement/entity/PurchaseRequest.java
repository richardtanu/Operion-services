package com.example.operion.module.procurement.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.procurement.enums.PurchaseRequestStatus;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PurchaseRequestStatus status = PurchaseRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id")
    private PurchaseOrder purchaseOrder;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {

        if (status == null) {
            status = PurchaseRequestStatus.PENDING;
        }

        createdAt = LocalDateTime.now();
    }
}
