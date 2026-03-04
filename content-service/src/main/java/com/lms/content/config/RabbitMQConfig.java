package com.lms.content.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ exchanges, queues, and bindings.
 *
 * <p>Topology:
 * <pre>
 * lms.content.exchange (topic)
 *   ├── content.event.uploaded   → content.uploaded (queue)
 *   ├── content.event.deleted    → content.deleted  (queue)
 *   ├── content.transcode.request → content.transcode.request (queue)
 *   └── content.transcode.complete → content.transcode.complete (queue)
 * </pre>
 *
 * <p>Topic exchange allows flexible routing. A transcoding service can bind to
 * {@code content.transcode.*} to receive all transcoding-related events.
 *
 * <p>All queues are durable — messages survive RabbitMQ restarts.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${messaging.exchange}")
    private String exchangeName;

    @Value("${messaging.queues.content-uploaded}")
    private String uploadedQueue;

    @Value("${messaging.queues.content-deleted}")
    private String deletedQueue;

    @Value("${messaging.queues.transcode-request}")
    private String transcodeRequestQueue;

    @Value("${messaging.queues.transcode-complete}")
    private String transcodeCompleteQueue;

    @Value("${messaging.routing-keys.content-uploaded}")
    private String uploadedRoutingKey;

    @Value("${messaging.routing-keys.content-deleted}")
    private String deletedRoutingKey;

    @Value("${messaging.routing-keys.transcode-request}")
    private String transcodeRequestRoutingKey;

    @Value("${messaging.routing-keys.transcode-complete}")
    private String transcodeCompleteRoutingKey;

    @Bean
    public TopicExchange contentExchange() {
        return ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
    }

    @Bean public Queue uploadedQueue()          { return new Queue(uploadedQueue, true); }
    @Bean public Queue deletedQueue()           { return new Queue(deletedQueue, true); }
    @Bean public Queue transcodeRequestQueue()  { return new Queue(transcodeRequestQueue, true); }
    @Bean public Queue transcodeCompleteQueue() { return new Queue(transcodeCompleteQueue, true); }

    @Bean
    public Binding uploadedBinding() {
        return BindingBuilder.bind(uploadedQueue()).to(contentExchange()).with(uploadedRoutingKey);
    }

    @Bean
    public Binding deletedBinding() {
        return BindingBuilder.bind(deletedQueue()).to(contentExchange()).with(deletedRoutingKey);
    }

    @Bean
    public Binding transcodeRequestBinding() {
        return BindingBuilder.bind(transcodeRequestQueue()).to(contentExchange()).with(transcodeRequestRoutingKey);
    }

    @Bean
    public Binding transcodeCompleteBinding() {
        return BindingBuilder.bind(transcodeCompleteQueue()).to(contentExchange()).with(transcodeCompleteRoutingKey);
    }

    /** Use Jackson for JSON serialization instead of Java serialization (binary, brittle). */
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
