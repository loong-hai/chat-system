package com.hailong.chatsystem.service.storage.impl;

import com.hailong.chatsystem.config.storage.S3StorageProperties;
import com.hailong.chatsystem.model.dto.FileUploadResult;
import com.hailong.chatsystem.model.dto.PresignedUploadRequest;
import com.hailong.chatsystem.model.entity.AvatarHistory;
import com.hailong.chatsystem.model.entity.FileIndex;
import com.hailong.chatsystem.model.entity.FileUploadTransaction;
import com.hailong.chatsystem.repository.AvatarHistoryRepository;
import com.hailong.chatsystem.repository.FileIndexRepository;
import com.hailong.chatsystem.repository.FileUploadTransactionRepository;
import com.hailong.chatsystem.service.storage.StorageService;
import com.hailong.chatsystem.utils.FileHashUtils;
import com.hailong.chatsystem.utils.ImageCompressUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * S3存储服务实现类
 * 基于AWS SDK v2，兼容RustFS/MinIO/阿里云OSS等S3协议存储
 *
 * 安全设计：
 * 1. 路径注入防护：所有用户输入的路径成分做sanitize，禁止../等
 * 2. 文件类型白名单：配置化限制可上传类型
 * 3. 用户隔离：强制存储路径包含userId，防止跨用户访问
 * 4. 大小限制： Service层二次校验，防止恶意超大文件
 */
@Slf4j
@Service
public class S3StorageServiceImpl implements StorageService {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private S3StorageProperties properties;

    @Autowired
    private FileIndexRepository fileIndexRepository;

    @Autowired
    private AvatarHistoryRepository avatarHistoryRepository;

    @Autowired
    private FileUploadTransactionRepository transactionRepository;

    /** 系统资源路径前缀，禁止用户操作 */
    private static final String SYSTEM_PREFIX = "system/";

    /** 用户资源路径前缀 */
    private static final String USER_PREFIX = "users/";

    /** 头像文件大小限制：5MB */
    private static final long AVATAR_MAX_SIZE = 5 * 1024 * 1024;

    /** 聊天文件大小限制（服务器中转模式）：100MB */
    private static final long CHAT_FILE_MAX_SIZE = 100 * 1024 * 1024;

