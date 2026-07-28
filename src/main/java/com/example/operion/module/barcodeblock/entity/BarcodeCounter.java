package com.example.operion.module.barcodeblock.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Single global row holding the last barcode number handed out. Ranges are
 * reserved by locking this row (see BarcodeCounterRepository.findByIdForUpdate)
 * and advancing lastValue by the requested block size, so two concurrent
 * issuances can never receive overlapping ranges.
 */
@Entity
@Table(name = "barcode_counters")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarcodeCounter {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private Long lastValue = 0L;
}
