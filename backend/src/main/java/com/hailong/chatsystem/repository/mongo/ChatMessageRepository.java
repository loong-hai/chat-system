package com.hailong.chatsystem.repository.mongo;

import com.hailong.chatsystem.model.document.ChatMessageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessageDocument, String> {

    /**
     * 根据会话ID分页查询消息（排除已删除）
     */
    @Query("{'conversation_id': ?0, 'is_deleted': false}")
    Page<ChatMessageDocument> findByConversationId(String conversationId, Pageable pageable);

    /**
     * 【修复】查找用户未读消息（状态为SENT且未删除）
     * 修正：使用 MongoDB 字段名（下划线）而非 Java 字段名
     */
    @Query("{'receiver_id': ?0, 'status': 'SENT', 'is_deleted': false, 'is_recalled': false}")
    List<ChatMessageDocument> findUnreadMessages(Long receiverId);

    /**
     * 【修复】查找指定时间范围的消息
     * 使用 MongoDB 字段名
     */
    @Query("{'sender_id': ?0, 'server_time': {'$gte': ?1, '$lte': ?2}}")
    List<ChatMessageDocument> findBySenderIdAndServerTimeBetween(
            Long senderId, LocalDateTime start, LocalDateTime end);

    /**
     * 根据messageId查询
     */
    Optional<ChatMessageDocument> findByMessageId(String messageId);

    /**
     * 检查消息是否存在
     */
    boolean existsByMessageId(String messageId);

    /**
     * 【修复】更新消息状态 - 使用正确的字段名
     */
    @Query("{'msg_id': ?0}")  // messageId 字段在 MongoDB 中是 msg_id
    @Update("{'$set': {'status': ?1, 'updated_at': ?2}}")  // 使用 updated_at 而非 updateTime
    void updateStatusByMessageId(String messageId, String status, LocalDateTime updateTime);

    /**
     * 【修复】批量标记消息为已读
     */
    @Query("{'_id': {'$in': ?0}}")
    @Update("{'$set': {'status': 'READ', 'read_at': ?1}}")
    void markAsRead(List<String> messageIds, LocalDateTime readTime);

    /**
     * 【修复】查找用户的离线消息（状态为OFFLINE或FAILED）
     * 关键修复：使用 receiver_id, status, is_deleted（下划线命名）
     */
    @Query("{ \"receiver_id\": ?0, \"$or\": [{ \"status\": \"OFFLINE\" }, { \"status\": \"FAILED\" }], \"is_deleted\": false }")
    List<ChatMessageDocument> findOfflineMessages(Long receiverId);
    /**
     * 【修复】根据发送者ID和消息ID查询
     */
    @Query("{'sender_id': ?0, 'msg_id': ?1}")
    List<ChatMessageDocument> findBySenderIdAndMessageId(Long senderId, String messageId);

    /**
     * 查询接收者在指定时间之后的已读消息（功能1使用）
     */
    @Query("{'receiver_id': ?0, 'status': ?1, 'server_time': {'$gte': ?2}, 'is_deleted': false}")
    List<ChatMessageDocument> findByReceiverIdAndStatusAndServerTimeAfter(
            Long receiverId, String status, LocalDateTime after);

    /**
     * 查询用户参与的所有消息（发送或接收）
     */
    @Query("{$or: [{'sender_id': ?0}, {'receiver_id': ?0}], 'server_time': {'$gte': ?1, '$lte': ?2}, 'is_deleted': false}")
    List<ChatMessageDocument> findByUserIdAndTimeRange(Long userId, LocalDateTime start, LocalDateTime end);

    /**
     * 按会话ID查询最近消息（功能2使用）
     */
    List<ChatMessageDocument> findByConversationIdOrderByServerTimeDesc(
            String conversationId, Pageable pageable);

    /**
     * 查询指定会话在时间范围内的消息
     */
    @Query("{'conversation_id': ?0, 'server_time': {'$gte': ?1}, 'is_deleted': false}")
    List<ChatMessageDocument> findByConversationIdAndServerTimeAfterOrderByServerTimeDesc(
            String conversationId, LocalDateTime after);
}