package com.example.operion.module.inventory.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.operion.module.inventory.entity.StockMovement;
import com.example.operion.module.inventory.enums.StockMovementType;
import com.example.operion.module.part.entity.Part;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findByPartIdOrderByCreatedAtDesc(
            UUID partId);

    List<StockMovement> findByMovementTypeAndCreatedAtAfter(
            StockMovementType movementType,
            LocalDateTime date);

    List<StockMovement> findByMovementTypeInAndCreatedAtAfter(
            List<StockMovementType> types,
            LocalDateTime date);
}
