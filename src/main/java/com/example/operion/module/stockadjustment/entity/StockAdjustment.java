package com.example.operion.module.stockadjustment.entity;

import java.util.UUID;

import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.stockadjustment.enums.StockAdjustmentStatus;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_adjustments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(nullable = false)
    private Integer quantity;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StockAdjustmentStatus status = StockAdjustmentStatus.PENDING;

    /**
     * BE-25: the requester approved it themself, allowed only as the store's
     * only Owner+ (V13). Flagged and shown, like BE-20's self-received goods.
     */
    @Column(name = "self_approved", nullable = false)
    @Builder.Default
    private boolean selfApproved = false;

    @PrePersist
    public void prePersist() {

        if (status == null) {
            status = StockAdjustmentStatus.PENDING;
        }
    }
}
