package com.example.operion.module.procurement.entity;

import java.util.UUID;

import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.procurement.enums.PurchaseRequestAuthorizationStatus;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_request_authorization_status_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestAuthorizationStatusHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_request_authorization_id", nullable = false)
    private PurchaseRequestAuthorization purchaseRequestAuthorization;

    @Enumerated(EnumType.STRING)
    private PurchaseRequestAuthorizationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseRequestAuthorizationStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    /** BE-22: the server derived this change; changedBy is null and triggeredBy is the cause. */
    @Column(nullable = false)
    @Builder.Default
    private boolean automatic = false;

    /** BE-22: whose action caused an automatic change. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by")
    private User triggeredBy;

    /** BE-22: what caused it, in words ("SHP-123 approved", "PO-… goods received"). */
    @Column(name = "trigger_reference")
    private String triggerReference;
}
