package com.hailong.chatsystem.repository;

import com.hailong.chatsystem.model.entity.FileIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query; // 修改这里
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface FileIndexRepository extends JpaRepository<FileIndex, Long> {

    /**
     * 原子增加引用计数（解决竞态条件）
     * @return 影响的行数，0表示记录不存在
     */
    @Modifying
    @Transactional
    @Query("UPDATE FileIndex f SET f.refCount = f.refCount + 1, f.updatedAt = :now WHERE f.fileHash = :hash")
    int incrementRefCount(@Param("hash") String hash, @Param("now") LocalDateTime now);

    /**
     * 原子减少引用计数
     */
    @Modifying
    @Transactional
    @Query("UPDATE FileIndex f SET f.refCount = f.refCount - 1, f.updatedAt = :now WHERE f.fileHash = :hash AND f.refCount > 0")
    int decrementRefCount(@Param("hash") String hash, @Param("now") LocalDateTime now);

    /**
     * 根据文件Hash查找索引（秒传核心查询）
     */
    Optional<FileIndex> findByFileHash(String fileHash);

    /**
     * 检查Hash是否存在（快速存在性检查，比查全对象快）
     */
    boolean existsByFileHash(String fileHash);

    /**
     * 根据S3 Key查找文件索引（用于删除时查询）
     * 注意：多个用户可能引用同一个S3Key（秒传复用），此方法返回第一个
     */
    Optional<FileIndex> findByS3Key(String s3Key);
}