package com.example.operion.module.serviceevent.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.serviceevent.enums.ServiceEventType;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.workorder.entity.WorkOrder;

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

@Entity
@Table(name = "service_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airsoft_unit_id", nullable = false)
    private AirsoftUnit airsoftUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    private User technician;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id")
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private ServiceEventType eventType;

    @Column(columnDefinition = "TEXT")
    private String issue;

    @Column(name = "action_taken", columnDefinition = "TEXT")
    private String actionTaken;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "next_check_date")
    private LocalDate nextCheckDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    void prePersist() {
        if (serviceDate == null) {
            serviceDate = LocalDate.now();
        }
    }
}
