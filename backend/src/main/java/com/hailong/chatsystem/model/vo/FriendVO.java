package com.hailong.chatsystem.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendVO {
    private Long relationId;
    private Long friendId;
    private String username;
    private String nickname;
    private String remark;
    private String displayName;
    private String avatarUrl;
    private String signature;
    private Integer onlineStatus;
    private String onlineStatusText;
    private Boolean isOnline;
    private Long groupId;
    private String groupName;
    private String groupColor;
    private Boolean isPinned;
    private Boolean isMuted;
    private Integer intimacyLevel;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastInteraction;

    // 物理连接状态（WebSocket是否连着）
    private Boolean isPhysicallyOnline;

    // 业务设置状态（online/busy/invisible，来自Redis）
    private String bizStatus;

    // 对当前查看者是否可见地在线（计算后的最终展示状态）
    private Boolean isVisibleOnline;
}