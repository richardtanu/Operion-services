package com.example.operion.module.upload.dto;

import com.example.operion.module.upload.enums.UploadPurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateUploadRequest {

    @NotNull(message = "Purpose is required")
    private UploadPurpose purpose;

    /** image/jpeg, image/png, image/webp or application/pdf. */
    @NotBlank(message = "Content type is required")
    private String contentType;

    /** Exact size of the file that will be PUT; signed into the URL. */
    @NotNull(message = "Size is required")
    @Positive(message = "Size must be positive")
    private Long sizeBytes;
}
