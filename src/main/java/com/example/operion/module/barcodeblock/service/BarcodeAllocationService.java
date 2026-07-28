package com.example.operion.module.barcodeblock.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.barcodeblock.dto.BarcodeAllocationResponse;
import com.example.operion.module.barcodeblock.dto.IssueBarcodeBlockRequest;
import com.example.operion.module.barcodeblock.entity.BarcodeAllocation;
import com.example.operion.module.barcodeblock.entity.BarcodeCounter;
import com.example.operion.module.barcodeblock.repository.BarcodeAllocationRepository;
import com.example.operion.module.barcodeblock.repository.BarcodeCounterRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Issues barcode code-blocks to a device on login (DL-06). The mobile client
 * generates codes offline as prefix + zero-padded number for every value in
 * [rangeStart, rangeEnd], without ever contacting the server per-tag, and
 * without colliding with another device's block since ranges are reserved
 * atomically via a locked counter and never reissued.
 */
@Service
@RequiredArgsConstructor
public class BarcodeAllocationService {

    private static final String PREFIX = "OFF-";
    private static final int PAD_WIDTH = 10;
    private static final int DEFAULT_BLOCK_SIZE = 500;

    private final BarcodeCounterRepository counterRepository;

    private final BarcodeAllocationRepository allocationRepository;

    private final TenantRepository tenantRepository;

    private final UserRepository userRepository;

    private final TenantHierarchyService tenantHierarchyService;

    @Transactional
    public BarcodeAllocationResponse issue(IssueBarcodeBlockRequest request) {

        int blockSize = request.getBlockSize() != null
                ? request.getBlockSize()
                : DEFAULT_BLOCK_SIZE;

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow();

        User issuedTo = currentUser();

        BarcodeCounter counter = counterRepository
                .findByIdForUpdate(BarcodeCounter.SINGLETON_ID)
                .orElseGet(() -> counterRepository.save(
                        BarcodeCounter.builder()
                                .id(BarcodeCounter.SINGLETON_ID)
                                .lastValue(0L)
                                .build()));

        long rangeStart = counter.getLastValue() + 1;
        long rangeEnd = counter.getLastValue() + blockSize;

        counter.setLastValue(rangeEnd);
        counterRepository.save(counter);

        BarcodeAllocation allocation = BarcodeAllocation.builder()
                .tenant(tenant)
                .issuedTo(issuedTo)
                .deviceId(request.getDeviceId())
                .prefix(PREFIX)
                .padWidth(PAD_WIDTH)
                .rangeStart(rangeStart)
                .rangeEnd(rangeEnd)
                .build();

        return map(allocationRepository.save(allocation));
    }

    public List<BarcodeAllocationResponse> getAll() {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(tenantId);

        return allocationRepository.findByTenantIdInOrderByCreatedAtDesc(tenantIds)
                .stream()
                .map(this::map)
                .toList();
    }

    private User currentUser() {

        UUID userId = UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private BarcodeAllocationResponse map(BarcodeAllocation allocation) {

        return BarcodeAllocationResponse.builder()
                .id(allocation.getId())
                .deviceId(allocation.getDeviceId())
                .prefix(allocation.getPrefix())
                .padWidth(allocation.getPadWidth())
                .rangeStart(allocation.getRangeStart())
                .rangeEnd(allocation.getRangeEnd())
                .blockSize((int) (allocation.getRangeEnd() - allocation.getRangeStart() + 1))
                .issuedToName(allocation.getIssuedTo() != null
                        ? allocation.getIssuedTo().getFullName()
                        : null)
                .createdAt(allocation.getCreatedAt())
                .build();
    }
}
