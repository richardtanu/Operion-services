package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.procurement.entity.PurchaseRequestItem;

public interface PurchaseRequestItemRepository extends JpaRepository<PurchaseRequestItem, UUID> {

    List<PurchaseRequestItem> findByPurchaseRequestId(UUID purchaseRequestId);
}
