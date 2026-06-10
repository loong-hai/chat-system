// interceptor/WebSocketHandshakeInterceptor.java
package com.hailong.chatsystem.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hailong.chatsystem.exception.InvalidTokenException;
import com.hailong.chatsystem.exception.TokenExpiredException;
import com.hailong.chatsystem.security.jwt.JwtTokenProvider;
import com.hailong.chatsystem.service.cache.UserSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
@Slf4j
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    ObjectMapper mapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        try {
            String query = request.getURI().getQuery();
            if (query == null) {
                log.warn("WebSocket握手失败: 无查询参数");
                return false;
            }

            // 提取token
            String token = null;
            if (query.contains("token=")) {
                token = query.substring(query.indexOf("token=") + 6);
                // 移除可能的其他参数
                if (token.contains("&")) {
                    token = token.substring(0, token.indexOf("&"));
                }
            }

            if (query == null) {
                throw new InvalidTokenException("缺少认证参数");
            }


            // 验证 Token（会抛出 ExpiredJwtException 等）
            if (!jwtTokenProvider.validateToken(token)) {
                throw new InvalidTokenException("Token 验证失败");
            }

            // 检查是否过期（根据业务需要）
            if (jwtTokenProvider.isTokenExpired(token)) {
                throw new TokenExpiredException("Token 已过期，请重新登录");
            }

            // 获取用户信息
            String username = jwtTokenProvider.getUsernameFromToken(token);
            Long userId = jwtTokenProvider.getUserIdFromToken(token);

            if (userId == null) {
                log.error("WebSocket握手失败: Token中未找到userId");
                return false;
            }

            // 提取客户端期望的状态（支持JWT直接登录场景）
            String clientStatus = extractParam(query, "status");
            if (!StringUtils.hasText(clientStatus)) {
                clientStatus = "online"; // 默认在线
            }

            // 验证状态合法性（防止非法状态污染系统）
            if (!isValidStatus(clientStatus)) {
                log.warn("非法的状态参数: {}，使用默认值online", clientStatus);
                clientStatus = "online";
            }


            // 存储到attributes，供后续使用
            attributes.put("userId", userId);
            attributes.put("username", username);
            attributes.put("token", token);
            attributes.put("clientStatus", clientStatus); // 关键！存储客户端状态

            log.info("WebSocket握手成功: userId={}, username={}, clientStatus={}", userId, username,clientStatus);
            return true;

        } catch (TokenExpiredException e) {
            log.warn("WebSocket 握手失败 - Token 过期: {}", e.getMessage());
            writeErrorResponse(response, 4010, e.getMessage());
            return false;
        } catch (InvalidTokenException e) {
            log.warn("WebSocket 握手失败 - Token 无效: {}", e.getMessage());
            writeErrorResponse(response, 4011, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("WebSocket 握手异常", e);
            writeErrorResponse(response, 5000, "服务器内部错误");
            return false;
        }
    }
    private String extractToken(String query) {
        if (query.contains("token=")) {
            String token = query.substring(query.indexOf("token=") + 6);
            if (token.contains("&")) {
                token = token.substring(0, token.indexOf("&"));
            }
            return token;
        }
        return null;
    }

    private void writeErrorResponse(ServerHttpResponse response, int code, String message) {
        try {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> error = new HashMap<>();
            error.put("code", code);
            error.put("message", message);
            error.put("timestamp", System.currentTimeMillis());

            byte[] bytes = mapper.writeValueAsBytes(error);
            response.getBody().write(bytes);
        } catch (IOException ioException) {
            log.error("写入错误响应失败", ioException);
        }
    }



    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket握手异常", exception);
        }
    }
    // 辅助方法：提取URL参数
    private String extractParam(String query, String paramName) {
        if (query == null) return null;
        String prefix = paramName + "=";
        int start = query.indexOf(prefix);
        if (start == -1) return null;
        start += prefix.length();
        int end = query.indexOf("&", start);
        if (end == -1) end = query.length();
        return query.substring(start, end);
    }

    // 辅助方法：验证状态合法性
    private boolean isValidStatus(String status) {
        return status.equals("online") || status.equals("busy")
                || status.equals("invisible") || status.equals("offline");
    }
}