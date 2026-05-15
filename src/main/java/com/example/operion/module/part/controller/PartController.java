package com.example.operion.module.part.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.part.dto.CreatePartRequest;
import com.example.operion.module.part.dto.PartResponse;
import com.example.operion.module.part.service.PartService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/parts")
@RequiredArgsConstructor
public class PartController {

  private final PartService service;

  @PostMapping
  public ApiResponse<PartResponse> create(
      @RequestBody CreatePartRequest request) {

    return ApiResponse
        .<PartResponse>builder()
        .success(true)
        .message("Part created")
        .data(service.create(request))
        .build();
  }

  @GetMapping
  public ApiResponse<List<PartResponse>> getAll() {

    return ApiResponse
        .<List<PartResponse>>builder()
        .success(true)
        .message("Parts fetched")
        .data(service.getAll())
        .build();
  }
}