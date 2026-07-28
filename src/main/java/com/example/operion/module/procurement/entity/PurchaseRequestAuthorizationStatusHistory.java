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
}
