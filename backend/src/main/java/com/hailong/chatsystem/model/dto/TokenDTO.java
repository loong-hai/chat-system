package com.hailong.chatsystem.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenDTO {
    private String accessToken;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private Long userId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String sessionId;  // 新增
    private String deviceId;   // 新增

    public TokenDTO(String accessToken, Long expiresIn, Long userId,
                    String username, String nickname, String avatarUrl) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
    }
}