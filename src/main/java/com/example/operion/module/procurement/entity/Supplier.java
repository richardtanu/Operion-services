package com.example.operion.module.procurement.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    private String contact;

    /**
     * BE-21: this supplier is HQ itself (at most one per tenant, V8). Orders to
     * HQ use it automatically; a mandatory item bought outside an order to HQ
     * must name it as the seller. BE-16 splits spending "from HQ" on it.
     */
    @Column(name = "franchise_centre", nullable = false)
    @Builder.Default
    private boolean franchiseCentre = false;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
