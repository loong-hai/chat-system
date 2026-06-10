package com.hailong.chatsystem.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendRequestHandleDTO {

    @NotNull(message = "请求ID不能为空")
    private Long requestId;

    @NotNull(message = "处理类型不能为空")
    private Boolean accept; // true: 同意, false: 拒绝

    @Size(max = 200, message = "拒绝理由不能超过200个字符")
    private String rejectReason;
}