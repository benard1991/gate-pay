package com.gatepay.kycservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.gatepay.kycservice.exception.InvalidFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock private Cloudinary     cloudinary;
    @Mock private Uploader       uploader;
    @Mock private MultipartFile  file;

    @InjectMocks
    private CloudinaryService cloudinaryService;

    private static final String SECURE_URL = "https://res.cloudinary.com/demo/image/upload/sample.jpg";
    private static final String PUBLIC_ID  = "gatepay/kyc/doc-123";

    private void stubUploaderReady() {
        when(cloudinary.uploader()).thenReturn(uploader);
    }

    private void stubFileBytes(byte[] bytes) throws IOException {
        when(file.getBytes()).thenReturn(bytes);
    }

    private void stubSuccessfulUpload() throws IOException {
        stubUploaderReady();
        stubFileBytes(new byte[]{1, 2, 3});
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", SECURE_URL));
    }

    //  uploadFile

    @Nested
    @DisplayName("uploadFile")
    class UploadFile {

        @Test
        @DisplayName("returns secure_url from Cloudinary on successful upload")
        void shouldReturnSecureUrlOnSuccess() throws IOException {
            when(file.getSize()).thenReturn(1024L);
            stubSuccessfulUpload();

            String result = cloudinaryService.uploadFile(file, null);

            assertThat(result).isEqualTo(SECURE_URL);
        }

        @Test
        @DisplayName("throws InvalidFileException when file exceeds 5 MB")
        void shouldThrowInvalidFileExceptionWhenFileTooLarge() {
            // size check happens first — uploader and getBytes are never reached
            when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);

            assertThatThrownBy(() -> cloudinaryService.uploadFile(file, null))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("File size exceeds the maximum allowed size of 5 MB");

            verifyNoInteractions(uploader);
        }

        @Test
        @DisplayName("accepts file exactly at 5 MB boundary without throwing")
        void shouldAcceptFileExactlyAtFiveMbBoundary() throws IOException {
            when(file.getSize()).thenReturn(5L * 1024 * 1024);
            stubSuccessfulUpload();

            assertThatNoException()
                    .isThrownBy(() -> cloudinaryService.uploadFile(file, null));
        }

        @Test
        @DisplayName("includes public_id and overwrite=true in params when publicId is non-blank")
        void shouldIncludePublicIdAndOverwriteWhenPublicIdIsProvided() throws IOException {
            when(file.getSize()).thenReturn(1024L);
            stubSuccessfulUpload();

            cloudinaryService.uploadFile(file, PUBLIC_ID);

            verify(uploader).upload(any(byte[].class), argThat(params -> {
                Map<?, ?> map = (Map<?, ?>) params;
                return PUBLIC_ID.equals(map.get("public_id"))
                        && Boolean.TRUE.equals(map.get("overwrite"));
            }));
        }

        @Test
        @DisplayName("does not include public_id in params when publicId is null")
        void shouldNotIncludePublicIdWhenPublicIdIsNull() throws IOException {
            when(file.getSize()).thenReturn(1024L);
            stubSuccessfulUpload();

            cloudinaryService.uploadFile(file, null);

            verify(uploader).upload(any(byte[].class), argThat(params -> {
                Map<?, ?> map = (Map<?, ?>) params;
                return !map.containsKey("public_id") && !map.containsKey("overwrite");
            }));
        }

        @Test
        @DisplayName("does not include public_id in params when publicId is blank")
        void shouldNotIncludePublicIdWhenPublicIdIsBlank() throws IOException {
            when(file.getSize()).thenReturn(1024L);
            stubSuccessfulUpload();

            cloudinaryService.uploadFile(file, "   ");

            verify(uploader).upload(any(byte[].class), argThat(params -> {
                Map<?, ?> map = (Map<?, ?>) params;
                return !map.containsKey("public_id") && !map.containsKey("overwrite");
            }));
        }

        @Test
        @DisplayName("always sets resource_type=auto and folder=gatepay/kyc in params")
        void shouldAlwaysSetResourceTypeAndFolder() throws IOException {
            when(file.getSize()).thenReturn(1024L);
            stubSuccessfulUpload();

            cloudinaryService.uploadFile(file, null);

            verify(uploader).upload(any(byte[].class), argThat(params -> {
                Map<?, ?> map = (Map<?, ?>) params;
                return "auto".equals(map.get("resource_type"))
                        && "gatepay/kyc".equals(map.get("folder"));
            }));
        }

        @Test
        @DisplayName("always includes a transformation param")
        void shouldAlwaysIncludeTransformationParam() throws IOException {
            when(file.getSize()).thenReturn(1024L);
            stubSuccessfulUpload();

            cloudinaryService.uploadFile(file, null);

            verify(uploader).upload(any(byte[].class), argThat(params ->
                    ((Map<?, ?>) params).containsKey("transformation")
            ));
        }

        @Test
        @DisplayName("throws InvalidFileException wrapping IOException when Cloudinary upload fails")
        void shouldThrowInvalidFileExceptionWhenCloudinaryUploadFails() throws IOException {
            when(file.getSize()).thenReturn(1024L);
            when(file.getOriginalFilename()).thenReturn("passport.jpg");
            stubUploaderReady();
            stubFileBytes(new byte[]{1, 2, 3});
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenThrow(new IOException("network error"));

            assertThatThrownBy(() -> cloudinaryService.uploadFile(file, null))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("Failed to upload file to Cloudinary")
                    .hasMessageContaining("passport.jpg");
        }

        @Test
        @DisplayName("throws InvalidFileException wrapping IOException when getBytes fails")
        void shouldThrowInvalidFileExceptionWhenGetBytesFails() throws IOException {
            when(file.getSize()).thenReturn(1024L);
            when(file.getOriginalFilename()).thenReturn("id-card.png");
            stubUploaderReady();
            when(file.getBytes()).thenThrow(new IOException("read error"));

            assertThatThrownBy(() -> cloudinaryService.uploadFile(file, null))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageContaining("Failed to upload file to Cloudinary");
        }

        @Test
        @DisplayName("uploads the raw bytes returned by file.getBytes()")
        void shouldUploadRawBytesFromFile() throws IOException {
            byte[] expectedBytes = {10, 20, 30};
            when(file.getSize()).thenReturn(1024L);
            stubUploaderReady();
            stubFileBytes(expectedBytes);
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(Map.of("secure_url", SECURE_URL));

            cloudinaryService.uploadFile(file, null);

            verify(uploader).upload(eq(expectedBytes), anyMap());
        }
    }


    //  deleteFile

    @Nested
    @DisplayName("deleteFile")
    class DeleteFile {

        @BeforeEach
        void stubUploader() {
            when(cloudinary.uploader()).thenReturn(uploader);
        }

        @Test
        @DisplayName("calls Cloudinary destroy with the given publicId")
        void shouldCallDestroyWithPublicId() throws IOException {
            cloudinaryService.deleteFile(PUBLIC_ID);

            verify(uploader).destroy(eq(PUBLIC_ID), anyMap());
        }

        @Test
        @DisplayName("completes silently when Cloudinary destroy succeeds")
        void shouldCompleteSilentlyOnSuccess() {
            assertThatNoException()
                    .isThrownBy(() -> cloudinaryService.deleteFile(PUBLIC_ID));
        }

        @Test
        @DisplayName("throws RuntimeException wrapping IOException when Cloudinary destroy fails")
        void shouldThrowRuntimeExceptionWhenDestroyFails() throws IOException {
            when(uploader.destroy(eq(PUBLIC_ID), anyMap()))
                    .thenThrow(new IOException("connection refused"));

            assertThatThrownBy(() -> cloudinaryService.deleteFile(PUBLIC_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to delete file from Cloudinary")
                    .hasMessageContaining(PUBLIC_ID);
        }

        @Test
        @DisplayName("passes an empty options map to Cloudinary destroy")
        void shouldPassEmptyOptionsMapToDestroy() throws IOException {
            cloudinaryService.deleteFile(PUBLIC_ID);

            verify(uploader).destroy(eq(PUBLIC_ID), argThat(map ->
                    map instanceof Map && ((Map<?, ?>) map).isEmpty()
            ));
        }

        @Test
        @DisplayName("calls destroy exactly once per deleteFile invocation")
        void shouldCallDestroyExactlyOnce() throws IOException {
            cloudinaryService.deleteFile(PUBLIC_ID);

            verify(uploader, times(1)).destroy(any(), any());
        }
    }
}