    /** 允许的图片类型 */
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /** 允许的文件扩展名（安全校验） */
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "pdf", "doc", "docx","txt","zip","rar"
    );


    @Override
    public FileUploadResult uploadAvatar(Long userId, MultipartFile file) {
        validateFile(file, AVATAR_MAX_SIZE, true);
        String originalFilename = file.getOriginalFilename();
        String ext = FileHashUtils.getFileExtension(originalFilename).toLowerCase();

        byte[] fileData;
        try {
            fileData = ImageCompressUtils.compressAvatar(file,
                    properties.getAvatarCompress().getMaxWidth(),
                    properties.getAvatarCompress().getMaxHeight(),
                    properties.getAvatarCompress().getQuality());
        } catch (Exception e) {
            throw new RuntimeException("图片压缩失败", e);
        }

        String fileHash = FileHashUtils.calculateSha256(fileData);
        String s3Key = FileHashUtils.generateAvatarKey(userId, fileHash, ext);

        // 【步骤1】秒传检查（无事务）
        int updated = fileIndexRepository.incrementRefCount(fileHash, LocalDateTime.now());
        if (updated > 0) {
            Optional<FileIndex> existingFile = fileIndexRepository.findByFileHash(fileHash);
            if (existingFile.isPresent()) {
                FileIndex index = existingFile.get();
                // 独立事务保存历史
                saveAvatarHistoryInNewTx(userId, index.getS3Key(), null, originalFilename,
                        (long) fileData.length, fileHash);
                return FileUploadResult.builder()
                        .isRapidUpload(true)
                        .fileUrl(convertToCdnUrl(getPublicUrl(index.getS3Key())))
                        .fileKey(index.getS3Key())
                        .fileHash(fileHash)
                        .message("秒传成功")
                        .build();
            }
        }

        // 【步骤2】创建事务记录（独立事务，快速提交）
        Long txId = createUploadTransaction(userId, s3Key, fileHash, (long) fileData.length, "AVATAR");

        boolean s3UploadSuccess = false;
        try {
            // 【步骤3】S3上传（无事务，不占用连接池）
            uploadToS3(s3Key, fileData, file.getContentType(), true);
            s3UploadSuccess = true;

            // 【步骤4】数据库记录（独立事务）
            completeUploadTransaction(txId, s3Key, fileHash, originalFilename,
                    (long) fileData.length, file.getContentType(), userId);

            return FileUploadResult.builder()
                    .fileUrl(convertToCdnUrl(getPublicUrl(s3Key)))
                    .fileKey(s3Key)
                    .fileHash(fileHash)
                    .isRapidUpload(false)
                    .message("上传成功")
                    .build();

        } catch (Exception e) {
            // 标记失败
            markTransactionFailed(txId, s3UploadSuccess ? "DB失败" : "S3失败: " + e.getMessage());
            throw new RuntimeException("头像上传失败: " + e.getMessage(), e);
        }
    }
    // 独立事务方法（通过自调用或新建Service实现）
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private Long createUploadTransaction(Long userId, String s3Key, String fileHash,
                                         Long fileSize, String sourceType) {
        return transactionTemplate.execute(status -> {
            FileUploadTransaction tx = new FileUploadTransaction();
            tx.setFileKey(s3Key);
            tx.setFileHash(fileHash);
            tx.setUserId(userId);
            tx.setFileSize(fileSize);
            tx.setSourceType(sourceType);
            tx.setStatus(FileUploadTransaction.Status.PENDING);
            tx.setRetryCount(0);
            FileUploadTransaction saved = transactionRepository.save(tx);
            return saved.getId();
        });
    }

    private void completeUploadTransaction(Long txId, String s3Key, String fileHash,
                                           String originalName, Long size, String mimeType, Long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            // 保存文件索引
            saveFileIndex(fileHash, s3Key, originalName, size, mimeType, "AVATAR", true);
            // 保存头像历史
            saveAvatarHistory(userId, s3Key, null, originalName, size, fileHash);
            // 更新事务状态
            FileUploadTransaction tx = transactionRepository.findById(txId)
                    .orElseThrow(() -> new RuntimeException("事务不存在"));
            tx.setStatus(FileUploadTransaction.Status.COMPLETED);
            tx.setConfirmedAt(LocalDateTime.now());
            transactionRepository.save(tx);
        });
    }

    private void markTransactionFailed(Long txId, String errorMsg) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                FileUploadTransaction tx = transactionRepository.findById(txId).orElse(null);
                if (tx != null) {
                    tx.setStatus(FileUploadTransaction.Status.FAILED);
                    tx.setErrorMsg(errorMsg);
                    transactionRepository.save(tx);
                }
            });
        } catch (Exception ex) {
            log.error("标记事务失败状态失败", ex);
        }
    }

    private void saveAvatarHistoryInNewTx(Long userId, String s3Key, String thumbnailKey,
                                          String originalName, Long size, String hash) {
        transactionTemplate.executeWithoutResult(status -> {
            saveAvatarHistory(userId, s3Key, thumbnailKey, originalName, size, hash);
        });
    }

    // 启动补偿机制（解决P0-14）
    @PostConstruct
    public void startupRecovery() {
        log.info("启动上传事务补偿扫描...");

        // 查询超过5分钟仍为PENDING的事务
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        List<FileUploadTransaction> pendingTxs = transactionRepository
                .findByStatusAndCreatedAtBefore(FileUploadTransaction.Status.PENDING, fiveMinutesAgo);

        for (FileUploadTransaction tx : pendingTxs) {
            try {
                // 检查S3文件是否存在
                boolean exists = checkS3ObjectExists(tx.getFileKey());

                if (exists) {
                    // 文件已上传但DB未更新，尝试补偿
                    log.info("补偿事务: txId={}, fileKey={}", tx.getId(), tx.getFileKey());
                    completeUploadTransaction(tx.getId(), tx.getFileKey(), tx.getFileHash(),
                            tx.getFileKey().substring(tx.getFileKey().lastIndexOf('/') + 1),
                            tx.getFileSize(), "image/jpeg", tx.getUserId());
                } else {
                    // 文件不存在，标记为失败
                    tx.setStatus(FileUploadTransaction.Status.FAILED);
                    tx.setErrorMsg("启动扫描：文件未上传");
                    transactionRepository.save(tx);
                }
            } catch (Exception e) {
                log.error("补偿事务失败: txId={}", tx.getId(), e);
                tx.setRetryCount(tx.getRetryCount() + 1);
                if (tx.getRetryCount() > 5) {
                    tx.setStatus(FileUploadTransaction.Status.ORPHAN);
                }
                transactionRepository.save(tx);
            }
        }
    }

    private boolean checkS3ObjectExists(String fileKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(properties.getDefaultBucket())
                    .key(fileKey)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("检查S3对象存在性失败", e);
            return false;
        }
    }

    @Override
    @Transactional
    public FileUploadResult uploadChatFile(Long userId, MultipartFile file, boolean chatRelated) {
        if (file.getSize() > CHAT_FILE_MAX_SIZE) {
            throw new RuntimeException("文件过大，请使用大文件上传接口");
        }
        validateFile(file, CHAT_FILE_MAX_SIZE, false);

        String originalFilename = file.getOriginalFilename();
        String ext = FileHashUtils.getFileExtension(originalFilename).toLowerCase();

        try {
            // 计算Hash使用流式Digest（避免全量读取）
            String fileHash = calculateSha256FromStream(file);

            // 【秒传检查】
            int updated = fileIndexRepository.incrementRefCount(fileHash, LocalDateTime.now());
            if (updated > 0) {
                Optional<FileIndex> existingFile = fileIndexRepository.findByFileHash(fileHash);
                if (existingFile.isPresent()) {
                    FileIndex index = existingFile.get();
                    return FileUploadResult.builder()
                            .isRapidUpload(true)
                            .fileUrl(convertToCdnUrl(getPublicUrl(index.getS3Key())))
                            .fileKey(index.getS3Key())
                            .fileHash(fileHash)
                            .fileSize(file.getSize())
                            .message("秒传成功")
                            .build();
                }
            }

            // 流式上传（关键修复）
            String s3Key = FileHashUtils.generateStorageKey(userId, fileHash, originalFilename);

            // 使用InputStream直接上传，不占用大量内存
            uploadToS3Stream(s3Key, file.getInputStream(), file.getSize(), file.getContentType(), true);

            saveFileIndex(fileHash, s3Key, originalFilename, file.getSize(),
                    file.getContentType(), "CHAT", true);

            return FileUploadResult.builder()
                    .isRapidUpload(false)
                    .fileUrl(convertToCdnUrl(getPublicUrl(s3Key)))
                    .fileKey(s3Key)
                    .fileHash(fileHash)
                    .fileSize(file.getSize())
                    .message("上传成功")
                    .build();

        } catch (Exception e) {
            log.error("文件上传失败: userId={}", userId, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    // 辅助方法：流式上传到S3
    private void uploadToS3Stream(String key, InputStream inputStream, long contentLength,
                                  String contentType, boolean isPublic) {
        validateKeySafety(key);

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(properties.getDefaultBucket())
                .key(key)
                .contentType(contentType)
                .build();

        // 使用RequestBody.fromInputStream实现流式上传
        s3Client.putObject(putReq,
                RequestBody.fromInputStream(inputStream, contentLength));
    }

    // 辅助方法：流式计算Hash
    private String calculateSha256FromStream(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    // ==================== 模式B：预签名直传 ====================

    @Override
    @Transactional
    public FileUploadResult getPresignedUploadUrl(Long userId, PresignedUploadRequest request) {
        // ... 秒传检查逻辑不变 ...

        String uploadId = UUID.randomUUID().toString();
        String s3Key = generateS3Key(userId, request, uploadId);

        // 【关键】创建事务记录（PENDING状态）
        FileUploadTransaction transaction = new FileUploadTransaction();
        transaction.setFileKey(s3Key);
        transaction.setFileHash(request.getFileHash());
        transaction.setUserId(userId);
        transaction.setFileSize(request.getFileSize());
        transaction.setContentType(request.getContentType());
        transaction.setSourceType(request.getFileType().toUpperCase());
        transaction.setStatus(FileUploadTransaction.Status.PENDING);
        transactionRepository.save(transaction);

        // 生成预签名URL
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getDefaultBucket())
                    .key(s3Key)
                    .contentType(request.getContentType())
                    .metadata(Collections.singletonMap("uploader-id", userId.toString()))
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(properties.getPresignedUrlExpiryMinutes()))
                    .putObjectRequest(putRequest)
                    .build();

            String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

            return FileUploadResult.builder()
                    .isRapidUpload(false)
                    .presignedUrl(presignedUrl)
                    .fileKey(s3Key)
                    .uploadId(uploadId)
                    .expiresIn(properties.getPresignedUrlExpiryMinutes() * 60)
                    .build();

        } catch (Exception e) {
            transaction.setStatus(FileUploadTransaction.Status.FAILED);
            transaction.setErrorMsg(e.getMessage());
            transactionRepository.save(transaction);
            throw new RuntimeException("生成上传链接失败", e);
        }
    }

    /**
     * 生成 S3 存储 Key（提取原代码逻辑）
     */
    private String generateS3Key(Long userId, PresignedUploadRequest request, String uploadId) {
        String fileName = sanitizeFileName(request.getFileName());
        String ext = FileHashUtils.getFileExtension(fileName).toLowerCase();

        if ("avatar".equals(request.getFileType())) {
            // 头像路径
            return String.format("users/%d/avatar/%s_%s.%s",
                    userId, System.currentTimeMillis(), uploadId.substring(0, 8), ext);
        } else {
            // 聊天文件路径
            String hashPrefix = uploadId.replace("-", "").substring(0, 8);
            return String.format("users/%d/chat/%s/%s.%s", userId, hashPrefix, uploadId, ext);
        }
    }

    @Override
    @Transactional
    public FileUploadResult confirmUpload(Long userId, String fileKey, String fileHash) {
        // 1. 查找事务记录
        FileUploadTransaction transaction = transactionRepository.findByFileKey(fileKey)
                .orElseThrow(() -> new RuntimeException("未找到上传任务，请重新申请上传"));

        if (!transaction.getUserId().equals(userId)) {
            throw new RuntimeException("无权确认此文件");
        }

        if (!FileUploadTransaction.Status.PENDING.equals(transaction.getStatus())) {
            throw new RuntimeException("该文件已处理或已过期");
        }

        try {
            // 2. 检查S3对象是否存在
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(properties.getDefaultBucket())
                    .key(fileKey)
                    .build();

            s3Client.headObject(headRequest);
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);

            // 3. 保存FileIndex（重复Hash检查）
            Optional<FileIndex> existing = fileIndexRepository.findByFileHash(fileHash);
            if (existing.isPresent()) {
                // 重复上传，删除S3重复文件（秒传逻辑）
                try {
                    deleteFromS3(fileKey);
                } catch (Exception ignored) {}

                transaction.setStatus(FileUploadTransaction.Status.COMPLETED);
                transaction.setConfirmedAt(LocalDateTime.now());
                transactionRepository.save(transaction);

                return FileUploadResult.builder()
                        .fileUrl(convertToCdnUrl(getPublicUrl(existing.get().getS3Key())))
                        .fileKey(existing.get().getS3Key())
                        .isRapidUpload(true)
                        .build();
            }

            // 4. 保存索引
            saveFileIndex(fileHash, fileKey, null, headResponse.contentLength(),
                    headResponse.contentType(), transaction.getSourceType(), true);

            // 5. 更新事务完成
            transaction.setStatus(FileUploadTransaction.Status.COMPLETED);
            transaction.setConfirmedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            return FileUploadResult.builder()
                    .fileUrl(convertToCdnUrl(getPublicUrl(fileKey)))
                    .fileKey(fileKey)
                    .build();

        } catch (NoSuchKeyException e) {
            transaction.setStatus(FileUploadTransaction.Status.FAILED);
            transaction.setErrorMsg("文件未上传到S3");
            transactionRepository.save(transaction);
            throw new RuntimeException("文件尚未上传到S3或已过期，请重新上传");
        }
    }

    // ==================== 文件管理接口 ====================

    @Override
    public String getFileUrl(String fileKey, boolean forDownload, int expireMinutes) {
        // 查询FileIndex判断是否公开文件
        Optional<FileIndex> indexOpt = fileIndexRepository.findByS3Key(fileKey);

        if (indexOpt.isPresent() && Boolean.TRUE.equals(indexOpt.get().getIsPublic()) && !forDownload) {
            // 公开文件直接返回URL（走CDN或S3直链）
            return convertToCdnUrl(getPublicUrl(fileKey));
        } else {
            // 私有文件生成预签名URL
            try {
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest =
                        software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(expireMinutes))
                                .getObjectRequest(req -> req.bucket(properties.getDefaultBucket()).key(fileKey))
                                .build();

                return s3Presigner.presignGetObject(presignRequest).url().toString();
            } catch (Exception e) {
                log.error("生成预签名访问URL失败", e);
                throw new RuntimeException("获取文件访问链接失败");
            }
        }
    }

    // ==================== 修改 upload 相关方法（可选，防止恶意上传覆盖） ====================
    private void validateKeySafety(String s3Key) {
        if (s3Key.startsWith(SYSTEM_PREFIX)) {
            log.error("非法路径：尝试写入系统目录: {}", s3Key);
            throw new RuntimeException("无权访问系统路径");
        }
    }


    @Override
    @Transactional
    public boolean deleteFile(Long userId, String fileKey) {
        // 1. 【核心防护】禁止操作系统目录
        if (fileKey.startsWith(SYSTEM_PREFIX)) {
            log.error("非法操作：尝试删除系统资源: userId={}, key={}", userId, fileKey);
            throw new RuntimeException("无权操作系统资源");
        }

        // 2. 原有校验：只能删除自己的文件
        if (!fileKey.startsWith(USER_PREFIX + userId + "/")) {
            log.warn("非法删除尝试: userId={}, key={}", userId, fileKey);
            throw new RuntimeException("无权删除此文件");
        }

        try {
            // 减少FileIndex引用计数（原子操作）
            Optional<FileIndex> fileIndex = fileIndexRepository.findByS3Key(fileKey);
            if (fileIndex.isPresent()) {
                FileIndex index = fileIndex.get();
                String fileHash = index.getFileHash();

                // 原子减计数
                int updated = fileIndexRepository.decrementRefCount(fileHash, LocalDateTime.now());

                // 重新查询最新值（因为原子操作不返回新值）
                Optional<FileIndex> refreshed = fileIndexRepository.findByFileHash(fileHash);
                if (refreshed.isPresent() && refreshed.get().getRefCount() <= 0) {
                    // 引用为0，物理删除
                    deleteFromS3(fileKey);
                    // 删除缩略图...
                    fileIndexRepository.delete(refreshed.get());
                }
            } else {
                // 无索引记录，直接删S3
                deleteFromS3(fileKey);
            }

            // 软删除头像历史记录...
            return true;

        } catch (Exception e) {
            log.error("删除文件失败", e);
            return false;
        }
    }

    @Override
    public List<FileUploadResult> getAvatarHistory(Long userId) {
        List<AvatarHistory> historyList = avatarHistoryRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, AvatarHistory.Status.ACTIVE);

        return historyList.stream().map(h -> FileUploadResult.builder()
                .fileKey(h.getS3Key())
                .fileUrl(convertToCdnUrl(getPublicUrl(h.getS3Key())))
                .thumbnailUrl(h.getThumbnailKey() != null ?
                        convertToCdnUrl(getPublicUrl(h.getThumbnailKey())) : null)
                .fileSize(h.getFileSize())
                .fileHash(h.getOriginalName()) // 复用字段显示原始名，或扩展DTO
                .isRapidUpload(false)
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FileUploadResult switchAvatar(Long userId, Long historyId) {
        AvatarHistory target = avatarHistoryRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("历史记录不存在"));

        if (!target.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作");
        }

        // 取消当前
        avatarHistoryRepository.clearCurrentAvatar(userId);

        // 设置新的当前
        target.setIsCurrent(true);
        target.setUpdatedAt(LocalDateTime.now());
        avatarHistoryRepository.save(target);

        return FileUploadResult.builder()
                .fileUrl(convertToCdnUrl(getPublicUrl(target.getS3Key())))
                .thumbnailUrl(target.getThumbnailKey() != null ?
                        convertToCdnUrl(getPublicUrl(target.getThumbnailKey())) : null)
                .fileKey(target.getS3Key())
                .message("头像切换成功")
                .build();
    }

    // ==================== 私有辅助方法 ====================

    private void uploadToS3(String key, byte[] data, String contentType, boolean isPublic) {
        validateKeySafety(key);
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(properties.getDefaultBucket())
                .key(key)
                .contentType(contentType)
                // ACL设置：公开读或私有（rustFS可能不支持ACL，视情况注释掉）
                // .acl(isPublic ? ObjectCannedACL.PUBLIC_READ : ObjectCannedACL.PRIVATE)
                .build();

        s3Client.putObject(putReq, RequestBody.fromBytes(data));
    }

    private void deleteFromS3(String key) {
        DeleteObjectRequest delReq = DeleteObjectRequest.builder()
                .bucket(properties.getDefaultBucket())
                .key(key)
                .build();
        s3Client.deleteObject(delReq);
    }

    private String getPublicUrl(String key) {
        // 构建S3直链
        return String.format("%s/%s/%s",
                properties.getEndpoint(),
                properties.getDefaultBucket(),
                key);
    }

