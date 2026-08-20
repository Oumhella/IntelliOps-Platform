package org.example.delivery_service.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryProofStorage {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:delivery-proofs}")
    private String bucketName;

    public String store(Long enterpriseId, Long deliveryId, MultipartFile photo) {
        try {
            byte[] bytes = photo.getBytes();
            String contentType = detectedContentType(bytes);
            String extension = "image/png".equals(contentType) ? ".png" : ".jpg";
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Proof photo must be between 1 byte and 5 MB");
            }
            String objectKey = "enterprises/%d/deliveries/%d/%s%s".formatted(
                    enterpriseId, deliveryId, UUID.randomUUID(), extension);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Proof photo could not be stored", exception);
        }
    }

    public byte[] load(String objectKey) {
        try {
            try (var stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build())) {
                return stream.readAllBytes();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Proof photo could not be loaded", exception);
        }
    }

    private String detectedContentType(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        throw new IllegalArgumentException("Proof photo must be a JPEG or PNG image");
    }
}
