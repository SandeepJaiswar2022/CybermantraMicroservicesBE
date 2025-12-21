package com.mobisec.in.userservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    /** Main exchange, routing key and queue (same as Auth Service) */
    public static final String MAIN_EXCHANGE = "auth.user.exchange";
    public static final String MAIN_ROUTING_KEY = "user.verified";
    public static final String MAIN_QUEUE = "user.profile.queue";

    /** Retry setup */
    public static final String RETRY_EXCHANGE = "user.profile.retry.exchange";
    public static final String RETRY_QUEUE = "user.profile.retry.queue";
    public static final String RETRY_ROUTING_KEY = "user.profile.retry";

    /** DLQ setup */
    public static final String DLQ_EXCHANGE = "user.profile.dlq.exchange";
    public static final String DLQ_QUEUE = "user.profile.dlq";
    public static final String DLQ_ROUTING_KEY = "user.profile.dlq";

    @Value("${app.rabbitmq.retry.ttl}")
    private int retryTtl; // e.g. 5000 for 5 seconds

    // ----------------------------------------
    // MAIN EXCHANGE + QUEUE
    // ----------------------------------------

    @Bean
    public TopicExchange mainExchange() {
        return new TopicExchange(MAIN_EXCHANGE);
    }

    @Bean
    public Queue mainQueue() {
        Map<String, Object> args = new HashMap<>();

        // If MAIN queue fails → send to RETRY exchange
        args.put("x-dead-letter-exchange", RETRY_EXCHANGE);
        args.put("x-dead-letter-routing-key", RETRY_ROUTING_KEY);

        return new Queue(MAIN_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding mainBinding() {
        return BindingBuilder.bind(mainQueue())
                .to(mainExchange())
                .with(MAIN_ROUTING_KEY);
    }

    // ----------------------------------------
    // RETRY EXCHANGE + QUEUE
    // ----------------------------------------

    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE);
    }

    @Bean
    public Queue retryQueue() {
        Map<String, Object> args = new HashMap<>();

        // Time to wait before retrying
        args.put("x-message-ttl", retryTtl);

        // After TTL expires → send BACK to MAIN queue
        args.put("x-dead-letter-exchange", MAIN_EXCHANGE);
        args.put("x-dead-letter-routing-key", MAIN_ROUTING_KEY);

        return new Queue(RETRY_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding retryBinding() {
        return BindingBuilder.bind(retryQueue())
                .to(retryExchange())
                .with(RETRY_ROUTING_KEY);
    }

    // ----------------------------------------
    // DLQ EXCHANGE + QUEUE
    // ----------------------------------------

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }

    @Bean
    public Queue dlqQueue() {
        return new Queue(DLQ_QUEUE, true);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue())
                .to(dlqExchange())
                .with(DLQ_ROUTING_KEY);
    }

    // ----------------------------------------
    // JSON Converter
    // ----------------------------------------
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
