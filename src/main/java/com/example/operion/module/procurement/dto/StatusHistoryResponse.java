package com.example.operion.module.procurement.dto;

import java.time.LocalDateTime;

import com.example.operion.module.procurement.enums.ActorKind;

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

    /** BE-22 */
    private ActorKind actorKind;

    /** BE-22: whose action caused an automatic change. */
    private String triggeredByName;

    /** BE-22: what caused it, in words. */
    private String triggerReference;

    private LocalDateTime createdAt;
}
