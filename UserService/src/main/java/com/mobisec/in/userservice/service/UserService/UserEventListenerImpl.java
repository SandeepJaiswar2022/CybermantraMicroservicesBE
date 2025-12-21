package com.mobisec.in.userservice.service.UserService;

// ============================================================================
// service/UserEventListener.java
// ============================================================================
import com.mobisec.in.userservice.config.RabbitMQConfig;
import com.mobisec.in.userservice.dto.UserVerifiedEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventListenerImpl implements UserEventListener {

    private final UserService userService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.retry.max-attempts}")
    private int maxRetryAttempts;

    /**
     * Listen to USER_VERIFIED events with manual acknowledgment
     */
    @RabbitListener(queues = RabbitMQConfig.MAIN_QUEUE)
    public void handleUserVerifiedEvent(UserVerifiedEvent event, Message message, Channel channel)
            throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("Received USER_VERIFIED event: {}", event);

            // Get retry count from x-death header
            int retryCount = getRetryCount(message);
            log.debug("Processing attempt: {}", retryCount + 1);

            // Process the event
            userService.createUserProfile(event);

            // Acknowledge successful processing
            channel.basicAck(deliveryTag, false);
            log.info("Message acknowledged successfully for userId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Error processing USER_VERIFIED event: {}", event, e);

            int retryCount = getRetryCount(message);

            // Check if max retries exceeded
            if (retryCount >= maxRetryAttempts - 1) {
                log.error("Max retry attempts ({}) reached for userId: {}. Moving to DLQ.",
                        maxRetryAttempts, event.getUserId());

                // Send to DLQ manually
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.DLQ_EXCHANGE,
                        RabbitMQConfig.DLQ_ROUTING_KEY,
                        event
                );

                // Acknowledge to remove from queue
                channel.basicAck(deliveryTag, false);
            } else {
                // Reject and requeue to retry queue
                log.warn("Rejecting message for retry. Attempt: {}/{}",
                        retryCount + 1, maxRetryAttempts);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /**
     * Extract retry count from x-death header
     */
    private int getRetryCount(Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        List<?> xDeathHeader = (List<?>) headers.get("x-death");

        if (xDeathHeader != null && !xDeathHeader.isEmpty()) {
            Map<?, ?> death = (Map<?, ?>) xDeathHeader.get(0);
            Long count = (Long) death.get("count");
            return count != null ? count.intValue() : 0;
        }

        return 0;
    }
}
