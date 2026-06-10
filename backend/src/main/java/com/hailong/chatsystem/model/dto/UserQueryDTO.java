package com.hailong.chatsystem.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserQueryDTO {
    @NotBlank(message = "查询关键词不能为空")
    private String keyword;  // 统一搜索关键词

    // 移除单独的 username/nickname/email/phone 字段
    // 保留以下字段
    private Integer userStatus;
    private Integer onlineStatus;
    private LocalDate registerDateStart;
    private LocalDate registerDateEnd;
    private Integer page = 1;
    private Integer size = 20;
}