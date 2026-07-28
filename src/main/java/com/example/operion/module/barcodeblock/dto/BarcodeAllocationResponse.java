package com.example.operion.module.barcodeblock.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BarcodeAllocationResponse {

    private UUID id;
    private String deviceId;
    private String prefix;
    private Integer padWidth;
    private Long rangeStart;
    private Long rangeEnd;
    private Integer blockSize;
    private String issuedToName;
    private LocalDateTime createdAt;
}
