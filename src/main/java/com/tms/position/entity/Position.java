package com.tms.position.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "position",
    uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "instrument_id", "as_of_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_code", nullable = false, length = 50)
    private String accountCode;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(name = "avg_cost", nullable = false, precision = 18, scale = 8)
    private BigDecimal avgCost;

    @Column(name = "cost_basis", nullable = false, precision = 18, scale = 4)
    private BigDecimal costBasis;

    @Column(name = "market_value", precision = 18, scale = 4)
    private BigDecimal marketValue;

    @Column(name = "unrealized_pnl", precision = 18, scale = 4)
    private BigDecimal unrealizedPnl;

    @Column(name = "realized_pnl", precision = 18, scale = 4)
    private BigDecimal realizedPnl;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (realizedPnl == null) {
            realizedPnl = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
