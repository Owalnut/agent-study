package com.walnut.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    private boolean enabled = true;
    private String endpoint = "http://localhost:9000";
    private String accessKey = "";
    private String secretKey = "";
    private String bucketName = "walnut-agent";
    /** 浏览器/Podcast 播放用的公网基址，不含末尾 /，与桶内路径拼接成最终 URL（匿名可读桶时可用直链） */
    private String publicUrl = "http://localhost:9000";
    /** 为 true 时启动阶段对 bucket 设置匿名 GetObject（与预签名二选一即可；生产慎用） */
    private boolean applyPublicReadPolicyOnStartup = false;
    /** 预签名 GET 有效期（秒），默认 7 天 */
    private int presignedUrlExpirySeconds = 604800;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public boolean isApplyPublicReadPolicyOnStartup() {
        return applyPublicReadPolicyOnStartup;
    }

    public void setApplyPublicReadPolicyOnStartup(boolean applyPublicReadPolicyOnStartup) {
        this.applyPublicReadPolicyOnStartup = applyPublicReadPolicyOnStartup;
    }

    public int getPresignedUrlExpirySeconds() {
        return presignedUrlExpirySeconds;
    }

    public void setPresignedUrlExpirySeconds(int presignedUrlExpirySeconds) {
        this.presignedUrlExpirySeconds = presignedUrlExpirySeconds;
    }
}
