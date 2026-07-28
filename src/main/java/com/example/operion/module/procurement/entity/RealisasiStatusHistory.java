package com.example.operion.module.procurement.entity;

import java.util.UUID;

import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.procurement.enums.RealisasiStatus;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "realisasi_status_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealisasiStatusHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realisasi_id", nullable = false)
    private Realisasi realisasi;

    @Enumerated(EnumType.STRING)
    private RealisasiStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RealisasiStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
