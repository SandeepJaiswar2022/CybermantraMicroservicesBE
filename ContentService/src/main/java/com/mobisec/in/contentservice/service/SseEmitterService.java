package com.mobisec.in.contentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE Emitter Service — backed by Redis Pub/Sub for cross-pod delivery.
 *
 * Problem solved:
 * Without Redis, when a transcoding event arrives on Pod B but the client
 * SSE connection lives on Pod A, the status push is silently dropped.
 *
 * Solution:
 * - Each pod subscribes to a Redis channel for every SSE connection it holds.
 * - When any pod calls notifyStatusChange(), it publishes to Redis.
 * - Redis broadcasts to ALL pods.
 * - Each pod checks its LOCAL emitters map and pushes to connected browsers.
 *
 * Result: It doesn't matter which pod the browser is connected to or which
 * pod processes the transcoding event. The update always reaches the browser.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseEmitterService {

    private final StringRedisTemplate           redisTemplate;
    private final RedisMessageListenerContainer redisListenerContainer;
    private final ObjectMapper                  objectMapper;

    // In-memory map: contentId → emitters connected to THIS pod only
    private final Map<UUID, List<SseEmitter>> localEmitters = new ConcurrentHashMap<>();

    // Redis channel prefix — one channel per contentId
    private static final String CHANNEL_PREFIX = "content-status:";

    // SSE timeout: 10 minutes. Browser EventSource auto-reconnects after timeout.
    private static final long SSE_TIMEOUT_MS = 600_000L;

    /**
     * Called by the controller when a browser subscribes to /status/stream.
     *
     * Creates a SseEmitter, registers it locally, and subscribes this pod
     * to the Redis channel for this contentId.
     *
     * Multiple browser tabs watching the same content each get their own emitter.
     */
    public SseEmitter createEmitter(UUID contentId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Register cleanup callbacks — prevents memory leaks
        emitter.onCompletion(() -> removeEmitter(contentId, emitter));
        emitter.onTimeout(()     -> { removeEmitter(contentId, emitter); emitter.complete(); });
        emitter.onError((e)      -> removeEmitter(contentId, emitter));

        // Add to local registry
        localEmitters.computeIfAbsent(contentId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Subscribe this pod to the Redis channel for this contentId.
        // When any pod publishes to this channel, our onMessage() fires.
        String channel = CHANNEL_PREFIX + contentId;
        redisListenerContainer.addMessageListener(buildRedisListener(), new ChannelTopic(channel));

        log.info("SSE emitter registered: contentId={}, localCount={}",
                contentId, localEmitters.get(contentId).size());

        return emitter;
    }

    /**
     * Publish a status change to the Redis channel for this contentId.
     *
     * This can be called from ANY pod. Redis broadcasts to all pods.
     * Each pod's Redis listener then pushes to its local SSE connections.
     */
    public void notifyStatusChange(UUID contentId, String status) {
        String channel = CHANNEL_PREFIX + contentId;
        String message = serializeStatusMessage(contentId, status);
        redisTemplate.convertAndSend(channel, message);
        log.debug("Redis publish: channel={}, status={}", channel, status);
    }

    /**
     * Complete and remove all emitters for a contentId.
     * Called when content reaches a terminal state (READY, FAILED, DELETED).
     */
    public void completeEmitters(UUID contentId) {
        List<SseEmitter> emitters = localEmitters.remove(contentId);
        if (emitters != null) {
            emitters.forEach(SseEmitter::complete);
            log.info("All SSE emitters completed: contentId={}", contentId);
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Builds a Redis MessageListener that, when a message arrives,
     * pushes it to all local SSE connections for that contentId.
     *
     * This listener fires on THIS pod whenever any pod publishes
     * to the Redis channel.
     */
    private MessageListener buildRedisListener() {
        return (message, pattern) -> {
            String body    = new String(message.getBody());
            String channel = new String(message.getChannel());

            // Extract contentId from channel name: "content-status:{uuid}"
            String contentIdStr = channel.replace(CHANNEL_PREFIX, "");
            UUID contentId;
            try {
                contentId = UUID.fromString(contentIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid contentId in Redis channel: {}", channel);
                return;
            }

            // Push to all local emitters for this contentId
            pushToLocalEmitters(contentId, body);
        };
    }

    private void pushToLocalEmitters(UUID contentId, String jsonPayload) {
        List<SseEmitter> emitters = localEmitters.get(contentId);
        if (emitters == null || emitters.isEmpty()) return;

        // CopyOnWriteArrayList: safe to iterate while removing in callbacks
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(jsonPayload));
                log.debug("SSE push: contentId={}", contentId);
            } catch (IOException e) {
                // Client disconnected — cleanup handled by onError callback
                log.debug("SSE client disconnected: contentId={}", contentId);
                removeEmitter(contentId, emitter);
            }
        }
    }

    private void removeEmitter(UUID contentId, SseEmitter emitter) {
        List<SseEmitter> emitters = localEmitters.get(contentId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                localEmitters.remove(contentId);
            }
        }
    }

    private String serializeStatusMessage(UUID contentId, String status) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("contentId", contentId.toString(), "status", status));
        } catch (Exception e) {
            return "{\"contentId\":\"" + contentId + "\",\"status\":\"" + status + "\"}";
        }
    }
}
