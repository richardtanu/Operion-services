package com.example.operion.module.procurement.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.operion.module.procurement.enums.ReimbursementMethod;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** BE-18: settle one owed purchase or a whole pile at once. All-or-nothing. */
@Data
public class MarkReimbursedRequest {

    @NotEmpty(message = "At least one purchase is required")
    private List<UUID> ids;

    @NotNull(message = "Paid date is required")
    private LocalDate paidAt;

    @NotNull(message = "Method is required")
    private ReimbursementMethod method;

    /** Optional transfer reference or note. */
    private String reference;
}
