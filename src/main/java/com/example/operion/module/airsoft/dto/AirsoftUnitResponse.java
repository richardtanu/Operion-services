package com.example.operion.module.airsoft.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AirsoftUnitResponse {

  private UUID id;

  private String serialNumber;

  private String name;

  private String brand;

  private String model;

  private String type;

  private String status;
}