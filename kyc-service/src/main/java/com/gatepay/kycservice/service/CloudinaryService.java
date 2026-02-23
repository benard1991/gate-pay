package com.gatepay.kycservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.gatepay.kycservice.exception.InvalidFileException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {
    private final Cloudinary cloudinary;


    public String uploadFile(MultipartFile file, String publicId) {
        long maxFileSize = 5 * 1024 * 1024; // 5 MB

        if (file.getSize() > maxFileSize) {
            throw new InvalidFileException("File size exceeds the maximum allowed size of 5 MB");
        }

        try {
            Map<String, Object> params = ObjectUtils.asMap(
                    "resource_type", "auto",
                    "folder", "gatepay/kyc"
            );

            // overwrite existing file
            if (publicId != null && !publicId.isBlank()) {
                params.put("public_id", publicId);
                params.put("overwrite", true);
            }

            // Apply Cloudinary transformation if it's an image
            params.put("transformation", new com.cloudinary.Transformation()
                    .width(300)
                    .height(300)
                    .crop("fill")
                    .gravity("auto")
                    .quality("auto")
            );

            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), params);

            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            throw new InvalidFileException("Failed to upload file to Cloudinary: " + file.getOriginalFilename(), e);
        }
    }





    public void deleteFile(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file from Cloudinary: " + publicId, e);
        }
    }
}
