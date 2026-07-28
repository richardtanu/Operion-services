package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.procurement.entity.RealisasiItem;
import com.example.operion.module.procurement.enums.RealisasiStatus;

public interface RealisasiItemRepository extends JpaRepository<RealisasiItem, UUID> {

    List<RealisasiItem> findByRealisasiId(UUID realisasiId);

    List<RealisasiItem> findByPurchaseRequestAuthorizationItemIdAndRealisasi_Status(
            UUID purchaseRequestAuthorizationItemId, RealisasiStatus status);
}
