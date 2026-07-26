package com.example.operion.module.stockadjustment.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class StatusHistoryResponse {

    private String previousStatus;

    private String newStatus;

    private String changedByName;

    private String reason;

    private LocalDateTime createdAt;
}
