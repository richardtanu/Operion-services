package com.example.operion.module.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;

    private String type;

    private String title;

    private String message;

    private String referenceType;

    private UUID referenceId;

    private boolean read;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
