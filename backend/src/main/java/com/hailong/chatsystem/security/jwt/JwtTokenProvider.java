// security/jwt/JwtTokenProvider.java - 完整修正版
package com.hailong.chatsystem.security.jwt;

import com.hailong.chatsystem.config.JwtProperties;
import com.hailong.chatsystem.model.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expiration;
    private final String header;
    private final String prefix;

    @Autowired
    public JwtTokenProvider(JwtProperties jwtProperties) throws InvalidKeySpecException, NoSuchAlgorithmException {
        String secret = jwtProperties.getSecret();
        String salt = jwtProperties.getSalt();

        // 使用PBKDF2WithHmacSHA256派生密钥
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] saltBytes = (salt != null) ? salt.getBytes(StandardCharsets.UTF_8) : "default-salt".getBytes(StandardCharsets.UTF_8);
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), saltBytes, 65536, 512);
        SecretKey tmp = factory.generateSecret(spec);
        this.secretKey = new SecretKeySpec(tmp.getEncoded(), "HmacSHA512");

        this.expiration = jwtProperties.getExpiration();
        this.header = jwtProperties.getHeader();
        this.prefix = jwtProperties.getPrefix();
    }

    // 从token中获取用户名
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    // 从token中获取userId - 关键新增方法
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Object userIdObj = claims.get("userId");

            if (userIdObj == null) {
                log.warn("Token 中未包含 userId");
                return null;
            }

            // 防御性类型处理
            if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            } else if (userIdObj instanceof String) {
                try {
                    return Long.parseLong((String) userIdObj);
                } catch (NumberFormatException e) {
                    log.error("Token 中 userId 格式错误: {}", userIdObj);
                    return null;
                }
            } else {
                log.error("Token 中 userId 类型未知: {}", userIdObj.getClass());
                return null;
            }

        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期");
            return null;
        } catch (Exception e) {
            log.error("解析 Token 失败", e);
            return null;
        }
    }

    // 从token中获取过期时间
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    // 从token中获取指定的claim
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    // 从token中获取所有claims
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 检查token是否过期
    public Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    // 生成token - 支持UserDetails
    public String generateToken(UserDetails userDetails) {
        if (userDetails instanceof User) {
            return generateToken((User) userDetails);
        }
        // 如果不是User类型，使用用户名生成
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, userDetails.getUsername());
    }

    // 生成token - 支持User对象（关键修改）
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        // 将userId存入claims - 这是关键配置
        claims.put("userId", user.getUserId());
        claims.put("nickname", user.getNickname());
        claims.put("avatarUrl", user.getAvatarUrl());
        claims.put("email", user.getEmail());
        claims.put("phone", user.getPhone());
        claims.put("userStatus", user.getUserStatus());

        return doGenerateToken(claims, user.getUsername());
    }

    // 生成token - 支持用户名和userId
    public String generateToken(String username, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return doGenerateToken(claims, username);
    }

    // 生成token的具体实现
    private String doGenerateToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    // 验证token
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    // 验证token是否有效
    public Boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.error("JWT token已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("不支持的JWT token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("JWT token格式错误: {}", e.getMessage());
        } catch (SecurityException e) {
            log.error("JWT token签名无效: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT token参数错误: {}", e.getMessage());
        } catch (Exception e) {
            log.error("JWT token验证失败: {}", e.getMessage());
        }
        return false;
    }

    // 刷新token
    public String refreshToken(String token) {
        final Claims claims = getAllClaimsFromToken(token);
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    // 获取剩余过期时间
    public long getRemainingExpiration(String token) {
        try {
            final Date expiration = getExpirationDateFromToken(token);
            final Date now = new Date();
            long remaining = expiration.getTime() - now.getTime();
            return Math.max(remaining, 0); // 防止返回负数
        } catch (ExpiredJwtException e) {
            return 0; // Token已过期
        }
    }

    // 获取配置信息
    public String getHeader() {
        return header;
    }

    public String getPrefix() {
        return prefix;
    }
}