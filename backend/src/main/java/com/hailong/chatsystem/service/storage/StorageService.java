package com.hailong.chatsystem.service.storage;

import com.hailong.chatsystem.model.dto.FileUploadResult;
import com.hailong.chatsystem.model.dto.PresignedUploadRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


public interface StorageService {

    /**
     * ============================================
     * 模式A：服务器中转上传（推荐用于小文件、头像）
     * ============================================
     */
    /**
     * 上传用户头像（强制服务器中转模式）
     * 流程：接收文件->压缩->计算Hash->秒传检查->上传S3->记录历史
     *
     * @param userId 用户ID（决定存储路径，同时用于权限校验）
     * @param file 头像文件
     * @return 上传结果（包含原图和缩略图URL）
     * @throws RuntimeException 文件类型不允许、压缩失败、上传失败等
     */
    FileUploadResult uploadAvatar(Long userId, MultipartFile file);

    /**
     * 上传聊天文件（服务器中转模式，适合<100MB文件）
     * 流程同头像，但不做压缩，只做Hash和秒传检查
     *
     * @param userId 发送者ID
     * @param file 文件
     * @param chatRelated 是否聊天相关（决定路径和权限）
     * @return 上传结果
     */
    FileUploadResult uploadChatFile(Long userId, MultipartFile file, boolean chatRelated);

    /**
     * ============================================
     * 模式B：预签名直传（推荐用于大文件 >=100MB）
     * ============================================
     */

    /**
     * 获取预签名上传URL（大文件上传第一步）
     * 流程：检查配额->秒传检查(如果提供Hash)->生成唯一Key->生成预签名URL->返回给前端
     *
     * 前端使用流程：
     * 1. 调用此接口获取presignedUrl
     * 2. 使用HTTP PUT/POST上传文件到presignedUrl（不经过本服务器）
     * 3. 上传成功后调用confirmUpload()通知后端
     *
     * @param userId 用户ID
     * @param request 预签名请求参数
     * @return 如果是秒传返回isRapidUpload=true和fileUrl；否则返回presignedUrl
     */
    FileUploadResult getPresignedUploadUrl(Long userId, PresignedUploadRequest request);

    /**
     * 确认预签名上传完成（大文件上传第二步）
     * 前端直传S3成功后调用，后端校验文件存在并记录元数据
     *
     * @param userId 用户ID
     * @param fileKey 文件Key（第一步返回的fileKey）
     * @param fileHash 文件Hash（前端计算的，后端用于校验一致性）
     * @return 最终文件URL
     */
    FileUploadResult confirmUpload(Long userId, String fileKey, String fileHash);

    /**
     * ============================================
     * 文件管理接口
     * ============================================
     */

    /**
     * 获取文件访问URL（支持CDN转换和预签名）
     *
     * @param fileKey S3 Key
     * @param forDownload 是否用于下载（是则强制预签名，否则根据isPublic决定）
     * @param expireMinutes 预签名过期时间（仅当需要预签名时有效）
     * @return 可访问的URL
     */
    String getFileUrl(String fileKey, boolean forDownload, int expireMinutes);

    /**
     * 删除文件（物理删除S3对象+清理数据库记录）
     * 安全校验：userId必须与文件所属用户匹配（头像历史表检查）
     *
     * @param userId 操作用户ID
     * @param fileKey 文件Key
     * @return 是否成功
     */
    boolean deleteFile(Long userId, String fileKey);

    /**
     * 获取用户头像历史列表
     */
    List<FileUploadResult> getAvatarHistory(Long userId);

    /**
     * 切换当前头像为历史版本
     *
     * @param userId 用户ID
     * @param historyId 历史记录ID
     * @return 切换后的头像URL
     */
    FileUploadResult switchAvatar(Long userId, Long historyId);
}