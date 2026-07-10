package com.example.operion.module.workorder.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignWorkOrderRequest {

    @NotNull
    private UUID technicianId;
}