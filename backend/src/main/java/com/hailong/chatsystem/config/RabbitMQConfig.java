// config/RabbitMQConfig.java
package com.hailong.chatsystem.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hailong.chatsystem.model.dto.ChatMessageDTO;
import com.hailong.chatsystem.service.message.MessagePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableRabbit
public class RabbitMQConfig {
    @Autowired
    private InstanceInfo instanceInfo;
    @Autowired
    private MessagePersistenceService messagePersistenceService;


    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:guest}")
    private String username;

    @Value("${spring.rabbitmq.password:guest}")
    private String password;

    @Value("${spring.rabbitmq.virtual-host:/}")
    private String virtualHost;

    // 消息队列定义
    public static final String CHAT_MESSAGE_QUEUE = "chat.message.queue";
    public static final String CHAT_MESSAGE_EXCHANGE = "chat.message.exchange";
    public static final String CHAT_MESSAGE_ROUTING_KEY = "chat.message.routing";

    public static final String OFFLINE_MESSAGE_QUEUE = "chat.offline.queue";
    public static final String OFFLINE_MESSAGE_EXCHANGE = "chat.offline.exchange";
    public static final String OFFLINE_MESSAGE_ROUTING_KEY = "chat.offline.routing";

    public static final String SYSTEM_NOTIFICATION_QUEUE = "chat.system.notification.queue";
    public static final String SYSTEM_NOTIFICATION_EXCHANGE = "chat.system.notification.exchange";
    public static final String SYSTEM_NOTIFICATION_ROUTING_KEY = "chat.system.notification.routing";

    public static final String DEAD_LETTER_QUEUE = "chat.dead.letter.queue";
    public static final String DEAD_LETTER_EXCHANGE = "chat.dead.letter.exchange";
    // 路由
    public static final String PUSH_EXCHANGE = "chat.push.exchange";
    public static final String PUSH_QUEUE_PREFIX = "chat.push.queue.";

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        connectionFactory.setVirtualHost(virtualHost);
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        return connectionFactory;
    }

    @Bean
    public DirectExchange pushExchange() {
        return new DirectExchange(PUSH_EXCHANGE, true, false);
    }

    @Bean
    public Queue pushQueue(InstanceInfo instanceInfo) {
        // 独占、自动删除队列
        return new Queue(PUSH_QUEUE_PREFIX + instanceInfo.getInstanceId(), false, true, true);
    }

    @Bean
    public Binding pushBinding(Queue pushQueue, DirectExchange pushExchange, InstanceInfo instanceInfo) {
        return BindingBuilder.bind(pushQueue)
                .to(pushExchange)
                .with(instanceInfo.getInstanceId()); // 路由键为实例ID
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
        rabbitTemplate.setMandatory(true);

        // 消息确认回调（原有）
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                // 消息成功到达Broker
            } else {
                // 消息发送失败
            }
        });

        // 消息返回回调
        rabbitTemplate.setReturnsCallback(returnedMessage -> {
            log.warn("消息无法路由: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returnedMessage.getExchange(), returnedMessage.getRoutingKey(),
                    returnedMessage.getReplyCode(), returnedMessage.getReplyText());

            try {
                // 正确反序列化消息体
                byte[] body = returnedMessage.getMessage().getBody();
                ChatMessageDTO message = objectMapper.readValue(body, ChatMessageDTO.class);

                String messageId = message.getMessageId();
                if (messageId != null) {
                    log.info("标记消息为离线状态: messageId={}", messageId);
                    messagePersistenceService.updateMessageStatus(messageId, "OFFLINE", LocalDateTime.now());
                }
            } catch (Exception e) {
                log.error("处理退回消息失败，无法解析消息体", e);
                // 尝试从CorrelationData获取messageId
                if (returnedMessage.getMessage().getMessageProperties() != null) {
                    String messageId = returnedMessage.getMessage().getMessageProperties().getMessageId();
                    if (messageId != null) {
                        messagePersistenceService.updateMessageStatus(messageId, "OFFLINE", LocalDateTime.now());
                    }
                }
            }
        });

        return rabbitTemplate;
    }

    @Bean
    public Queue chatMessageQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        args.put("x-dead-letter-routing-key", DEAD_LETTER_QUEUE);
        args.put("x-max-length", 10000); // 最大队列长度
        args.put("x-message-ttl", 86400000); // 消息TTL 24小时
        return new Queue(CHAT_MESSAGE_QUEUE, true, false, false, args);
    }

    @Bean
    public DirectExchange chatMessageExchange() {
        return new DirectExchange(CHAT_MESSAGE_EXCHANGE, true, false);
    }

    @Bean
    public Binding chatMessageBinding() {
        return BindingBuilder.bind(chatMessageQueue())
                .to(chatMessageExchange())
                .with(CHAT_MESSAGE_ROUTING_KEY);
    }

    @Bean
    public Queue offlineMessageQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        args.put("x-max-length", 50000); // 离线消息队列可以更大
        args.put("x-message-ttl", 604800000); // 7天
        return new Queue(OFFLINE_MESSAGE_QUEUE, true, false, false, args);
    }

    @Bean
    public DirectExchange offlineMessageExchange() {
        return new DirectExchange(OFFLINE_MESSAGE_EXCHANGE, true, false);
    }

    @Bean
    public Binding offlineMessageBinding() {
        return BindingBuilder.bind(offlineMessageQueue())
                .to(offlineMessageExchange())
                .with(OFFLINE_MESSAGE_ROUTING_KEY);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DEAD_LETTER_QUEUE);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(5); // 并发消费者数量
        factory.setMaxConcurrentConsumers(10); // 最大并发消费者
        factory.setPrefetchCount(10); // 每次预取数量
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); // 手动确认
        return factory;
    }
}