package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationStatusHistory;

public interface PurchaseRequestAuthorizationStatusHistoryRepository
        extends JpaRepository<PurchaseRequestAuthorizationStatusHistory, UUID> {

    List<PurchaseRequestAuthorizationStatusHistory> findByPurchaseRequestAuthorization_IdOrderByCreatedAtDesc(
            UUID purchaseRequestAuthorizationId);
}
