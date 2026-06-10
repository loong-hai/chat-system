package com.hailong.chatsystem.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "登录标识不能为空")
    private String identifier; // 可以是用户名、邮箱、手机号

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "登录状态不能为空")
    @Pattern(regexp = "^(online|busy|invisible|offline)$", message = "登录状态格式错误")
    private String status; // online, busy, invisible, offline
}