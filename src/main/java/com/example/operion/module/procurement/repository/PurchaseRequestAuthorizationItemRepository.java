package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.procurement.entity.PurchaseRequestAuthorizationItem;

public interface PurchaseRequestAuthorizationItemRepository
        extends JpaRepository<PurchaseRequestAuthorizationItem, UUID> {

    List<PurchaseRequestAuthorizationItem> findByPurchaseRequestAuthorizationId(
            UUID purchaseRequestAuthorizationId);
}
