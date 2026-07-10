package com.example.operion.module.part.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.operion.module.part.entity.PartType;

@Repository
public interface PartTypeRepository
                extends JpaRepository<PartType, UUID> {

        Optional<PartType> findByIdAndTenantId(
                        UUID id,
                        UUID tenantId);

        List<PartType> findByTenantId(
                        UUID tenantId);

        List<PartType> findByCategoryIdAndTenantId(
                        UUID categoryId,
                        UUID tenantId);

        Optional<PartType> findByTenantIdAndCategoryIdAndName(
                        UUID tenantId,
                        UUID categoryId,
                        String name);

        boolean existsById(UUID id);
}
