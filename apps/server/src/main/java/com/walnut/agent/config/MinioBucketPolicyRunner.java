package com.walnut.agent.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时为桶设置匿名可读（仅 GetObject），使形如 /bucket/key 的直链可访问。
 * 生产环境请谨慎开启，或仅对测试桶使用。
 */
@Component
@Order(100)
@ConditionalOnBean(MinioClient.class)
@ConditionalOnProperty(name = "minio.apply-public-read-policy-on-startup", havingValue = "true", matchIfMissing = false)
public class MinioBucketPolicyRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MinioBucketPolicyRunner.class);

    private final MinioProperties properties;
    private final MinioClient minioClient;

    public MinioBucketPolicyRunner(MinioProperties properties, MinioClient minioClient) {
        this.properties = properties;
        this.minioClient = minioClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        String bucket = properties.getBucketName();
        if (bucket == null || bucket.isBlank()) {
            return;
        }
        try {
            ensureBucket(bucket);
            String policy = publicReadPolicyJson(bucket);
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(policy)
                            .build()
            );
            log.info("MinIO bucket '{}' policy set: anonymous read (GetObject) on objects", bucket);
        } catch (Exception e) {
            log.warn("MinIO bucket public-read policy not applied (bucket may stay private): {}", e.getMessage());
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    /**
     * S3 兼容策略：允许匿名下载桶内所有对象，不包含 ListBucket。
     */
    static String publicReadPolicyJson(String bucketName) {
        String b = bucketName.replace("\"", "\\\"");
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(b);
    }
}
