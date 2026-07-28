package com.example.operion.module.barcodeblock.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.barcodeblock.entity.BarcodeAllocation;

public interface BarcodeAllocationRepository extends JpaRepository<BarcodeAllocation, UUID> {

    List<BarcodeAllocation> findByTenantIdInOrderByCreatedAtDesc(List<UUID> tenantIds);
}
