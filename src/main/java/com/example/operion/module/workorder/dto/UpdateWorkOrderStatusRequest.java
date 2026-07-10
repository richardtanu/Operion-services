package com.example.operion.module.workorder.dto;

import com.example.operion.module.workorder.enums.WorkOrderStatus;

import lombok.*;

@Data
public class UpdateWorkOrderStatusRequest {

  private WorkOrderStatus status;
}