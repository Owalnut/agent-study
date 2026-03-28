package com.walnut.agent.service;

import com.walnut.agent.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioStorageService {
    private final MinioProperties properties;
    private final ObjectProvider<MinioClient> minioClientProvider;

    public MinioStorageService(MinioProperties properties, ObjectProvider<MinioClient> minioClientProvider) {
        this.properties = properties;
        this.minioClientProvider = minioClientProvider;
    }

    /**
     * 上传 TTS 音频字节，返回预签名 GET URL（私有桶也可在浏览器播放）。
     * 若已配置匿名读桶策略，仍可使用返回链接；未开公读时预签名不依赖桶公开。
     */
    public String uploadTtsAudio(byte[] data, String contentType, Long executionId) throws Exception {
        if (!properties.isEnabled() || data == null || data.length == 0) {
            return null;
        }
        MinioClient client = minioClientProvider.getIfAvailable();
        if (client == null) {
            return null;
        }
        String bucket = properties.getBucketName();
        ensureBucket(client, bucket);
        String safeContentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        String suffix = guessSuffix(safeContentType);
        String prefix = executionId != null && executionId > 0 ? "tts/" + executionId + "/" : "tts/adhoc/";
        String objectName = prefix + UUID.randomUUID() + suffix;
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(in, data.length, -1)
                            .contentType(safeContentType)
                            .build()
            );
        }
        return presignedGetUrl(client, bucket, objectName);
    }

    private String presignedGetUrl(MinioClient client, String bucket, String objectName) throws Exception {
        int sec = Math.max(60, properties.getPresignedUrlExpirySeconds());
        return client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectName)
                        .expiry(sec, TimeUnit.SECONDS)
                        .build()
        );
    }

    private void ensureBucket(MinioClient client, String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private static String guessSuffix(String contentType) {
        String ct = contentType.toLowerCase();
        if (ct.contains("wav")) return ".wav";
        if (ct.contains("mpeg") || ct.contains("mp3")) return ".mp3";
        if (ct.contains("ogg")) return ".ogg";
        return ".bin";
    }
}
