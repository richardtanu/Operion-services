package com.example.operion.module.procurement.entity;

import java.util.UUID;

import com.example.operion.module.part.entity.Part;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "goods_receipt_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;

    private Integer quantity;
}
