package org.example.delivery_service.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    MinioClient minioClient(
            @Value("${minio.url}") String url,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket-name}") String bucketName) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("MinIO credentials are required for delivery proof storage");
        }
        MinioClient client = MinioClient.builder().endpoint(url).credentials(accessKey, secretKey).build();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize delivery proof storage", exception);
        }
        return client;
    }
}
