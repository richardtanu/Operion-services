package com.example.operion.module.part.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.part.dto.CreatePartTypeRequest;
import com.example.operion.module.part.dto.PartTypeResponse;
import com.example.operion.module.part.dto.UpdatePartTypeRequest;
import com.example.operion.module.part.service.PartTypeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/part-types")
@RequiredArgsConstructor
public class PartTypeController {

  private final PartTypeService service;

  /*
   * CREATE
   */

  @PostMapping
  public ApiResponse<PartTypeResponse> create(
      @RequestBody CreatePartTypeRequest request) {

    return ApiResponse
        .<PartTypeResponse>builder()
        .success(true)
        .message("Part Type created")
        .data(service.create(request))
        .build();
  }

  /*
   * SEED DEFAULTS
   */

  @PostMapping("/seed")
  public ApiResponse<Void> seed() {

    UUID tenantId = UUID.fromString(
        TenantContext.getTenantId());

    service.seedDefaultPartTypes(tenantId);

    return ApiResponse.<Void>builder()
        .success(true)
        .message("Default part types created")
        .build();
  }

  /*
   * GET ALL
   */

  @GetMapping
  public ApiResponse<List<PartTypeResponse>> getAll() {

    return ApiResponse
        .<List<PartTypeResponse>>builder()
        .success(true)
        .message("Part Types fetched")
        .data(service.getAll())
        .build();
  }

  /*
   * GET BY CATEGORY
   */

  @GetMapping("/category/{categoryId}")
  public ApiResponse<List<PartTypeResponse>> getByCategory(
      @PathVariable UUID categoryId) {

    return ApiResponse
        .<List<PartTypeResponse>>builder()
        .success(true)
        .message("Part Types fetched")
        .data(service.getByCategory(categoryId))
        .build();
  }

  /*
   * UPDATE
   */

  @PutMapping("/{id}")
  public ApiResponse<PartTypeResponse> update(

      @PathVariable UUID id,

      @RequestBody UpdatePartTypeRequest request) {

    return ApiResponse
        .<PartTypeResponse>builder()
        .success(true)
        .message("Part Type updated")
        .data(service.update(id, request))
        .build();
  }

  /*
   * DELETE
   */

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(
      @PathVariable UUID id) {

    service.delete(id);

    return ApiResponse
        .<Void>builder()
        .success(true)
        .message("Part Type deleted")
        .build();
  }

}
