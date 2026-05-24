package com.mobisec.in.contentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ── Exchange ──────────────────────────────────────────────────────────────
    public static final String CONTENT_EVENTS_EXCHANGE = "content.events";

    // ── Routing keys ─────────────────────────────────────────────────────────
    public static final String RK_CONTENT_UPLOADED = "content.event.uploaded";
    public static final String RK_CONTENT_READY    = "content.event.ready";
    public static final String RK_CONTENT_DELETED  = "content.event.deleted";

    // ── Queue names ───────────────────────────────────────────────────────────
    public static final String TRANSCODING_QUEUE  = "content.transcoding.queue";
    public static final String SEARCH_QUEUE       = "content.search.queue";
    public static final String NOTIFICATION_QUEUE = "content.notification.queue";

    // ── Dead-letter exchange (for failed messages) ────────────────────────────
    public static final String CONTENT_DLX = "content.dlx";

    /**
     * Topic exchange — routes by routing key pattern.
     * durable=true: survives RabbitMQ restart.
     */
    @Bean
    public TopicExchange contentEventsExchange() {
        return ExchangeBuilder.topicExchange(CONTENT_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(CONTENT_DLX).durable(true).build();
    }

    // ── Queues ────────────────────────────────────────────────────────────────

    @Bean
    public Queue transcodingQueue() {
        return QueueBuilder.durable(TRANSCODING_QUEUE)
                .withArgument("x-dead-letter-exchange", CONTENT_DLX)
                .withArgument("x-message-ttl", 86_400_000)  // 24 h TTL
                .build();
    }

    @Bean
    public Queue searchQueue() {
        return QueueBuilder.durable(SEARCH_QUEUE)
                .withArgument("x-dead-letter-exchange", CONTENT_DLX)
                .build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", CONTENT_DLX)
                .build();
    }

    // ── Bindings ─────────────────────────────────────────────────────────────
    // content.event.uploaded → transcoding + search
    // content.event.ready    → notification
    // content.event.deleted  → search (remove from index)

    @Bean
    public Binding transcodingBinding() {
        return BindingBuilder.bind(transcodingQueue())
                .to(contentEventsExchange()).with(RK_CONTENT_UPLOADED);
    }

    @Bean
    public Binding searchUploadedBinding() {
        return BindingBuilder.bind(searchQueue())
                .to(contentEventsExchange()).with(RK_CONTENT_UPLOADED);
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(contentEventsExchange()).with(RK_CONTENT_READY);
    }

    // ── Message converter ─────────────────────────────────────────────────────

    /**
     * Serialize/deserialize messages as JSON using Jackson.
     * Without this, Spring AMQP uses Java serialization — fragile and insecure.
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
