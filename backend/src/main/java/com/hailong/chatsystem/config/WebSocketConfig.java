// config/WebSocketConfig.java - 修正版
package com.hailong.chatsystem.config;

import com.hailong.chatsystem.interceptor.WebSocketAuthInterceptor;
import com.hailong.chatsystem.interceptor.WebSocketHandshakeInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Autowired
    private WebSocketHandshakeInterceptor webSocketHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(taskScheduler())
                .setHeartbeatValue(new long[]{10000, 10000}); // 10秒心跳，服务端20秒未收到即判定断开

        // 应用目的地前缀
        config.setApplicationDestinationPrefixes("/app");

        // 用户目的地前缀（用于点对点）
        config.setUserDestinationPrefix("/user");

        config.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 正确的方法调用：使用 allowedOriginPatterns
        registry.addEndpoint("/ws")
                .addInterceptors(webSocketHandshakeInterceptor)
                .setAllowedOriginPatterns("*")  // 使用通配符，解决跨域问题
                .withSockJS();  // 支持 SockJS 降级

        // 原生 WebSocket 端点
        registry.addEndpoint("/ws-native")
                .addInterceptors(webSocketHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
        registration.taskExecutor()
                .corePoolSize(10)
                .maxPoolSize(20)
                .queueCapacity(1000);

    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(10)
                .maxPoolSize(20)
                .queueCapacity(1000);
        log.info("WebSocket出站通道配置完成");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // 配置传输参数
        registration.setMessageSizeLimit(1024 * 1024 * 5); // 5MB
        registration.setSendTimeLimit(20 * 10000); // 20秒
        registration.setSendBufferSizeLimit(1024 * 1024 * 5); // 5MB

        // 心跳配置
        registration.setTimeToFirstMessage(30000); // 30秒超时
    }

    /**
     * 创建心跳调度器
     */
    private ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("websocket-heartbeat-thread-");
        taskScheduler.initialize();
        return taskScheduler;
    }
}