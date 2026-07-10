package com.example.operion.module.parthistory.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.operion.module.inventory.dto.StockMovementResponse;
import com.example.operion.module.inventory.entity.StockMovement;
import com.example.operion.module.parthistory.dto.PartConditionHistoryResponse;
import com.example.operion.module.parthistory.entity.PartConditionHistory;
import com.example.operion.module.parthistory.repository.PartConditionHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartConditionHistoryService {

        private final PartConditionHistoryRepository repository;

        public List<PartConditionHistoryResponse> getHistory(UUID unitPartId) {

                List<PartConditionHistory> data = repository.findByAirsoftUnitPart_IdOrderByCreatedAtDesc(
                                unitPartId);

                // System.out.println("HISTORY SIZE = " + data.size());

                data.forEach(item -> System.out.println(
                                item.getId() + " | " + item.getReason()));

                return repository
                                .findByAirsoftUnitPart_IdOrderByCreatedAtDesc(
                                                unitPartId)
                                .stream()
                                .map(this::map)
                                .toList();
        }

        private PartConditionHistoryResponse map(
                        PartConditionHistory history) {

                return PartConditionHistoryResponse.builder()
                                .id(history.getId())
                                .previousCondition(
                                                history.getPreviousCondition())
                                .newCondition(
                                                history.getNewCondition())
                                .reason(
                                                history.getReason())
                                .technicianAssessment(
                                                history.getTechnicianAssessment())
                                .createdAt(
                                                history.getCreatedAt())
                                .build();
        }

        private StockMovementResponse map(
                        StockMovement movement) {

                return StockMovementResponse.builder()
                                .id(movement.getId())
                                .partName(movement.getPart().getName())
                                .movementType(movement.getMovementType())
                                .quantity(movement.getQuantity())
                                .beforeStock(movement.getBeforeStock())
                                .afterStock(movement.getAfterStock())
                                .notes(movement.getNotes())
                                .createdAt(movement.getCreatedAt())

                                .build();
        }
}