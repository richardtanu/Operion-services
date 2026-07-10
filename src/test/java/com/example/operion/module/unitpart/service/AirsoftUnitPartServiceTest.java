package com.example.operion.module.unitpart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.airsoft.repository.AirsoftUnitRepository;
import com.example.operion.module.airsoft.service.UnitOperationalRuleService;
import com.example.operion.module.analytics.service.UnitHealthService;
import com.example.operion.module.inventory.services.PartStockService;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.entity.PartCategory;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.parthistory.repository.PartConditionHistoryRepository;
import com.example.operion.module.serviceevent.repository.ServiceEventRepository;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.unitpart.dto.AirsoftUnitPartResponse;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.UnitPartStatus;
import com.example.operion.module.unitpart.repository.AirsoftUnitPartRepository;
import com.example.operion.module.workorder.repository.WorkOrderRepository;
import com.example.operion.module.workorder.service.WorkOrderService;

@ExtendWith(MockitoExtension.class)
class AirsoftUnitPartServiceTest {

  @Mock
  private UnitOperationalRuleService operationalRuleService;
  @Mock
  private UnitHealthService unitHealthService;
  @Mock
  private AirsoftUnitPartRepository repository;
  @Mock
  private TenantRepository tenantRepository;
  @Mock
  private AirsoftUnitRepository airsoftUnitRepository;
  @Mock
  private PartRepository partRepository;
  @Mock
  private PartConditionHistoryRepository historyRepository;
  @Mock
  private WorkOrderService workOrderService;
  @Mock
  private WorkOrderRepository workOrderRepository;
  @Mock
  private ServiceEventRepository serviceEventRepository;
  @Mock
  private PartStockService stockAdjustmentRequest;

  @InjectMocks
  private AirsoftUnitPartService service;

  @Test
  void getInstalledPartsShouldExposeCategoryNameAndId() {
    UUID unitId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();

    PartCategory category = PartCategory.builder()
        .id(categoryId)
        .name("Inner Barrel")
        .build();

    Part part = Part.builder()
        .name("Cylinder Head Mushroom")
        .brand("Brand")
        .category(category)
        .build();

    AirsoftUnit unit = AirsoftUnit.builder()
        .name("Test Unit")
        .build();

    AirsoftUnitPart installedPart = AirsoftUnitPart.builder()
        .airsoftUnit(unit)
        .part(part)
        .status(UnitPartStatus.INSTALLED)
        .build();

    when(repository.findByAirsoftUnitIdAndStatus(unitId, UnitPartStatus.INSTALLED))
        .thenReturn(List.of(installedPart));

    List<AirsoftUnitPartResponse> response = service.getInstalledParts(unitId);

    assertEquals(1, response.size());
    assertEquals("Inner Barrel", response.get(0).getCategory());
    assertEquals("Inner Barrel", response.get(0).getCategoryName());
    assertEquals(categoryId, response.get(0).getCategoryId());
  }
}
