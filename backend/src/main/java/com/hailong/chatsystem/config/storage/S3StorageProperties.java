package com.hailong.chatsystem.config.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3存储配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.s3")
public class S3StorageProperties {

    /** S3服务端点 (如 http://192.168.88.105:9000) */
    private String endpoint = "http://192.168.88.105:9000";

    /** Access Key */
    private String accessKey = "root";

    /** Secret Key */
    private String secretKey = "123456";

    /** 默认Bucket (用户资源主存储桶) */
    private String defaultBucket = "chat-db";

    private String defaultAvatarPath = "system/default/avatar.png"; // 默认值

    /** 地区 (rustFS兼容模式可随意填写或使用us-east-1) */
    private String region = "us-east-1";

    /** 是否启用PathStyle访问 (本地S3通常需要) */
    private Boolean pathStyleAccess = true;

    /** 预签名URL有效期(分钟) */
    private Integer presignedUrlExpiryMinutes = 10;

    /** 头像压缩配置 */
    private AvatarCompress avatarCompress = new AvatarCompress();

    /** CDN域名 (后期加速时填写，如 https://cdn.yourdomain.com) */
    private String cdnDomain = "";

    @Data
    public static class AvatarCompress {
        /** 是否启用头像压缩 */
        private Boolean enabled = true;
        /** 最大宽度 */
        private Integer maxWidth = 800;
        /** 最大高度 */
        private Integer maxHeight = 800;
        /** 压缩质量 (0.0-1.0) */
        private Double quality = 0.85;
        /** 缩略图尺寸 */
        private Integer thumbnailSize = 200;
    }
}