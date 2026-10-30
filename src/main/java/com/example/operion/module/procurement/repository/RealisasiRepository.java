package com.example.operion.module.procurement.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.enums.PaymentMethod;
import com.example.operion.module.procurement.enums.RealisasiStatus;
import com.example.operion.module.procurement.enums.ReimbursementStatus;

public interface RealisasiRepository extends JpaRepository<Realisasi, UUID> {

    Optional<Realisasi> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByExternalOrderRef(String externalOrderRef);

    @Query("select r from Realisasi r where r.tenant.id in :tenantIds")
    List<Realisasi> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

    /** BE-16: a store's purchases in one status, purchased in [from, to). */
    @Query("select r from Realisasi r where r.tenant.id = :tenantId and r.status = :status"
            + " and r.purchasedAt >= :from and r.purchasedAt < :to")
    List<Realisasi> findPurchasedBetween(@Param("tenantId") UUID tenantId, @Param("status") RealisasiStatus status,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** BE-41: a store's purchases in the given statuses, not paid with someone's own money, purchased in [from, to). */
    @Query("select r from Realisasi r where r.tenant.id = :tenantId and r.status in :statuses"
            + " and (r.paymentMethod is null or r.paymentMethod <> :personal)"
            + " and r.purchasedAt >= :from and r.purchasedAt < :to")
    List<Realisasi> findCompanyPaidBetween(@Param("tenantId") UUID tenantId,
            @Param("statuses") Collection<RealisasiStatus> statuses, @Param("personal") PaymentMethod personal,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** BE-41: a store's own-money purchases paid back to the buyer in [from, to). */
    @Query("select r from Realisasi r where r.tenant.id = :tenantId and r.reimbursementStatus = :paidBack"
            + " and r.reimbursedAt >= :from and r.reimbursedAt < :to")
    List<Realisasi> findPaidBackBetween(@Param("tenantId") UUID tenantId,
            @Param("paidBack") ReimbursementStatus paidBack,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Realisasi> findByPurchaseRequestAuthorizationId(UUID purchaseRequestAuthorizationId);

    List<Realisasi> findByPurchaseRequestAuthorizationIdAndStatus(
            UUID purchaseRequestAuthorizationId, RealisasiStatus status);
}
