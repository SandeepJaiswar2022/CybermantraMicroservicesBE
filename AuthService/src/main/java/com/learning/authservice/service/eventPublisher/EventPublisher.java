package com.learning.authservice.service.eventPublisher;

import com.learning.authservice.dto.UserVerifiedEvent;
import org.springframework.stereotype.Service;

public interface EventPublisher {
    void publishUserVerifiedEvent(UserVerifiedEvent event);
}