private String convertToCdnUrl(String s3Url) {
    if (properties.getCdnDomain() != null && !properties.getCdnDomain().isEmpty()) {
        // 保留 bucket 名：cdnDomain + /bucket/key
        String key = s3Url.substring(s3Url.indexOf(properties.getDefaultBucket())
                + properties.getDefaultBucket().length() + 1);
        return properties.getCdnDomain() + "/" + properties.getDefaultBucket() + "/" + key;
    }
    return s3Url;
}

    private void saveFileIndex(String hash, String s3Key, String originalName,
                               Long size, String mimeType, String source, boolean isPublic) {
        FileIndex index = new FileIndex();
        index.setFileHash(hash);
        index.setS3Key(s3Key);
        index.setOriginalName(originalName);
        index.setFileSize(size);
        index.setMimeType(mimeType);
        index.setSourceType(source);
        index.setIsPublic(isPublic);
        index.setRefCount(1);
        fileIndexRepository.save(index);
    }

    private void saveAvatarHistory(Long userId, String s3Key, String thumbnailKey,
                                   String originalName, Long size, String hash) {
        // 取消之前的当前头像
        avatarHistoryRepository.clearCurrentAvatar(userId);

        AvatarHistory history = new AvatarHistory();
        history.setUserId(userId);
        history.setS3Key(s3Key);
        history.setThumbnailKey(thumbnailKey);
        history.setOriginalName(originalName);
        history.setFileSize(size);
        history.setIsCurrent(true);
        history.setStatus(AvatarHistory.Status.ACTIVE);
        avatarHistoryRepository.save(history);
    }

    private void validateFile(MultipartFile file, long maxSize, boolean isImage) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }

        if (file.getSize() > maxSize) {
            throw new RuntimeException("文件大小超过限制: " + (maxSize / 1024 / 1024) + "MB");
        }

        // 扩展名检查
        String ext = FileHashUtils.getFileExtension(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new RuntimeException("不支持的文件类型: " + ext);
        }

        // 图片类型额外检查（如果是头像）
        if (isImage && !ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new RuntimeException("头像必须是图片文件");
        }
    }


    // ==================== 定时任务：清理孤儿文件 ====================

    /**
     * 每天凌晨2点清理孤儿文件
     * 超过24小时仍为PENDING或标记为ORPHAN的记录
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOrphanFiles() {
        log.info("开始清理孤儿文件...");

        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);

        // 1. 查找超时的PENDING记录（前端未确认）
        List<FileUploadTransaction> pendingList =
                transactionRepository.findByStatusAndCreatedAtBefore(
                        FileUploadTransaction.Status.PENDING, yesterday);

        // 2. 查找标记为ORPHAN的记录
        List<FileUploadTransaction> orphanList =
                transactionRepository.findByStatusAndCreatedAtBefore(
                        FileUploadTransaction.Status.ORPHAN, yesterday);

        pendingList.addAll(orphanList);

        int deletedCount = 0;
        for (FileUploadTransaction tx : pendingList) {
            try {
                // 检查DB是否真的没有引用
                Optional<FileIndex> index = fileIndexRepository.findByS3Key(tx.getFileKey());
                if (index.isEmpty()) {
                    // DB无记录，删除S3文件
                    deleteFromS3(tx.getFileKey());
                    tx.setStatus(FileUploadTransaction.Status.ORPHAN);
                    tx.setErrorMsg("已清理孤儿文件");
                    transactionRepository.save(tx);
                    deletedCount++;
                } else {
                    // DB有记录，只是事务状态没更新，修复状态
                    tx.setStatus(FileUploadTransaction.Status.COMPLETED);
                    transactionRepository.save(tx);
                }
            } catch (Exception e) {
                log.error("清理孤儿文件失败: fileKey={}", tx.getFileKey(), e);
                tx.setRetryCount(tx.getRetryCount() + 1);
                if (tx.getRetryCount() > 3) {
                    tx.setStatus(FileUploadTransaction.Status.ORPHAN);
                }
                transactionRepository.save(tx);
            }
        }

        log.info("孤儿文件清理完成: 处理{}个, 删除{}个", pendingList.size(), deletedCount);
    }

    private String sanitizeFileName(String fileName) {
        // 防止路径注入，只保留文件名部分，去除路径
        if (fileName == null) return "unknown";
        return fileName.replaceAll(".*[/\\\\]", "");
    }
}