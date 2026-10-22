package com.example.operion.module.tenant.entity;

import com.example.operion.module.auth.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Tenant parent;

    /**
     * BE-16: optional, set by the store's own Owner. Warns in the spending
     * summary when spent + committed goes over it; never blocks a purchase.
     */
    @Column(name = "monthly_purchase_budget", precision = 38, scale = 2)
    private BigDecimal monthlyPurchaseBudget;
}
