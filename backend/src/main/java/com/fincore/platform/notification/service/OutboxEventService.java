package com.fincore.platform.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.platform.notification.entity.OutboxEvent;
import com.fincore.platform.notification.entity.OutboxStatus;
import com.fincore.platform.notification.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an event to the Transactional Outbox table in the current database transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publishEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        String payloadJson;
        try {
            payloadJson = payload instanceof String ? (String) payload : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for {} {}", aggregateType, aggregateId, e);
            payloadJson = "{\"raw\":\"" + payload.toString().replace("\"", "\\\"") + "\"}";
        }

        OutboxEvent event = new OutboxEvent(aggregateType, aggregateId, eventType, payloadJson);
        OutboxEvent saved = outboxEventRepository.save(event);
        log.info("Published outbox event [{}] for aggregate [{} / {}] with eventType [{}]",
                saved.getId(), aggregateType, aggregateId, eventType);
        return saved;
    }

    /**
     * Dispatches pending outbox events (simulates message broker publishing / worker consumption).
     */
    @Transactional
    public int processPendingEvents(int batchSize) {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, batchSize));

        int processedCount = 0;
        for (OutboxEvent event : pending) {
            try {
                // In production, this dispatches to Kafka, RabbitMQ, or SQS
                log.info("Processing outbox event [{}] of type [{}]: {}", event.getId(), event.getEventType(), event.getPayload());
                event.markProcessed();
                outboxEventRepository.save(event);
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to process outbox event [{}]", event.getId(), e);
                event.markFailed(e.getMessage());
                outboxEventRepository.save(event);
            }
        }
        return processedCount;
    }
}

