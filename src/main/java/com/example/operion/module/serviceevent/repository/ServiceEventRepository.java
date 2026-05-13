package com.example.operion.module.serviceevent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.serviceevent.entity.ServiceEvent;

public interface ServiceEventRepository
    extends JpaRepository<ServiceEvent, UUID> {

  List<ServiceEvent> findByTenantId(UUID tenantId);
}