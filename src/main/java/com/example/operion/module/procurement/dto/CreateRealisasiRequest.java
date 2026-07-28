package com.example.operion.module.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.operion.module.procurement.enums.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRealisasiRequest {

    @NotNull
    private UUID purchaseRequestAuthorizationId;

    @NotBlank
    private String externalOrderRef;

    private String channel;

    private UUID supplierId;

    private String paymentRef;

    @NotNull
    private PaymentMethod paymentMethod;

    private LocalDateTime purchasedAt;

    @NotNull
    private BigDecimal subtotal;

    private BigDecimal sellerDiscount;

    private BigDecimal platformVoucher;

    private BigDecimal shipping;

    private BigDecimal insurance;

    private BigDecimal serviceFee;

    private String attachments;

    private Boolean retroPurchaseFlag;

    @NotEmpty
    @Valid
    private List<RealisasiItemRequest> items;
}
