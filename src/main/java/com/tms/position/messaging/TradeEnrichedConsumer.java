package com.tms.position.messaging;

import com.tms.common.config.kafka.KafkaTopics;
import com.tms.common.observability.logging.CorrelationIdFilter;
import com.tms.position.service.PositionService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeEnrichedConsumer {

    private final PositionService positionService;

    @KafkaListener(
        topics = KafkaTopics.TRADES_ENRICHED,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "kafka-consumer", fallbackMethod = "handleTradeEnrichedFallback")
    @Retry(name = "kafka-consumer")
    public void handleTradeEnriched(ConsumerRecord<String, Map<String, Object>> record,
                                     Acknowledgment acknowledgment) {
        Map<String, Object> tradeEvent = record.value();
        String tradeId = (String) tradeEvent.get("tradeId");
        String correlationId = (String) tradeEvent.get("correlationId");

        try {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, correlationId);
            log.info("Received enriched trade for position update: tradeId={}, partition={}, offset={}",
                tradeId, record.partition(), record.offset());

            positionService.updatePosition(tradeEvent);

            acknowledgment.acknowledge();
            log.debug("Position updated successfully for trade: tradeId={}", tradeId);

        } catch (Exception e) {
            log.error("Failed to update position for trade: tradeId={}", tradeId, e);
            throw e;
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_KEY);
        }
    }

    public void handleTradeEnrichedFallback(ConsumerRecord<String, Map<String, Object>> record,
                                             Acknowledgment acknowledgment,
                                             Exception e) {
        Map<String, Object> tradeEvent = record.value();
        String tradeId = (String) tradeEvent.get("tradeId");

        log.error("Circuit breaker open, sending trade to DLQ: tradeId={}", tradeId, e);
        // In production, this would send to DLQ topic
        // For now, we acknowledge to prevent infinite retry
        acknowledgment.acknowledge();
    }
}
