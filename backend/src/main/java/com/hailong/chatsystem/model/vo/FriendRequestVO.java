package com.hailong.chatsystem.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendRequestVO {
    private Boolean isSender; // true: 我是发送者, false: 我是接收者
    private Long requestId;
    private Long senderId;
    private String senderUsername;
    private String senderNickname;
    private String senderAvatarUrl;
    private Long receiverId;
    private String receiverUsername;
    private Integer status;
    private String statusText;
    private String message;
    private String source;
    private String rejectReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
    private Long remainingDays;
}