package com.hailong.chatsystem.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendGroupVO {
    private Long groupId;
    private String groupName;
    private Integer sortOrder;
    private String color;
    private String description;
    private String icon;
    private Boolean isDefault;
    private Boolean isVisible;
    private Integer friendCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}