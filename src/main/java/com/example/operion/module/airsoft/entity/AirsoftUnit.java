package com.example.operion.module.airsoft.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.airsoft.enums.OperationalSeverity;
import com.example.operion.module.airsoft.enums.UnitStatus;
import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "airsoft_units")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class AirsoftUnit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String serialNumber;

    @Column(nullable = false)
    private String name;

    private String brand;

    private String model;

    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_severity")
    private OperationalSeverity operationalSeverity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitStatus status;

    private Integer healthScore;

    private LocalDate purchaseDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @PrePersist
    public void prePersistAirsoftUnit() {

        if (healthScore == null) {
            healthScore = 100;
        }
    }

}
