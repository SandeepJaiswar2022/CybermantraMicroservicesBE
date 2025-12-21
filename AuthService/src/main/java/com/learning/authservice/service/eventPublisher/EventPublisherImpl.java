package com.learning.authservice.service.eventPublisher;

import com.learning.authservice.configuration.RabbitMQConfig;
import com.learning.authservice.dto.UserVerifiedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherImpl implements EventPublisher{

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishUserVerifiedEvent(UserVerifiedEvent event) {

        String correlationId = UUID.randomUUID().toString();
        CorrelationData correlationData = new CorrelationData(correlationId);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                event,
                correlationData
        );

        log.info("Published USER_VERIFIED event with correlationId={}", correlationId);
    }
}
