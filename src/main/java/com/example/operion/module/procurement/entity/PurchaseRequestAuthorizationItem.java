package com.example.operion.module.procurement.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.operion.module.part.entity.Part;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_request_authorization_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestAuthorizationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pra_id")
    private PurchaseRequestAuthorization purchaseRequestAuthorization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;

    @Column(name = "authorized_qty")
    private Integer authorizedQty;

    @Column(name = "max_value")
    private BigDecimal maxValue;
}
