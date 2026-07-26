package com.example.operion.module.procurement.entity;

import java.util.UUID;

import com.example.operion.module.part.entity.Part;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_request_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pr_id")
    private PurchaseRequest purchaseRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;

    private Integer quantity;
}
