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
import com.example.operion.module.tenant.entity.Tenant;
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
    // BE-26: list reads go through readScope (no ?tenantId= = the caller's whole tree)
    when(tenantHierarchyService.readScope(null)).thenReturn(List.of(hqId, outletId));
    Tenant hq = Tenant.builder().id(hqId).name("HQ").build();
    Tenant outlet = Tenant.builder().id(outletId).name("Outlet").build();

    Part hqLow = part("HQ low", 5, 10, hq);
    Part outletLow = part("Outlet low", 2, 8, outlet);
    Part outletHealthy = part("Outlet healthy", 20, 8, outlet);

    when(partRepository.findByTenantIdInOrderByCurrentStockAsc(List.of(hqId, outletId)))
        .thenReturn(List.of(outletLow, hqLow, outletHealthy));

    var response = service.lowStock(null);

    assertEquals(2, response.size());
    assertEquals(InventoryStockStatus.LOW_STOCK, response.get(0).getStatus());
    assertEquals(2, response.get(0).getCurrentStock());
    assertEquals(InventoryStockStatus.LOW_STOCK, response.get(1).getStatus());
    assertEquals(5, response.get(1).getCurrentStock());
    // BE-26: each row says which store it belongs to
    assertEquals(outletId, response.get(0).getTenantId());
    assertEquals(hqId, response.get(1).getTenantId());
  }

  private static Part part(String name, int currentStock, int minimumStock, Tenant tenant) {
    return Part.builder()
        .id(UUID.randomUUID())
        .name(name)
        .tenant(tenant)
        .currentStock(currentStock)
        .minimumStock(minimumStock)
        .build();
  }
}
