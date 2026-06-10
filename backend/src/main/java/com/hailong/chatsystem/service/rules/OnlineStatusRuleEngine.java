package com.hailong.chatsystem.service.rules;

import com.hailong.chatsystem.model.dto.ChatMessageDTO;
import com.hailong.chatsystem.service.cache.UserStatusService;
import com.hailong.chatsystem.service.websocket.WebSocketSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Slf4j
@Component
public class OnlineStatusRuleEngine {

    @Autowired
    private WebSocketSessionService webSocketSessionService;

    @Autowired
    private UserStatusService userStatusService;

    // 规则注册表
    private final Map<String, BiFunction<Long, ChatMessageDTO, RuleResult>> ruleRegistry = new HashMap<>();

    public OnlineStatusRuleEngine() {
        registerAllRules();
    }

    /**
     * 执行完整规则检查（物理连接 + 业务状态）
     */
    public RuleResult checkRules(Long receiverId, ChatMessageDTO message) {
        // ===== 第1步：物理连接检查（WebSocket）=====
        boolean isPhysicallyOnline = webSocketSessionService.isUserOnline(receiverId);

        if (!isPhysicallyOnline) {
            return new RuleResult(false, "用户物理离线（WebSocket未连接）",
                    RuleAction.STORE_OFFLINE);
        }

        // 到这里说明WebSocket连着，检查各项业务规则
        for (Map.Entry<String, BiFunction<Long, ChatMessageDTO, RuleResult>> entry : ruleRegistry.entrySet()) {
            RuleResult result = entry.getValue().apply(receiverId, message);
            if (!result.isAllow()) {
                log.debug("规则拦截: rule={}, receiverId={}, reason={}",
                        entry.getKey(), receiverId, result.getMessage());
                return result;
            }
        }

        return new RuleResult(true, "所有规则通过，允许推送", RuleAction.ALLOW_PUSH);
    }

    /**
     * 注册完整规则集
     */
    private void registerAllRules() {
        // 规则1: 隐身状态规则
        registerRule("INVISIBLE_RULE", (receiverId, message) -> {
            if (!userStatusService.isInvisible(receiverId)) {
                return new RuleResult(true, "用户未隐身", null);
            }

            // 系统消息始终放行
            Long senderId = message.getSenderId();
            if (senderId == null || senderId == 0L) {
                return new RuleResult(true, "系统消息放行", null);
            }

            // 简化：隐身即离线（对白名单所有人）
            return new RuleResult(false, "用户隐身，消息转为离线存储", RuleAction.STORE_OFFLINE);
        });

        // 规则2: 忙碌状态规则
        registerRule("BUSY_RULE", (receiverId, message) -> {
            if (!userStatusService.isBusy(receiverId)) {
                return new RuleResult(true, "用户不忙碌", null);
            }

            // 检查消息是否为紧急消息
            boolean isUrgent = message.getExtra() != null &&
                    "true".equals(message.getExtra().get("urgent"));

            // 检查发送者是否为重要联系人（亲密度高）
            int intimacy = userStatusService.getIntimacy(receiverId, message.getSenderId());
            boolean isImportantContact = intimacy >= 2;  // STARRED级别

            if (isUrgent || isImportantContact) {
                log.debug("忙碌用户接收紧急/重要消息: receiverId={}, urgent={}, intimacy={}",
                        receiverId, isUrgent, intimacy);
                return new RuleResult(true, "忙碌但消息紧急或发送者重要", null);
            }

            // 忙碌时不是紧急消息，延迟推送
            return new RuleResult(false, "用户忙碌，非紧急消息延迟推送",
                    RuleAction.DELAY_PUSH);
        });

        // 规则3: 免打扰时间规则
        registerRule("DO_NOT_DISTURB_RULE", (receiverId, message) -> {
            if (!userStatusService.isInDoNotDisturbPeriod(receiverId)) {
                return new RuleResult(true, "不在免打扰时段", null);
            }

            // 检查是否为紧急消息
            boolean isUrgent = message.getExtra() != null &&
                    "true".equals(message.getExtra().get("urgent"));

            if (isUrgent) {
                return new RuleResult(true, "免打扰时段但消息紧急", null);
            }

            // 免打扰时段非紧急消息，延迟到结束时段
            return new RuleResult(false, "免打扰时段，非紧急消息延迟",
                    RuleAction.DELAY_PUSH);
        });

        // 规则4: 消息频率限制（保留接口，简化实现）
        registerRule("RATE_LIMIT_RULE", (receiverId, message) -> {
            // TODO: 如需实现频率限制，可在此集成Redis限流
            // 目前默认放行
            return new RuleResult(true, "频率检查通过", null);
        });
    }

    /**
     * 注册规则
     */
    public void registerRule(String ruleName, BiFunction<Long, ChatMessageDTO, RuleResult> rule) {
        ruleRegistry.put(ruleName, rule);
    }

    /**
     * 规则结果类
     */
    public static class RuleResult {
        private final boolean allow;
        private final String message;
        private final RuleAction action;
        private final long delaySeconds;  // 延迟推送的秒数

        public RuleResult(boolean allow, String message, RuleAction action) {
            this(allow, message, action, 0);
        }

        public RuleResult(boolean allow, String message, RuleAction action, long delaySeconds) {
            this.allow = allow;
            this.message = message;
            this.action = action;
            this.delaySeconds = delaySeconds;
        }

        public boolean isAllow() { return allow; }
        public String getMessage() { return message; }
        public RuleAction getAction() { return action; }
        public long getDelaySeconds() { return delaySeconds; }
    }

    /**
     * 规则动作枚举
     */
    public enum RuleAction {
        ALLOW_PUSH,        // 允许推送
        STORE_OFFLINE,     // 存储为离线消息
        DELAY_PUSH,        // 延迟推送
        BLOCK,             // 阻止推送（拉黑等极端情况）
        NOTIFY_SENDER      // 通知发送者（如对方忙碌）
    }
}