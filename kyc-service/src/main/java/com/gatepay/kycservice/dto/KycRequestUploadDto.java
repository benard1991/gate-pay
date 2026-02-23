package com.gatepay.kycservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycRequestUploadDto {

    private Long userId;

    @NotBlank(message = "NIN is required")
    @Pattern(regexp = "\\d{11}", message = "NIN must be 11 digits")
    private String nin;

    @NotBlank(message = "BVN is required")
    @Pattern(regexp = "\\d{11}", message = "BVN must be 11 digits")
    private String bvn;

    @Size(max = 500, message = "Comments must not exceed 500 characters")
    private String comments;

    @Size(min = 1, message = "At least one document must be uploaded")
    private List<MultipartFile> documents = new ArrayList<>();

    private String idempotencyKey;

}

