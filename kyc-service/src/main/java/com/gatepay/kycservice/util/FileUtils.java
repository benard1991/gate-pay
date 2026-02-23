package com.gatepay.kycservice.util;

import com.gatepay.kycservice.model.KycDocument;
import com.gatepay.kycservice.model.KycRequest;
import com.gatepay.kycservice.service.CloudinaryService;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileUtils {

    private static final ExecutorService executor = Executors.newFixedThreadPool(5);

    public static CompletableFuture<KycDocument> uploadDocumentAsync(MultipartFile file, KycRequest kycRequest, CloudinaryService cloudinaryService) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cleanFileName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9.\\-]", "_");
                String publicId = String.format("kyc/user_%d/%d_%s",
                        kycRequest.getUserId(),
                        System.currentTimeMillis(),
                        cleanFileName
                );

                String url = cloudinaryService.uploadFile(file, publicId);

                return KycDocument.builder()
                        .documentName(file.getOriginalFilename())
                        .documentType(file.getContentType())
                        .documentLink(url)
                        .kycRequest(kycRequest)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

            } catch (Exception e) {
                throw new RuntimeException("Failed to upload file: " + file.getOriginalFilename(), e);
            }
        }, executor);
    }

    public static List<KycDocument> toKycDocuments(List<MultipartFile> files, KycRequest kycRequest, CloudinaryService cloudinaryService) {
        if (files == null || files.isEmpty()) return List.of();

        List<CompletableFuture<KycDocument>> futures = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> uploadDocumentAsync(file, kycRequest, cloudinaryService))
                .toList();

        // Wait for all uploads to complete
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }
}
