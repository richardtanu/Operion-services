package com.example.operion.module.procurement.entity;

import java.util.UUID;

import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

/**
 * PRA (Purchase Request Authorization) — an authorization ceiling (max qty +
 * max value per line), not an instruction to buy. Created directly by the
 * Purchasing/Finance PIC; creation IS the authorization act, so there is no
 * separate approve step on this entity itself. One or more Realisasi records
 * are created against it over time (partial fulfillment is normal).
 */
@Entity
@Table(name = "purchase_request_authorizations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestAuthorization extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PurchaseRequestAuthorizationStatus status = PurchaseRequestAuthorizationStatus.ACTIVE;

    @PrePersist
    public void prePersist() {

        if (status == null) {
            status = PurchaseRequestAuthorizationStatus.ACTIVE;
        }
    }
}
