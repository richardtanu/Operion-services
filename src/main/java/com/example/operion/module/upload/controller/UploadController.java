package com.example.operion.module.upload.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.upload.dto.CreateUploadRequest;
import com.example.operion.module.upload.dto.UploadTicketResponse;
import com.example.operion.module.upload.dto.UploadViewResponse;
import com.example.operion.module.upload.service.UploadService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * BE-23: photos and receipts. Two steps for the phone: ask for an upload URL
 * here, PUT the file straight to storage, then send the {@code uploadId} with
 * the record it belongs to. Any signed-in user may upload; attaching is checked
 * by the record that takes the file.
 */
@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService service;

    @PostMapping
    public ApiResponse<UploadTicketResponse> create(@Valid @RequestBody CreateUploadRequest request) {

        return ApiResponse.<UploadTicketResponse>builder()
                .success(true)
                .message("Upload URL created")
                .data(service.create(request))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<UploadViewResponse> view(@PathVariable UUID id) {

        return ApiResponse.<UploadViewResponse>builder()
                .success(true)
                .message("Upload link created")
                .data(service.view(id))
                .build();
    }
}
