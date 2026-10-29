package com.example.operion.module.upload.controller;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.operion.module.upload.service.LocalDiskStorage;
import com.example.operion.module.upload.service.ObjectStorage;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * BE-30: where the signed links of {@code UPLOAD_STORAGE=local} point. No
 * login: the signature in the link is the permission, exactly like an R2
 * presigned URL (the phone PUTs with only the headers {@code POST /uploads}
 * returned). Exists only in local mode.
 */
@RestController
@RequestMapping("/uploads-local")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "uploads.storage", havingValue = "local", matchIfMissing = true)
public class LocalUploadController {

    // the interface, not LocalDiskStorage, so a test that mocks ObjectStorage still starts
    private final ObjectStorage storage;

    @PutMapping
    public ResponseEntity<Void> put(
            @RequestParam String key,
            @RequestParam("ct") String contentType,
            @RequestParam long size,
            @RequestParam long expires,
            @RequestParam String sig,
            HttpServletRequest request) throws IOException {

        local().store(key, contentType, size, expires, sig, request.getContentType(), request.getInputStream());

        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Resource> get(
            @RequestParam String key,
            @RequestParam long expires,
            @RequestParam String sig) {

        LocalDiskStorage.StoredFile file = local().read(key, expires, sig);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new FileSystemResource(file.path()));
    }

    private LocalDiskStorage local() {

        if (!(storage instanceof LocalDiskStorage localDisk)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Local uploads are not enabled");
        }

        return localDisk;
    }
}
