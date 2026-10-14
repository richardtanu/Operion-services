package com.example.operion.module.inventory.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.inventory.enums.InventoryStockStatus;
import com.example.operion.module.inventory.repository.StockMovementRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;

@ExtendWith(MockitoExtension.class)
class PartStockServiceTest {

  @Mock
  private PartRepository partRepository;

  @Mock
  private StockMovementRepository movementRepository;

  @Mock
  private TenantHierarchyService tenantHierarchyService;

  @InjectMocks
  private PartStockService service;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void lowStockShouldReturnLowStockPartsAcrossTheTenantTree() {
    UUID hqId = UUID.randomUUID();
    UUID outletId = UUID.randomUUID();
    TenantContext.setTenantId(hqId.toString());

    // Two tenants, not one: with a single tenant, "includes descendants"
    // and "ignores them" produce the same output (CLAUDE.md rule 6).
    // Mockito strict stubs fail the test if the repository is queried with
    // anything other than the full tree.
    when(tenantHierarchyService.getEffectiveTenantIds(hqId)).thenReturn(List.of(hqId, outletId));

    Part hqLow = part("HQ low", 5, 10);
    Part outletLow = part("Outlet low", 2, 8);
    Part outletHealthy = part("Outlet healthy", 20, 8);

    when(partRepository.findByTenantIdInOrderByCurrentStockAsc(List.of(hqId, outletId)))
        .thenReturn(List.of(outletLow, hqLow, outletHealthy));

    var response = service.lowStock();

    assertEquals(2, response.size());
    assertEquals(InventoryStockStatus.LOW_STOCK, response.get(0).getStatus());
    assertEquals(2, response.get(0).getCurrentStock());
    assertEquals(InventoryStockStatus.LOW_STOCK, response.get(1).getStatus());
    assertEquals(5, response.get(1).getCurrentStock());
  }

  private static Part part(String name, int currentStock, int minimumStock) {
    return Part.builder()
        .id(UUID.randomUUID())
        .name(name)
        .currentStock(currentStock)
        .minimumStock(minimumStock)
        .build();
  }
}
