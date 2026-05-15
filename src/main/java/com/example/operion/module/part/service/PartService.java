package com.example.operion.module.part.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.part.dto.CreatePartRequest;
import com.example.operion.module.part.dto.PartResponse;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.part.repository.PartRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartService {

  private final PartRepository repository;

  private final TenantRepository tenantRepository;

  public PartResponse create(
      CreatePartRequest request) {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    Tenant tenant = tenantRepository
        .findById(tenantId)
        .orElseThrow();

    Part part = Part.builder()
        .tenant(tenant)
        .name(request.getName())
        .brand(request.getBrand())
        .category(request.getCategory())
        .expectedLifespanDays(
            request.getExpectedLifespanDays())
        .notes(request.getNotes())
        .build();

    Part saved = repository.save(part);

    return map(saved);
  }

  public List<PartResponse> getAll() {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    return repository.findByTenantId(tenantId)
        .stream()
        .map(this::map)
        .toList();
  }

  private PartResponse map(Part part) {

    return PartResponse.builder()
        .id(part.getId())
        .name(part.getName())
        .brand(part.getBrand())
        .category(part.getCategory())
        .expectedLifespanDays(
            part.getExpectedLifespanDays())
        .notes(part.getNotes())
        .build();
  }
}