package com.hailong.chatsystem.utils;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class FileHashUtils {

    /**
     * 计算文件SHA-256 Hash (用于秒传去重)
     * @param file 上传的文件
     * @return 32位小写Hex字符串
     */
    public static String calculateSha256(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return DigestUtils.sha256Hex(is);
        } catch (IOException e) {
            log.error("计算文件Hash失败", e);
            throw new RuntimeException("文件Hash计算失败", e);
        }
    }

    /**
     * 计算字节数组SHA-256 (用于压缩后的图片)
     */
    public static String calculateSha256(byte[] data) {
        return DigestUtils.sha256Hex(data);
    }

    /**
     * 生成文件存储Key
     * @param userId 用户ID
     * @param hash 文件Hash
     * @param originalFilename 原始文件名(用于获取扩展名)
     * @return 存储Key路径
     */
    public static String generateStorageKey(Long userId, String hash, String originalFilename) {
        String ext = getFileExtension(originalFilename).toLowerCase();
        // 路径: users/{userId}/chat/2024/01/{hash前8位}/{完整hash}.ext
        // 或者: users/{userId}/avatar/{timestamp}_{hash}.ext (头像特殊处理在外层)
        return String.format("users/%d/chat/%s/%s.%s",
                userId,
                hash.substring(0, 8),
                hash,
                ext);
    }

    /**
     * 生成头像存储Key
     */
    public static String generateAvatarKey(Long userId, String hash, String ext) {
        long timestamp = System.currentTimeMillis();
        return String.format("users/%d/avatar/%d_%s.%s", userId, timestamp, hash.substring(0, 16), ext);
    }

    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * 根据MIME类型判断是否为图片
     */
    public static boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    /**
     * 根据扩展名判断是否为视频
     */
    public static boolean isVideo(String filename) {
        if (filename == null) return false;
        String ext = getFileExtension(filename).toLowerCase();
        return ext.matches("mp4|mov|avi|mkv|flv|wmv");
    }
}