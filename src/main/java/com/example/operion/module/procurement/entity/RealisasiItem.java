package com.example.operion.module.procurement.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.operion.module.part.entity.Part;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "realisasi_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealisasiItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realisasi_id")
    private Realisasi realisasi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pra_item_id")
    private PurchaseRequestAuthorizationItem purchaseRequestAuthorizationItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;

    @Column(name = "purchase_uom")
    private String purchaseUom;

    @Column(name = "conversion_factor")
    private BigDecimal conversionFactor;

    @Column(name = "purchased_qty")
    private Integer purchasedQty;

    @Column(name = "actual_unit_price")
    private BigDecimal actualUnitPrice;

    @Column(name = "allocated_landed_cost")
    private BigDecimal allocatedLandedCost;
}
