package com.mobisec.in.userservice.service.UserService;
import com.mobisec.in.userservice.dto.UserVerifiedEvent;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;

import java.io.IOException;

public interface UserEventListener {
    void handleUserVerifiedEvent(UserVerifiedEvent event, Message message, Channel channel) throws IOException;
}
