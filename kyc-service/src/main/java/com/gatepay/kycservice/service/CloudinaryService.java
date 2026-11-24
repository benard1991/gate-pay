package com.gatepay.kycservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.gatepay.kycservice.exception.InvalidFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
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

            // Documents are stored exactly as uploaded. We used to crop them to 300x300 here,
            // which cut off parts of ID cards and made every upload wait while Cloudinary
            // processed the image. If a smaller version is needed, ask for it in the URL.
            long start = System.currentTimeMillis();
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), params);
            log.info("Uploaded {} ({} KB) to Cloudinary in {} ms",
                    file.getOriginalFilename(), file.getSize() / 1024, System.currentTimeMillis() - start);

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

    // Best effort clean-up for files whose KYC request never got saved.
    // A failure here is only logged; it shouldn't hide the error that caused it.
    public void deleteByUrl(String url) {
        try {
            String[] typeAndId = resourceTypeAndPublicId(url);
            cloudinary.uploader().destroy(typeAndId[1],
                    ObjectUtils.asMap("resource_type", typeAndId[0], "invalidate", true));
            log.info("Removed orphaned Cloudinary file {}", typeAndId[1]);
        } catch (Exception e) {
            log.warn("Could not remove Cloudinary file {}: {}", url, e.getMessage());
        }
    }

    // https://res.cloudinary.com/<cloud>/<type>/upload/v123/<public_id>.<ext> -> [type, public_id]
    static String[] resourceTypeAndPublicId(String url) {
        int upload = url.indexOf("/upload/");
        if (upload < 0) {
            throw new IllegalArgumentException("Not a Cloudinary upload URL: " + url);
        }
        String resourceType = url.substring(url.lastIndexOf('/', upload - 1) + 1, upload);

        String path = url.substring(upload + "/upload/".length());
        if (path.matches("v\\d+/.*")) {
            path = path.substring(path.indexOf('/') + 1);
        }
        // images and videos get their format appended; raw files keep their own extension
        if (!"raw".equals(resourceType) && path.lastIndexOf('.') > 0) {
            path = path.substring(0, path.lastIndexOf('.'));
        }
        return new String[]{resourceType, path};
    }
}
