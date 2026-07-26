package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.procurement.entity.GoodsReceiptItem;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, UUID> {

    List<GoodsReceiptItem> findByGoodsReceiptId(UUID goodsReceiptId);

    List<GoodsReceiptItem> findByGoodsReceipt_PurchaseOrder_Id(UUID purchaseOrderId);
}
