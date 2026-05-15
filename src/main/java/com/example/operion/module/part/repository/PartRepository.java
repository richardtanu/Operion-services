package com.example.operion.module.part.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.part.entity.Part;

public interface PartRepository
    extends JpaRepository<Part, UUID> {

  List<Part> findByTenantId(UUID tenantId);
}