package com.hailong.chatsystem.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendGroupDTO {
    @NotBlank(message = "分组名称不能为空")
    @Size(min = 1, max = 20, message = "分组名称长度必须在1-20个字符之间")
    private String groupName;

    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder = 0;
}