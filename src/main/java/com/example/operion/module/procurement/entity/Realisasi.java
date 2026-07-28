package com.example.operion.module.procurement.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.procurement.enums.PaymentMethod;
import com.example.operion.module.procurement.enums.ReimbursementStatus;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.enums.VarianceStatus;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

/**
 * Realisasi Pembelian — a record of a purchase that already happened, built
 * from the marketplace receipt. Never overwritten in place: a correction
 * creates a new row with `supersedes` pointing at this one, and this row's
 * status flips to SUPERSEDED (see RealisasiService.supersede).
 */
@Entity
@Table(name = "realisasis")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Realisasi extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pra_id", nullable = false)
    private PurchaseRequestAuthorization purchaseRequestAuthorization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "external_order_ref", nullable = false, unique = true)
    private String externalOrderRef;

    private String channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "payment_ref")
    private String paymentRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "reimbursement_status")
    @Builder.Default
    private ReimbursementStatus reimbursementStatus = ReimbursementStatus.NOT_APPLICABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reimbursed_to")
    private User reimbursedTo;

    @Column(name = "reimbursed_at")
    private LocalDateTime reimbursedAt;

    @Column(name = "purchased_at")
    private LocalDateTime purchasedAt;

    private BigDecimal subtotal;

    @Column(name = "seller_discount")
    private BigDecimal sellerDiscount;

    @Column(name = "platform_voucher")
    private BigDecimal platformVoucher;

    private BigDecimal shipping;

    private BigDecimal insurance;

    @Column(name = "service_fee")
    private BigDecimal serviceFee;

    @Column(name = "total_cost")
    private BigDecimal totalCost;

    @Column(columnDefinition = "TEXT")
    private String attachments;

    @Enumerated(EnumType.STRING)
    @Column(name = "variance_status")
    private VarianceStatus varianceStatus;

    @Column(name = "retro_purchase_flag")
    @Builder.Default
    private Boolean retroPurchaseFlag = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RealisasiStatus status = RealisasiStatus.PENDING_APPROVAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private Realisasi supersedes;

    @Column(name = "superseded_reason", columnDefinition = "TEXT")
    private String supersededReason;

    @PrePersist
    public void prePersist() {

        if (status == null) {
            status = RealisasiStatus.PENDING_APPROVAL;
        }

        if (reimbursementStatus == null) {
            reimbursementStatus = ReimbursementStatus.NOT_APPLICABLE;
        }

        if (retroPurchaseFlag == null) {
            retroPurchaseFlag = false;
        }
    }
}
