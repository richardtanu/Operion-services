package com.example.operion.module.stockadjustment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.stockadjustment.entity.StockAdjustmentStatusHistory;

public interface StockAdjustmentStatusHistoryRepository
        extends JpaRepository<StockAdjustmentStatusHistory, UUID> {

    List<StockAdjustmentStatusHistory> findByStockAdjustment_IdOrderByCreatedAtDesc(UUID stockAdjustmentId);
}
