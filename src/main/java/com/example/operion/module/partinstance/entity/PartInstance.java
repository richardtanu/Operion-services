package com.example.operion.module.partinstance.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.partinstance.enums.PartInstanceStatus;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "part_instances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Column(nullable = false, unique = true)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PartInstanceStatus status = PartInstanceStatus.IN_STOCK;

    private LocalDateTime receivedAt;

    private LocalDateTime takenAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taken_by")
    private User takenBy;

    private LocalDateTime exhaustedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installed_unit_part_id")
    private AirsoftUnitPart installedUnitPart;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    public void prePersist() {

        if (status == null) {
            status = PartInstanceStatus.IN_STOCK;
        }

        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
