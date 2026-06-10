package com.hailong.chatsystem.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendRequestDTO {

    @NotNull(message = "接收者ID不能为空")
    private Long receiverId;

    @Size(max = 200, message = "申请信息不能超过200个字符")
    private String message;

    private String source;
}