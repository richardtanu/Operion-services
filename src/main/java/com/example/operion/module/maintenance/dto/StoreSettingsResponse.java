package com.example.operion.module.maintenance.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/** BE-38: a store's effective settings, defaults filled in. */
@Data
@Builder
public class StoreSettingsResponse {

    private UUID tenantId;
    private String tenantName;

    /** False: the store never saved any; every value below is a default. */
    private boolean customised;

    private int daysOfCoverTarget;
    private int burnRateWindowDays;
    private int burnRateMinObservationDays;
    private int burnRateMinEvents;

    private int healthThreshold;
    private int minimumHealthForOperation;
    private int maintenanceIntervalDays;
    private int partEndOfLifeWarningDays;
    private int maxWornParts;
    private int maxRefurbishedParts;
    private int maxRemovedParts;
    private boolean autoCreateWorkOrder;
}
