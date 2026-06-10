package com.hailong.chatsystem.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendUpdateDTO {

    @Size(max = 50, message = "备注名不能超过50个字符")
    private String remark;

    private Long groupId;
    private Boolean isPinned;
    private Boolean isMuted;
    private Integer intimacyLevel;
}