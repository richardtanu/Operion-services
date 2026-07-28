package com.example.operion.module.barcodeblock.entity;

import java.util.UUID;

import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.*;
import lombok.*;

/**
 * Audit record of a barcode code-block issued to a device (DL-06). The
 * device generates codes offline as prefix + zero-padded number for every
 * value in [rangeStart, rangeEnd] without needing to contact the server
 * again, since ranges are never reissued.
 */
@Entity
@Table(name = "barcode_allocations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarcodeAllocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_to")
    private User issuedTo;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String prefix;

    @Column(name = "pad_width", nullable = false)
    private Integer padWidth;

    @Column(name = "range_start", nullable = false)
    private Long rangeStart;

    @Column(name = "range_end", nullable = false)
    private Long rangeEnd;
}
