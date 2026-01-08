package com.tms.position.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponse {
    private Long positionId;
    private Long accountId;
    private String accountCode;
    private Long instrumentId;
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal avgCost;
    private BigDecimal costBasis;
    private BigDecimal marketValue;
    private BigDecimal unrealizedPnl;
    private BigDecimal realizedPnl;
    private String currency;
    private LocalDate asOfDate;
    private LocalDateTime updatedAt;
}
