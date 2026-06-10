package com.hailong.chatsystem.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserUpdateDTO {

    @Size(min = 1, max = 50, message = "昵称长度必须在1-50个字符之间")
    private String nickname;

    @Size(max = 500, message = "头像URL过长")
    private String avatarUrl;

    @Pattern(regexp = "^[0-2]$", message = "性别格式错误")
    private String gender;

    private LocalDate birthday;

    @Size(max = 200, message = "个性签名不能超过200个字符")
    private String signature;

    @Email(regexp = "^$|^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}