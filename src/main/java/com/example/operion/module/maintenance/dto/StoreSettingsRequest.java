package com.example.operion.module.maintenance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * BE-38: {@code PATCH /tenants/{id}/settings}. Every field is optional: one
 * left out keeps its current value.
 */
@Data
public class StoreSettingsRequest {

    // stock: when an item counts as low, and how burn rate is measured
    @Min(value = 1, message = "Target days must be 1 to 365") @Max(value = 365, message = "Target days must be 1 to 365")
    private Integer daysOfCoverTarget;

    @Min(value = 7, message = "The burn-rate window must be 7 to 365 days") @Max(value = 365, message = "The burn-rate window must be 7 to 365 days")
    private Integer burnRateWindowDays;

    @Min(value = 1, message = "Minimum observation must be 1 to 365 days") @Max(value = 365, message = "Minimum observation must be 1 to 365 days")
    private Integer burnRateMinObservationDays;

    @Min(value = 1, message = "Minimum events must be 1 to 1000") @Max(value = 1000, message = "Minimum events must be 1 to 1000")
    private Integer burnRateMinEvents;

    // maintenance: when a unit needs attention
    @Min(value = 0, message = "Health threshold must be 0 to 100") @Max(value = 100, message = "Health threshold must be 0 to 100")
    private Integer healthThreshold;

    @Min(value = 0, message = "Minimum health must be 0 to 100") @Max(value = 100, message = "Minimum health must be 0 to 100")
    private Integer minimumHealthForOperation;

    @Min(value = 1, message = "Maintenance interval must be 1 to 3650 days") @Max(value = 3650, message = "Maintenance interval must be 1 to 3650 days")
    private Integer maintenanceIntervalDays;

    @Min(value = 0, message = "End-of-life warning must be 0 to 365 days") @Max(value = 365, message = "End-of-life warning must be 0 to 365 days")
    private Integer partEndOfLifeWarningDays;

    @Min(value = 0, message = "Must be 0 to 100") @Max(value = 100, message = "Must be 0 to 100")
    private Integer maxWornParts;

    @Min(value = 0, message = "Must be 0 to 100") @Max(value = 100, message = "Must be 0 to 100")
    private Integer maxRefurbishedParts;

    @Min(value = 0, message = "Must be 0 to 100") @Max(value = 100, message = "Must be 0 to 100")
    private Integer maxRemovedParts;

    private Boolean autoCreateWorkOrder;
}
