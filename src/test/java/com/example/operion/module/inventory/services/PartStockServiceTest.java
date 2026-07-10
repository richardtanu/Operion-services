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
import com.example.operion.module.inventory.entity.StockMovement;
import com.example.operion.module.inventory.enums.InventoryStockStatus;
import com.example.operion.module.inventory.repository.StockMovementRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;

@ExtendWith(MockitoExtension.class)
class PartStockServiceTest {

  @Mock
  private PartRepository partRepository;

  @Mock
  private StockMovementRepository movementRepository;

  @InjectMocks
  private PartStockService service;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void lowStockShouldReturnLowStockForPartsBelowMinimum() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setTenantId(tenantId.toString());

    Part part = Part.builder()
        .id(UUID.randomUUID())
        .name("Test Part")
        .currentStock(5)
        .minimumStock(10)
        .build();

    when(partRepository.findByTenantIdOrderByCurrentStockAsc(tenantId)).thenReturn(List.of(part));

    var response = service.lowStock();

    assertEquals(1, response.size());
    assertEquals(InventoryStockStatus.LOW_STOCK, response.get(0).getStatus());
    assertEquals(5, response.get(0).getCurrentStock());
  }
}
