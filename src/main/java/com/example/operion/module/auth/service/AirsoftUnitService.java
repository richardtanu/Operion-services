package com.example.operion.module.auth.service;

import org.springframework.stereotype.Service;

import com.example.operion.module.auth.dto.CreateAirsoftUnitRequest;
import com.example.operion.module.auth.entity.AirsoftUnit;
import com.example.operion.module.auth.enums.UnitStatus;
import com.example.operion.module.auth.repository.AirsoftUnitRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AirsoftUnitService {

    private final AirsoftUnitRepository repository;

    public AirsoftUnit create(CreateAirsoftUnitRequest request) {

        AirsoftUnit unit = AirsoftUnit.builder()
                .serialNumber(request.getSerialNumber())
                .name(request.getName())
                .brand(request.getBrand())
                .model(request.getModel())
                .type(request.getType())
                .purchaseDate(request.getPurchaseDate())
                .notes(request.getNotes())
                .status(UnitStatus.ACTIVE)
                .build();

        return repository.save(unit);
    }
}