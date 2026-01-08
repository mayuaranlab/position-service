package com.tms.position.service;

import com.tms.common.config.kafka.KafkaTopics;
import com.tms.common.observability.logging.CorrelationIdFilter;
import com.tms.common.observability.metrics.TradeMetrics;
import com.tms.position.entity.Position;
import com.tms.position.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionService {

    private final PositionRepository positionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TradeMetrics tradeMetrics;

    private static final String POSITION_CACHE_PREFIX = "position:";

    @Transactional
    public Position updatePosition(Map<String, Object> tradeEvent) {
        String correlationId = (String) tradeEvent.getOrDefault("correlationId",
            CorrelationIdFilter.getCurrentCorrelationId());
        String tradeId = (String) tradeEvent.get("tradeId");

        log.info("Updating position for trade: tradeId={}", tradeId);
        long startTime = System.currentTimeMillis();

        try {
            // Extract trade details
            Map<String, Object> account = (Map<String, Object>) tradeEvent.get("account");
            Map<String, Object> instrument = (Map<String, Object>) tradeEvent.get("instrument");

            Long accountId = Long.valueOf(account.get("accountId").toString());
            String accountCode = (String) account.get("accountCode");
            Long instrumentId = Long.valueOf(instrument.get("instrumentId").toString());
            String symbol = (String) instrument.get("symbol");
            String currency = (String) tradeEvent.get("currency");

            String side = (String) tradeEvent.get("side");
            BigDecimal quantity = new BigDecimal((String) tradeEvent.get("quantity"));
            BigDecimal price = new BigDecimal((String) tradeEvent.get("price"));
            LocalDate tradeDate = LocalDate.parse((String) tradeEvent.get("tradeDate"));

            // Adjust quantity for sell trades
            BigDecimal quantityChange = "SELL".equals(side) ? quantity.negate() : quantity;

            // Find or create position
            Position position = positionRepository
                .findByAccountIdAndInstrumentIdAndAsOfDate(accountId, instrumentId, tradeDate)
                .orElseGet(() -> Position.builder()
                    .accountId(accountId)
                    .accountCode(accountCode)
                    .instrumentId(instrumentId)
                    .symbol(symbol)
                    .quantity(BigDecimal.ZERO)
                    .avgCost(BigDecimal.ZERO)
                    .costBasis(BigDecimal.ZERO)
                    .realizedPnl(BigDecimal.ZERO)
                    .currency(currency)
                    .asOfDate(tradeDate)
                    .build());

            BigDecimal previousQuantity = position.getQuantity();
            BigDecimal previousAvgCost = position.getAvgCost();
            BigDecimal realizedPnl = BigDecimal.ZERO;

            // Calculate new position
            BigDecimal newQuantity = previousQuantity.add(quantityChange);

            BigDecimal newAvgCost;
            if ("BUY".equals(side)) {
                // Weighted average cost for buys
                if (newQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal existingValue = previousQuantity.multiply(previousAvgCost);
                    BigDecimal newValue = quantity.multiply(price);
                    newAvgCost = existingValue.add(newValue)
                        .divide(newQuantity, 8, RoundingMode.HALF_UP);
                } else {
                    newAvgCost = price;
                }
            } else {
                // For sells, calculate realized P&L
                if (previousQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    realizedPnl = quantity.multiply(price.subtract(previousAvgCost));
                }
                newAvgCost = previousAvgCost; // Avg cost unchanged on sells
            }

            // Update position
            position.setQuantity(newQuantity);
            position.setAvgCost(newAvgCost);
            position.setCostBasis(newQuantity.abs().multiply(newAvgCost));
            position.setRealizedPnl(position.getRealizedPnl().add(realizedPnl));

            position = positionRepository.save(position);
            log.info("Position updated: positionId={}, symbol={}, quantity={}",
                position.getPositionId(), symbol, newQuantity);

            // Update cache
            updatePositionCache(position);

            // Publish position updated event
            publishPositionUpdatedEvent(position, tradeId, previousQuantity, quantityChange,
                realizedPnl, correlationId);

            long duration = System.currentTimeMillis() - startTime;
            tradeMetrics.recordPositionCalculationTime(duration);
            tradeMetrics.incrementPositionsUpdated();

            return position;

        } catch (Exception e) {
            log.error("Failed to update position for trade: tradeId={}", tradeId, e);
            throw e;
        }
    }

    public List<Position> getPositionsByAccount(String accountCode, LocalDate asOfDate) {
        return positionRepository.findPositionsByAccount(accountCode, asOfDate);
    }

    public Optional<Position> getPosition(Long positionId) {
        return positionRepository.findById(positionId);
    }

    private void updatePositionCache(Position position) {
        try {
            String key = POSITION_CACHE_PREFIX + position.getAccountCode() + ":" + position.getSymbol();
            Map<String, Object> cached = new HashMap<>();
            cached.put("positionId", position.getPositionId());
            cached.put("accountCode", position.getAccountCode());
            cached.put("symbol", position.getSymbol());
            cached.put("quantity", position.getQuantity().toString());
            cached.put("avgCost", position.getAvgCost().toString());
            cached.put("costBasis", position.getCostBasis().toString());
            cached.put("currency", position.getCurrency());
            cached.put("updatedAt", position.getUpdatedAt().toString());

            redisTemplate.opsForValue().set(key, cached, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to update position cache", e);
        }
    }

    private void publishPositionUpdatedEvent(Position position, String tradeId,
                                              BigDecimal previousQuantity, BigDecimal quantityChange,
                                              BigDecimal realizedPnl, String correlationId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "PositionUpdated");
        event.put("eventTime", Instant.now().toEpochMilli());
        event.put("correlationId", correlationId);
        event.put("source", "position-service");

        event.put("positionId", position.getPositionId().toString());
        event.put("accountId", position.getAccountId().toString());
        event.put("accountCode", position.getAccountCode());
        event.put("instrumentId", position.getInstrumentId().toString());
        event.put("symbol", position.getSymbol());
        event.put("previousQuantity", previousQuantity.toString());
        event.put("newQuantity", position.getQuantity().toString());
        event.put("quantityChange", quantityChange.toString());
        event.put("avgCost", position.getAvgCost().toString());
        event.put("costBasis", position.getCostBasis().toString());
        event.put("realizedPnl", realizedPnl.toString());
        event.put("currency", position.getCurrency());
        event.put("triggeringTradeId", tradeId);
        event.put("asOfDate", position.getAsOfDate().toString());
        event.put("updateType", "TRADE");

        String key = position.getAccountCode() + ":" + position.getSymbol();
        kafkaTemplate.send(KafkaTopics.POSITIONS_UPDATED, key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish PositionUpdated event: positionId={}",
                        position.getPositionId(), ex);
                } else {
                    log.debug("PositionUpdated event published: positionId={}",
                        position.getPositionId());
                }
            });
    }
}
