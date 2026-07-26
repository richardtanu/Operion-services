package com.example.operion.module.stockadjustment.entity;

import java.util.UUID;

import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.stockadjustment.enums.StockAdjustmentStatus;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_adjustment_status_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentStatusHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_adjustment_id", nullable = false)
    private StockAdjustment stockAdjustment;

    @Enumerated(EnumType.STRING)
    private StockAdjustmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockAdjustmentStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
